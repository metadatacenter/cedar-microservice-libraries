package org.metadatacenter.server.search.elasticsearch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

/**
 * An OpenSearch cluster in-process, answering the part of the REST API that CEDAR's client calls: creating, finding,
 * listing and deleting indices, moving aliases, writing, updating and deleting documents singly and in bulk, deleting
 * by query, counting, refreshing, reading an index whole through a scroll, and paging through a point in time with
 * search_after. Queries are evaluated for the forms the indexing, management and searching code sends — match, term,
 * terms, ids, exists, match_all, query_string, match_phrase and bool over them — and any other form is answered with a
 * 400, so a test learns at once that it reached past what this cluster can answer.
 *
 * <p>Relevance and the shipped analyzers are the real cluster's business, and the integration tests against it cover
 * them. Every hit here scores 1.0. Text is split into lowercase words at every character that is neither a letter nor
 * a digit, roughly as the standard analyzer splits it, and a sub-field such as {@code .raw} reads the value of the
 * field it belongs to. A query_string query must hold one term, in which only an unescaped {@code *} or {@code ?} is
 * syntax. Anything else the query_string grammar would read as an operator is refused, because CEDAR is meant to send
 * a user's text escaped. Sorting compares field values as text.</p>
 */
public final class FakeOpenSearch implements AutoCloseable {

  private static final ObjectMapper JSON = new ObjectMapper();

  /** One index: its settings and mappings as created, its aliases, and its documents in the order written. */
  public static final class Index {
    public final String name;
    public final JsonNode settings;
    public final JsonNode mappings;
    public final Set<String> aliases = new LinkedHashSet<>();
    public final Map<String, ObjectNode> documents = new LinkedHashMap<>();

    Index(String name, JsonNode settings, JsonNode mappings) {
      this.name = name;
      this.settings = settings;
      this.mappings = mappings;
    }
  }

  private record Scroll(List<Map.Entry<String, ObjectNode>> hits, String index, int size, int[] next) {
  }

  /** What a point in time reads: the documents its indices held when it was opened, as they were then. */
  private record PointInTime(List<Map.Entry<String, ObjectNode>> documents, String index) {
  }

  /** The prefix of every point in time this cluster issues; an id without it cannot be one. */
  private static final String POINT_IN_TIME_PREFIX = "fake-pit-";
  /** What the query_string grammar reads as syntax, besides whitespace, the wildcards and the escape. */
  private static final String QUERY_STRING_SYNTAX = "+-=&|><!(){}[]^\"~:/";

  private final HttpServer server;
  private final Map<String, Index> indices = new LinkedHashMap<>();
  private final Map<String, Scroll> scrolls = new LinkedHashMap<>();
  private final Map<String, PointInTime> pointsInTime = new LinkedHashMap<>();
  /** Every request, as its method and path. */
  public final List<String> requests = new CopyOnWriteArrayList<>();
  /**
   * Whether index and alias changes are acknowledged; a test turns this off to see a refusal. As on a cluster whose
   * acknowledgement timed out, an unacknowledged change is still applied.
   */
  public volatile boolean acknowledge = true;
  private int sequence;
  private boolean closed;

  public FakeOpenSearch() throws IOException {
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext("/", this::handle);
    server.start();
  }

  public int port() {
    return server.getAddress().getPort();
  }

  public synchronized Index index(String name) {
    return indices.get(name);
  }

  public synchronized Set<String> indexNames() {
    return new LinkedHashSet<>(indices.keySet());
  }

  /** How many points in time are open, so a test can see that a finished walk released its own. */
  public synchronized int openPointsInTime() {
    return pointsInTime.size();
  }

  /** Releases every point in time, as a keep alive running out or a restart does. */
  public synchronized void expirePointsInTime() {
    pointsInTime.clear();
  }

  /** Stops answering, as a cluster that has gone away does. Closing twice is harmless. */
  @Override
  public synchronized void close() {
    if (!closed) {
      closed = true;
      server.stop(0);
    }
  }

  private void handle(HttpExchange exchange) throws IOException {
    String method = exchange.getRequestMethod();
    String path = exchange.getRequestURI().getRawPath();
    String query = exchange.getRequestURI().getQuery();
    requests.add(method + " " + path);
    String body;
    try (InputStream in = exchange.getRequestBody()) {
      body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
    Response response;
    try {
      synchronized (this) {
        response = route(method, path, query, body);
      }
    } catch (UnsupportedOperationException e) {
      response = error(400, "unsupported_by_fake", e.getMessage());
    }
    exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
    if (method.equals("HEAD") || response.body == null) {
      exchange.sendResponseHeaders(response.status, -1);
      exchange.close();
      return;
    }
    byte[] bytes = JSON.writeValueAsBytes(response.body);
    exchange.sendResponseHeaders(response.status, bytes.length);
    try (OutputStream out = exchange.getResponseBody()) {
      out.write(bytes);
    }
  }

  private record Response(int status, JsonNode body) {
  }

  private Response route(String method, String path, String query, String body) throws IOException {
    // Split the raw path: a document id is a CEDAR IRI, and its encoded slashes are part of one segment.
    List<String> parts = new ArrayList<>();
    for (String segment : path.replaceFirst("^/", "").split("/")) {
      if (!segment.isEmpty()) {
        parts.add(URLDecoder.decode(segment, StandardCharsets.UTF_8));
      }
    }
    if (parts.isEmpty()) {
      return new Response(200, JSON.createObjectNode().put("cluster_name", "fake"));
    }
    String first = parts.get(0);
    if (first.equals("_aliases") && method.equals("POST")) {
      return updateAliases(JSON.readTree(body));
    }
    if (first.equals("_bulk")) {
      return bulk(null, body);
    }
    if (first.equals("_search") && parts.size() > 1 && parts.get(1).equals("scroll")) {
      return method.equals("DELETE") ? clearScroll(body) : nextScrollPage(JSON.readTree(body).path("scroll_id").asText());
    }
    if (first.equals("_search") && parts.size() > 1 && parts.get(1).equals("point_in_time") && method.equals("DELETE")) {
      return deletePointsInTime(body);
    }
    if (first.equals("_search") && parts.size() == 1) {
      return search(null, body, query);
    }
    if (parts.size() == 1) {
      return switch (method) {
        case "PUT" -> createIndex(first, body);
        case "HEAD" -> new Response(resolve(first).isEmpty() ? 404 : 200, null);
        case "DELETE" -> deleteIndex(first);
        case "GET" -> getIndices(first);
        default -> throw new UnsupportedOperationException(method + " " + path);
      };
    }
    String operation = parts.get(1);
    return switch (operation) {
      case "_refresh" -> new Response(200, JSON.createObjectNode().set("_shards", shards()));
      case "_count" -> count(first, body);
      case "_search" -> parts.size() > 2 && parts.get(2).equals("point_in_time")
          ? createPointInTime(first) : search(first, body, query);
      case "_delete_by_query" -> deleteByQuery(first, body);
      case "_bulk" -> bulk(first, body);
      case "_doc" -> document(method, first, parts.size() > 2 ? parts.get(2) : null, body);
      case "_update" -> update(first, parts.get(2), body);
      default -> throw new UnsupportedOperationException(method + " " + path);
    };
  }

  // ---- indices and aliases -----------------------------------------------------------------------

  private Response createIndex(String name, String body) throws IOException {
    if (indices.containsKey(name)) {
      return error(400, "resource_already_exists_exception", "index [" + name + "] already exists");
    }
    JsonNode request = body.isBlank() ? JSON.createObjectNode() : JSON.readTree(body);
    indices.put(name, new Index(name, request.path("settings"), request.path("mappings")));
    return new Response(200, JSON.createObjectNode().put("acknowledged", acknowledge)
        .put("shards_acknowledged", acknowledge).put("index", name));
  }

  private Response deleteIndex(String target) {
    List<Index> found = resolve(target);
    if (found.isEmpty()) {
      return error(404, "index_not_found_exception", "no such index [" + target + "]");
    }
    found.forEach(index -> indices.remove(index.name));
    return acknowledged();
  }

  private Response getIndices(String target) {
    ObjectNode result = JSON.createObjectNode();
    for (Index index : resolve(target)) {
      ObjectNode entry = result.putObject(index.name);
      ObjectNode aliases = entry.putObject("aliases");
      index.aliases.forEach(aliases::putObject);
      entry.set("mappings", index.mappings.isMissingNode() ? JSON.createObjectNode() : index.mappings);
      entry.putObject("settings").putObject("index").put("number_of_shards", "1").put("provided_name", index.name);
    }
    return new Response(200, result);
  }

  private Response updateAliases(JsonNode request) {
    for (JsonNode action : request.path("actions")) {
      Map.Entry<String, JsonNode> only = action.fields().next();
      JsonNode spec = only.getValue();
      List<String> targets = new ArrayList<>();
      spec.path("indices").forEach(index -> targets.add(index.asText()));
      if (spec.hasNonNull("index")) {
        targets.add(spec.get("index").asText());
      }
      List<String> aliases = new ArrayList<>();
      spec.path("aliases").forEach(alias -> aliases.add(alias.asText()));
      if (spec.hasNonNull("alias")) {
        aliases.add(spec.get("alias").asText());
      }
      for (String target : targets) {
        for (Index index : resolve(target)) {
          switch (only.getKey()) {
            case "add" -> index.aliases.addAll(aliases);
            case "remove" -> index.aliases.removeAll(aliases);
            default -> throw new UnsupportedOperationException("alias action " + only.getKey());
          }
        }
      }
    }
    return acknowledged();
  }

  /** The indices a name, an alias, a wildcard or a comma-separated list of them names. */
  private List<Index> resolve(String target) {
    List<Index> found = new ArrayList<>();
    for (String name : target.split(",")) {
      for (Index index : indices.values()) {
        boolean matches = name.equals("*") || name.equals("_all") || index.name.equals(name)
            || index.aliases.contains(name)
            || (name.endsWith("*") && index.name.startsWith(name.substring(0, name.length() - 1)));
        if (matches && !found.contains(index)) {
          found.add(index);
        }
      }
    }
    return found;
  }

  /** The one index a write goes to: the named index, or the single index an alias names. */
  private Index writeTarget(String target) {
    List<Index> found = resolve(target);
    if (found.size() > 1) {
      throw new UnsupportedOperationException("a write to [" + target + "] names " + found.size() + " indices");
    }
    return found.isEmpty() ? null : found.get(0);
  }

  // ---- documents ---------------------------------------------------------------------------------

  private Response document(String method, String target, String id, String body) throws IOException {
    Index index = writeTarget(target);
    switch (method) {
      case "PUT", "POST" -> {
        if (index == null) {
          // As a real cluster does with automatic index creation, a write creates its index.
          indices.put(target, index = new Index(target, JSON.createObjectNode(), JSON.createObjectNode()));
        }
        String documentId = id == null ? UUID.randomUUID().toString() : id;
        boolean created = !index.documents.containsKey(documentId);
        index.documents.put(documentId, (ObjectNode) JSON.readTree(body));
        return new Response(created ? 201 : 200, writeResult(index.name, documentId, created ? "created" : "updated"));
      }
      case "DELETE" -> {
        boolean found = index != null && index.documents.remove(id) != null;
        return new Response(found ? 200 : 404, writeResult(index == null ? target : index.name, id,
            found ? "deleted" : "not_found"));
      }
      case "GET" -> {
        ObjectNode source = index == null ? null : index.documents.get(id);
        ObjectNode result = JSON.createObjectNode().put("_index", target).put("_id", id).put("found", source != null);
        if (source != null) {
          result.put("_version", 1).put("_seq_no", 0).put("_primary_term", 1).set("_source", source);
        }
        return new Response(source == null ? 404 : 200, result);
      }
      default -> throw new UnsupportedOperationException(method + " document");
    }
  }

  private Response update(String target, String id, String body) throws IOException {
    Index index = writeTarget(target);
    ObjectNode existing = index == null ? null : index.documents.get(id);
    if (existing == null) {
      return error(404, "document_missing_exception", "[" + id + "]: document missing");
    }
    JsonNode request = JSON.readTree(body);
    if (request.has("script")) {
      // The one script CEDAR sends assigns each entry of its patch parameter to the source.
      JsonNode patch = request.path("script").path("params").path("patch");
      if (!patch.isObject()) {
        throw new UnsupportedOperationException("a script other than the permission patch: " + request);
      }
      existing.setAll((ObjectNode) patch);
    } else {
      existing.setAll((ObjectNode) request.path("doc"));
    }
    return new Response(200, writeResult(index.name, id, "updated"));
  }

  private Response bulk(String defaultIndex, String body) throws IOException {
    ArrayNode items = JSON.createArrayNode();
    boolean errors = false;
    Iterator<String> lines = body.lines().filter(line -> !line.isBlank()).iterator();
    while (lines.hasNext()) {
      ObjectNode action = (ObjectNode) JSON.readTree(lines.next());
      Map.Entry<String, JsonNode> only = action.fields().next();
      String type = only.getKey();
      String target = only.getValue().path("_index").asText(defaultIndex);
      String id = only.getValue().path("_id").asText(UUID.randomUUID().toString());
      Response result = switch (type) {
        case "index", "create" -> document("PUT", target, id, lines.next());
        case "delete" -> document("DELETE", target, id, "");
        case "update" -> update(target, id, lines.next());
        default -> throw new UnsupportedOperationException("bulk action " + type);
      };
      ObjectNode item;
      if (result.body.has("error")) {
        item = JSON.createObjectNode().put("_index", target).put("_id", id).put("status", result.status);
        item.set("error", result.body.get("error"));
        errors = true;
      } else {
        item = ((ObjectNode) result.body).put("status", result.status);
      }
      items.addObject().set(type, item);
    }
    return new Response(200, JSON.createObjectNode().put("took", 1).put("errors", errors).set("items", items));
  }

  private Response deleteByQuery(String target, String body) throws IOException {
    JsonNode query = JSON.readTree(body).path("query");
    long deleted = 0;
    for (Index index : resolve(target)) {
      Iterator<Map.Entry<String, ObjectNode>> documents = index.documents.entrySet().iterator();
      while (documents.hasNext()) {
        Map.Entry<String, ObjectNode> document = documents.next();
        if (matches(query, document.getKey(), document.getValue())) {
          documents.remove();
          deleted++;
        }
      }
    }
    ObjectNode result = JSON.createObjectNode().put("took", 1).put("timed_out", false).put("total", deleted)
        .put("deleted", deleted).put("batches", 1).put("version_conflicts", 0).put("noops", 0)
        .put("throttled_millis", 0).put("requests_per_second", -1.0).put("throttled_until_millis", 0);
    result.putObject("retries").put("bulk", 0).put("search", 0);
    result.putArray("failures");
    return new Response(200, result);
  }

  // ---- reading -----------------------------------------------------------------------------------

  private List<Map.Entry<String, ObjectNode>> matching(String target, JsonNode query, List<String> owners) {
    List<Map.Entry<String, ObjectNode>> hits = new ArrayList<>();
    for (Index index : resolve(target)) {
      for (Map.Entry<String, ObjectNode> document : index.documents.entrySet()) {
        if (matches(query, document.getKey(), document.getValue())) {
          hits.add(document);
          owners.add(index.name);
        }
      }
    }
    return hits;
  }

  private Response count(String target, String body) throws IOException {
    JsonNode query = body.isBlank() ? null : JSON.readTree(body).path("query");
    long count = matching(target, query, new ArrayList<>()).size();
    return new Response(200, JSON.createObjectNode().put("count", count).set("_shards", shards()));
  }

  private Response search(String target, String body, String query) throws IOException {
    JsonNode request = body.isBlank() ? JSON.createObjectNode() : JSON.readTree(body);
    if (request.has("pit")) {
      return searchPointInTime(target, request);
    }
    // A search that names no index reads every one.
    String indices = target == null ? "_all" : target;
    List<String> owners = new ArrayList<>();
    List<Map.Entry<String, ObjectNode>> hits = matching(indices, request.path("query"), owners);
    int size = request.path("size").asInt(10);
    if (query != null && query.contains("scroll=")) {
      String scrollId = UUID.randomUUID().toString();
      Scroll scroll = new Scroll(hits, owners.isEmpty() ? indices : owners.get(0), size, new int[]{0});
      scrolls.put(scrollId, scroll);
      return page(scrollId, scroll);
    }
    return sortedPage(request, hits, owners.isEmpty() ? indices : owners.get(0), null);
  }

  /** The documents a point in time holds that match, one page of them after the position search_after names. */
  private Response searchPointInTime(String target, JsonNode request) {
    if (target != null) {
      return error(400, "action_request_validation_exception",
          "Validation Failed: 1: [indices] cannot be used with point in time;");
    }
    String id = request.path("pit").path("id").asText();
    if (!id.startsWith(POINT_IN_TIME_PREFIX)) {
      return error(400, "illegal_argument_exception", "invalid id: [" + id + "]");
    }
    PointInTime pointInTime = pointsInTime.get(id);
    if (pointInTime == null) {
      return error(404, "search_context_missing_exception", "No search context found for id [" + id + "]");
    }
    List<Map.Entry<String, ObjectNode>> hits = new ArrayList<>();
    for (Map.Entry<String, ObjectNode> document : pointInTime.documents()) {
      if (matches(request.path("query"), document.getKey(), document.getValue())) {
        hits.add(document);
      }
    }
    return sortedPage(request, hits, pointInTime.index(), id);
  }

  /**
   * One page of the hits in the order the request sorts them, resumed after search_after when it names a position.
   * Each hit carries its sort values, which is what a caller hands back to resume.
   */
  private Response sortedPage(JsonNode request, List<Map.Entry<String, ObjectNode>> hits, String index,
                              String pointInTimeId) {
    List<Map.Entry<String, Boolean>> sort = sortKeys(request.path("sort"));
    List<Map.Entry<String, ObjectNode>> ordered = new ArrayList<>(hits);
    ordered.sort((a, b) -> compareSortValues(sortValues(sort, a.getValue()), sortValues(sort, b.getValue()), sort));
    JsonNode after = request.path("search_after");
    if (after.isArray()) {
      if (after.size() != sort.size()) {
        return error(400, "illegal_argument_exception",
            "search_after has " + after.size() + " value(s) but sort has " + sort.size() + ".");
      }
      List<Object> position = new ArrayList<>();
      after.forEach(value -> position.add(value.isNumber() ? (Object) value.asDouble() : value.asText()));
      ordered.removeIf(hit -> compareSortValues(sortValues(sort, hit.getValue()), position, sort) <= 0);
    }
    int from = request.path("from").asInt(0);
    int size = request.path("size").asInt(10);
    List<Map.Entry<String, ObjectNode>> page = ordered.subList(Math.min(from, ordered.size()),
        Math.min(from + size, ordered.size()));
    ObjectNode result = searchResult(page, hits.size(), index, null);
    if (pointInTimeId != null) {
      result.put("pit_id", pointInTimeId);
    }
    if (request.path("track_total_hits").isBoolean() && !request.path("track_total_hits").asBoolean()) {
      ((ObjectNode) result.path("hits")).remove("total");
    }
    JsonNode source = request.path("_source");
    for (int i = 0; i < page.size(); i++) {
      ObjectNode hit = (ObjectNode) result.path("hits").path("hits").get(i);
      if (source.isBoolean() && !source.asBoolean()) {
        hit.remove("_source");
      }
      if (!sort.isEmpty()) {
        ArrayNode values = hit.putArray("sort");
        for (Object value : sortValues(sort, page.get(i).getValue())) {
          if (value instanceof Double number) {
            values.add(number);
          } else {
            values.add((String) value);
          }
        }
      }
    }
    return new Response(200, result);
  }

  /** Each sort key as its field and whether it runs ascending; _score is a field every hit holds as 1.0. */
  private static List<Map.Entry<String, Boolean>> sortKeys(JsonNode sort) {
    List<Map.Entry<String, Boolean>> keys = new ArrayList<>();
    for (JsonNode key : asArray(sort)) {
      if (key.isTextual()) {
        keys.add(Map.entry(key.asText(), !key.asText().equals("_score")));
        continue;
      }
      Map.Entry<String, JsonNode> only = key.fields().next();
      if (only.getKey().equals("_script")) {
        throw new UnsupportedOperationException("a script sort: " + key);
      }
      String order = only.getValue().isObject() ? only.getValue().path("order").asText("") : only.getValue().asText();
      boolean ascending = order.isEmpty() ? !only.getKey().equals("_score") : order.equals("asc");
      keys.add(Map.entry(only.getKey(), ascending));
    }
    return keys;
  }

  private static List<Object> sortValues(List<Map.Entry<String, Boolean>> sort, JsonNode document) {
    List<Object> values = new ArrayList<>();
    for (Map.Entry<String, Boolean> key : sort) {
      if (key.getKey().equals("_score")) {
        values.add(1.0);
      } else {
        List<String> found = subFieldValues(document, key.getKey());
        values.add(found.isEmpty() ? null : found.get(0));
      }
    }
    return values;
  }

  /** Orders two hits by their sort values. A hit missing a value sorts after every hit holding one, either way. */
  private static int compareSortValues(List<Object> a, List<Object> b, List<Map.Entry<String, Boolean>> sort) {
    for (int i = 0; i < sort.size(); i++) {
      Object left = a.get(i);
      Object right = b.get(i);
      int order;
      if (left == null || right == null) {
        order = left == right ? 0 : left == null ? 1 : -1;
      } else {
        order = left instanceof Double x && right instanceof Double y ? Double.compare(x, y)
            : left.toString().compareTo(right.toString());
        order = sort.get(i).getValue() ? order : -order;
      }
      if (order != 0) {
        return order;
      }
    }
    return 0;
  }

  private Response createPointInTime(String target) {
    List<Index> found = resolve(target);
    if (found.isEmpty()) {
      return error(404, "index_not_found_exception", "no such index [" + target + "]");
    }
    List<Map.Entry<String, ObjectNode>> documents = new ArrayList<>();
    for (Index index : found) {
      for (Map.Entry<String, ObjectNode> document : index.documents.entrySet()) {
        documents.add(Map.entry(document.getKey(), document.getValue().deepCopy()));
      }
    }
    String id = POINT_IN_TIME_PREFIX + UUID.randomUUID();
    pointsInTime.put(id, new PointInTime(documents, found.get(0).name));
    ObjectNode result = JSON.createObjectNode().put("pit_id", id);
    result.set("_shards", shards());
    result.put("creation_time", System.currentTimeMillis());
    return new Response(200, result);
  }

  private Response deletePointsInTime(String body) throws IOException {
    ObjectNode result = JSON.createObjectNode();
    ArrayNode deleted = result.putArray("pits");
    for (JsonNode id : JSON.readTree(body).path("pit_id")) {
      deleted.addObject().put("successful", pointsInTime.remove(id.asText()) != null).put("pit_id", id.asText());
    }
    return new Response(200, result);
  }

  private Response nextScrollPage(String scrollId) {
    Scroll scroll = scrolls.get(scrollId);
    if (scroll == null) {
      return error(404, "search_context_missing_exception", "No search context found for id [" + scrollId + "]");
    }
    return page(scrollId, scroll);
  }

  private Response page(String scrollId, Scroll scroll) {
    int from = scroll.next[0];
    int to = Math.min(from + scroll.size, scroll.hits.size());
    scroll.next[0] = to;
    return new Response(200, searchResult(scroll.hits.subList(from, to), scroll.hits.size(), scroll.index, scrollId));
  }

  private Response clearScroll(String body) throws IOException {
    int freed = 0;
    for (JsonNode id : JSON.readTree(body).path("scroll_id")) {
      if (scrolls.remove(id.asText()) != null) {
        freed++;
      }
    }
    return new Response(200, JSON.createObjectNode().put("succeeded", true).put("num_freed", freed));
  }

  private static ObjectNode searchResult(List<Map.Entry<String, ObjectNode>> page, int total, String index,
                                         String scrollId) {
    ObjectNode result = JSON.createObjectNode();
    if (scrollId != null) {
      result.put("_scroll_id", scrollId);
    }
    result.put("took", 1).put("timed_out", false).set("_shards", shards());
    ObjectNode hits = result.putObject("hits");
    hits.putObject("total").put("value", total).put("relation", "eq");
    hits.put("max_score", 1.0);
    ArrayNode list = hits.putArray("hits");
    for (Map.Entry<String, ObjectNode> hit : page) {
      list.addObject().put("_index", index).put("_id", hit.getKey()).put("_score", 1.0).set("_source", hit.getValue());
    }
    return result;
  }

  // ---- queries -----------------------------------------------------------------------------------

  static boolean matches(JsonNode query, String id, JsonNode document) {
    if (query == null || query.isMissingNode() || query.isEmpty()) {
      return true;
    }
    Map.Entry<String, JsonNode> only = query.fields().next();
    JsonNode spec = only.getValue();
    return switch (only.getKey()) {
      case "match_all" -> true;
      case "ids" -> {
        boolean listed = false;
        for (JsonNode value : spec.path("values")) {
          listed |= value.asText().equals(id);
        }
        yield listed;
      }
      case "match", "term" -> {
        Map.Entry<String, JsonNode> field = firstField(spec);
        JsonNode value = field.getValue().isObject()
            ? field.getValue().has("query") ? field.getValue().get("query") : field.getValue().get("value")
            : field.getValue();
        yield values(document, field.getKey()).stream().anyMatch(v -> v.equals(value.asText()));
      }
      case "terms" -> {
        Map.Entry<String, JsonNode> field = firstField(spec);
        List<String> wanted = new ArrayList<>();
        field.getValue().forEach(v -> wanted.add(v.asText()));
        yield values(document, field.getKey()).stream().anyMatch(wanted::contains);
      }
      case "exists" -> !values(document, spec.path("field").asText()).isEmpty();
      case "query_string" -> {
        Pattern term = queryStringTerm(spec.path("query").asText());
        List<String> fields = new ArrayList<>();
        spec.path("fields").forEach(field -> fields.add(field.asText().replaceFirst("\\^[0-9.]+$", "")));
        if (fields.isEmpty()) {
          throw new UnsupportedOperationException("a query_string without fields: " + spec);
        }
        yield fields.stream().flatMap(field -> subFieldValues(document, field).stream())
            .flatMap(value -> words(value).stream()).anyMatch(word -> term.matcher(word).matches());
      }
      case "match_phrase" -> {
        Map.Entry<String, JsonNode> field = firstField(spec);
        List<String> phrase = words((field.getValue().isObject() ? field.getValue().path("query") : field.getValue())
            .asText());
        yield !phrase.isEmpty() && subFieldValues(document, field.getKey()).stream()
            .anyMatch(value -> Collections.indexOfSubList(words(value), phrase) >= 0);
      }
      case "bool" -> {
        boolean must = all(spec.path("must"), id, document) && all(spec.path("filter"), id, document);
        boolean mustNot = any(spec.path("must_not"), id, document);
        JsonNode should = spec.path("should");
        int shouldCount = 0;
        for (JsonNode clause : asArray(should)) {
          if (matches(clause, id, document)) {
            shouldCount++;
          }
        }
        int minimum = spec.has("minimum_should_match") ? spec.get("minimum_should_match").asInt()
            : (asArray(spec.path("must")).isEmpty() && asArray(spec.path("filter")).isEmpty()
            && !asArray(should).isEmpty()) ? 1 : 0;
        yield must && !mustNot && shouldCount >= minimum;
      }
      default -> throw new UnsupportedOperationException("query [" + only.getKey() + "]");
    };
  }

  /** The first field of a query's body, skipping the options every query may carry. */
  private static Map.Entry<String, JsonNode> firstField(JsonNode spec) {
    Iterator<Map.Entry<String, JsonNode>> fields = spec.fields();
    while (fields.hasNext()) {
      Map.Entry<String, JsonNode> field = fields.next();
      if (!field.getKey().equals("boost") && !field.getKey().equals("_name")) {
        return field;
      }
    }
    throw new UnsupportedOperationException("a query without a field: " + spec);
  }

  private static boolean all(JsonNode clauses, String id, JsonNode document) {
    for (JsonNode clause : asArray(clauses)) {
      if (!matches(clause, id, document)) {
        return false;
      }
    }
    return true;
  }

  private static boolean any(JsonNode clauses, String id, JsonNode document) {
    for (JsonNode clause : asArray(clauses)) {
      if (matches(clause, id, document)) {
        return true;
      }
    }
    return false;
  }

  private static List<JsonNode> asArray(JsonNode node) {
    List<JsonNode> list = new ArrayList<>();
    if (node.isArray()) {
      node.forEach(list::add);
    } else if (!node.isMissingNode() && !node.isNull()) {
      list.add(node);
    }
    return list;
  }

  /**
   * The one term a query_string query may hold, as a pattern over words: an unescaped {@code *} or {@code ?} is a
   * wildcard, a backslash makes the next character literal, and any other syntax is refused.
   */
  private static Pattern queryStringTerm(String text) {
    StringBuilder pattern = new StringBuilder();
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c == '\\') {
        if (++i == text.length()) {
          throw new UnsupportedOperationException("a query_string ending in an escape: [" + text + "]");
        }
        pattern.append(Pattern.quote(String.valueOf(text.charAt(i))));
      } else if (c == '*') {
        pattern.append(".*");
      } else if (c == '?') {
        pattern.append('.');
      } else if (Character.isWhitespace(c) || QUERY_STRING_SYNTAX.indexOf(c) >= 0) {
        throw new UnsupportedOperationException("query_string syntax beyond one term, at '" + c + "' in [" + text + "]");
      } else {
        pattern.append(Pattern.quote(String.valueOf(c)));
      }
    }
    return Pattern.compile(pattern.toString(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.DOTALL);
  }

  /** A text value's words, lowercased, split at every character that is neither a letter nor a digit. */
  private static List<String> words(String text) {
    List<String> words = new ArrayList<>();
    for (String word : text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
      if (!word.isEmpty()) {
        words.add(word);
      }
    }
    return words;
  }

  /** The values a field holds, where a sub-field such as {@code .raw} or {@code .keyword} indexes its parent's. */
  private static List<String> subFieldValues(JsonNode document, String path) {
    List<String> found = values(document, path);
    if (found.isEmpty() && (path.endsWith(".raw") || path.endsWith(".keyword"))) {
      found = values(document, path.substring(0, path.lastIndexOf('.')));
    }
    return found;
  }

  /** Every scalar a dotted path reaches in a document, descending into arrays as an index does. */
  private static List<String> values(JsonNode document, String path) {
    List<JsonNode> current = List.of(document);
    for (String segment : path.split("\\.")) {
      List<JsonNode> next = new ArrayList<>();
      for (JsonNode node : current) {
        for (JsonNode element : node.isArray() ? asArray(node) : List.of(node)) {
          JsonNode child = element.get(segment);
          if (child != null && !child.isNull()) {
            next.add(child);
          }
        }
      }
      current = next;
    }
    List<String> values = new ArrayList<>();
    for (JsonNode node : current) {
      for (JsonNode element : node.isArray() ? asArray(node) : List.of(node)) {
        if (element.isValueNode()) {
          values.add(element.asText());
        }
      }
    }
    return values;
  }

  // ---- responses ---------------------------------------------------------------------------------

  private Response acknowledged() {
    return new Response(200, JSON.createObjectNode().put("acknowledged", acknowledge));
  }

  private ObjectNode writeResult(String index, String id, String result) {
    ObjectNode node = JSON.createObjectNode().put("_index", index).put("_id", id).put("_version", 1)
        .put("result", result).put("_seq_no", sequence++).put("_primary_term", 1);
    node.putObject("_shards").put("total", 1).put("successful", 1).put("failed", 0);
    return node;
  }

  private static ObjectNode shards() {
    return JSON.createObjectNode().put("total", 1).put("successful", 1).put("skipped", 0).put("failed", 0);
  }

  private static Response error(int status, String type, String reason) {
    ObjectNode body = JSON.createObjectNode();
    ObjectNode error = body.putObject("error");
    error.putArray("root_cause").addObject().put("type", type).put("reason", reason);
    error.put("type", type).put("reason", reason);
    body.put("status", status);
    return new Response(status, body);
  }
}
