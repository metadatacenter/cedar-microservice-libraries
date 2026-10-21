package org.metadatacenter.server.search.elasticsearch.worker;

import java.util.Locale;

import org.metadatacenter.model.request.ModifiedDateRange;
import org.metadatacenter.model.request.SearchSharingFilter;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.queryparser.flexible.core.QueryNodeException;
import org.apache.lucene.queryparser.flexible.standard.parser.StandardSyntaxParser;
import org.apache.lucene.queryparser.flexible.core.nodes.*;
import org.apache.lucene.queryparser.flexible.core.util.UnescapedCharSequence;
import org.apache.lucene.search.join.ScoreMode;
import org.metadatacenter.config.OpensearchConfig;
import org.metadatacenter.error.CedarErrorReasonKey;
import org.metadatacenter.exception.CedarDependencyUnavailableException;
import org.metadatacenter.exception.CedarException;
import org.metadatacenter.exception.CedarProcessingException;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.server.security.model.auth.CedarNodeMaterializedPermissions;
import org.metadatacenter.server.security.model.auth.CedarPermission;
import org.metadatacenter.server.security.model.auth.NodeSharePermission;
import org.metadatacenter.server.security.model.permission.resource.ResourceRole;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.metadatacenter.server.security.model.user.ResourcePublicationStatusFilter;
import org.metadatacenter.server.security.model.user.ResourceVersionFilter;
import org.opensearch.OpenSearchException;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.search.CreatePitRequest;
import org.opensearch.action.search.DeletePitRequest;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.search.builder.PointInTimeBuilder;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.sort.SortBuilders;
import org.opensearch.search.sort.ScriptSortBuilder;
import org.opensearch.script.Script;
import org.opensearch.search.sort.SortOrder;
import org.opensearch.core.rest.RestStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.metadatacenter.constant.ElasticsearchConstants.*;

public class ElasticsearchPermissionEnabledContentSearchingWorker {

  private static final Logger log = LoggerFactory.getLogger(ElasticsearchPermissionEnabledContentSearchingWorker.class);

  private final RestHighLevelClient client;
  private final String indexName;
  private final int deepPageSize;
  private final TimeValue pointInTimeKeepAlive;

  public ElasticsearchPermissionEnabledContentSearchingWorker(OpensearchConfig config, RestHighLevelClient client) {
    this.client = client;
    this.indexName = config.getIndexes().getSearchIndex().getName();
    // The deep walk skips a whole result window per round trip. Each skipped page re-runs the query
    // over the entire result set and carries no documents back, so the widest page the index will
    // serve is also the cheapest way to cross a large offset.
    this.deepPageSize = Math.max(1, config.getMaxResultWindow());
    this.pointInTimeKeepAlive = TimeValue.timeValueMillis(config.getSearchContextKeepAlive());
  }

  public SearchResponseResult search(CedarRequestContext rctx, String query, List<String> resourceTypes, ResourceVersionFilter version,
                                     ResourcePublicationStatusFilter publicationStatus, String categoryId, List<String> sortList, int limit,
                                     int offset, ModifiedDateRange modified, SearchSharingFilter sharing) throws CedarProcessingException {

    try {
      SearchSourceBuilder searchSourceBuilder =
          getSearchSourceBuilder(rctx, query, resourceTypes, version, publicationStatus, categoryId, sortList, modified, sharing);
      SearchRequest searchRequest = new SearchRequest(indexName).source(searchSourceBuilder);

      // Set pagination parameters
      searchSourceBuilder.from(offset);
      searchSourceBuilder.size(limit);
      searchSourceBuilder.trackTotalHits(true);

      // Execute request
      SearchResponse response = client.search(searchRequest, RequestOptions.DEFAULT);

      SearchResponseResult result = new SearchResponseResult();
      result.setTotalCount(response.getHits().getTotalHits().value);

      for (SearchHit hit : response.getHits().getHits()) {
        result.add(hit);
      }

      return result;
    } catch (IOException e) {
      throw new CedarDependencyUnavailableException("OpenSearch is unavailable", e);
    }
  }

  // Deep paging. The offset is walked with search_after over a point in time rather than asked for in
  // one oversized request: a page then costs the page, not everything in front of it, and the rows
  // skipped on the way are read as sort values with no document body. The scroll API cannot serve this
  // call at all. A scroll batch is capped by index.max_result_window exactly as from+size is, so asking
  // for offset+limit rows failed for every offset past 10,000 that this endpoint exists to reach.
  public SearchResponseResult searchDeep(CedarRequestContext rctx, String query, List<String> resourceTypes, ResourceVersionFilter version,
                                         ResourcePublicationStatusFilter publicationStatus, String categoryId, List<String> sortList, int limit,
                                         int offset, ModifiedDateRange modified, SearchSharingFilter sharing) throws CedarProcessingException {
    String pointInTimeId = null;
    try {
      pointInTimeId = openPointInTime();

      SearchSourceBuilder searchSourceBuilder = getDeepSearchSourceBuilder(rctx, query, resourceTypes, version,
          publicationStatus, categoryId, sortList, pointInTimeId, modified, sharing);
      searchSourceBuilder.trackTotalHits(true);

      // A point in time carries the indices it was opened on, and OpenSearch rejects a request naming both.
      SearchRequest searchRequest = new SearchRequest().source(searchSourceBuilder);

      SearchResponseResult result = new SearchResponseResult();
      long totalCount = -1;
      Object[] searchAfter = null;

      long skipped = 0;
      while (skipped < offset) {
        int pageSize = (int) Math.min(deepPageSize, offset - skipped);
        SearchHits hits = executePage(searchRequest, searchSourceBuilder, pageSize, false, searchAfter);
        if (totalCount < 0) {
          totalCount = hits.getTotalHits().value;
        }
        SearchHit[] page = hits.getHits();
        if (page.length < pageSize) {
          // The result set ended before the offset was reached, so the requested page holds nothing.
          result.setTotalCount(totalCount);
          return result;
        }
        searchAfter = page[page.length - 1].getSortValues();
        skipped += page.length;
        // The first page reported the total; counting it again on every skipped page is work for an
        // answer already held.
        searchSourceBuilder.trackTotalHits(false);
      }

      SearchHits hits = executePage(searchRequest, searchSourceBuilder, limit, true, searchAfter);
      if (totalCount < 0) {
        totalCount = hits.getTotalHits().value;
      }
      result.setTotalCount(totalCount);
      for (SearchHit hit : hits.getHits()) {
        result.add(hit);
      }
      return result;
    } catch (IOException e) {
      throw new CedarDependencyUnavailableException("OpenSearch is unavailable", e);
    } finally {
      closePointInTime(pointInTimeId);
    }
  }

  /**
   * One page of a walk the caller is driving. The point in time is opened here when the walk starts,
   * carried by the caller from page to page, and released as soon as a page comes back short, because
   * a short page is the last one. Nothing is skipped: the page resumes where the previous one stopped,
   * so a page costs the same wherever in the result set it falls.
   */
  public DeepSearchPage searchDeepPage(CedarRequestContext rctx, String query, List<String> resourceTypes,
                                       ResourceVersionFilter version, ResourcePublicationStatusFilter publicationStatus,
                                       String categoryId, List<String> sortList, int limit, String pointInTimeId,
                                       Object[] searchAfter, ModifiedDateRange modified, SearchSharingFilter sharing) throws CedarException {
    boolean walkStarts = pointInTimeId == null;
    String walkPointInTimeId;
    try {
      walkPointInTimeId = walkStarts ? openPointInTime() : pointInTimeId;
    } catch (IOException e) {
      throw new CedarDependencyUnavailableException("OpenSearch is unavailable", e);
    }
    try {
      SearchSourceBuilder searchSourceBuilder = getDeepSearchSourceBuilder(rctx, query, resourceTypes, version,
          publicationStatus, categoryId, sortList, walkPointInTimeId, modified, sharing);
      // Only the first page counts the result set. Every page after it is handed the total the walk
      // began with, which is also what makes the pages agree with each other.
      searchSourceBuilder.trackTotalHits(walkStarts);
      SearchRequest searchRequest = new SearchRequest().source(searchSourceBuilder);

      SearchHits hits = executePage(searchRequest, searchSourceBuilder, limit, true, searchAfter);
      SearchResponseResult result = new SearchResponseResult();
      if (walkStarts) {
        result.setTotalCount(hits.getTotalHits().value);
      }
      SearchHit[] page = hits.getHits();
      for (SearchHit hit : page) {
        result.add(hit);
      }

      boolean lastPage = page.length < limit;
      if (lastPage) {
        closePointInTime(walkPointInTimeId);
        return new DeepSearchPage(result, null, null);
      }
      return new DeepSearchPage(result, walkPointInTimeId, page[page.length - 1].getSortValues());
    } catch (IOException e) {
      closePointInTime(walkPointInTimeId);
      throw new CedarDependencyUnavailableException("OpenSearch is unavailable", e);
    } catch (OpenSearchStatusException e) {
      if (e.status() == RestStatus.NOT_FOUND) {
        // The snapshot the walk was reading has been released, by its keep alive or by a restart.
        // There is nothing to resume: the caller has to start the walk again.
        throw new CedarProcessingException("The continuation has expired. Start the walk again!")
            .errorReasonKey(CedarErrorReasonKey.CONTINUATION_EXPIRED)
            .badRequest();
      }
      closePointInTime(walkPointInTimeId);
      if (!walkStarts && e.status() == RestStatus.BAD_REQUEST) {
        // A continuation is not signed, so it can name a snapshot or a position the cluster cannot read.
        // That refusal is the caller's to act on, as an expired one is.
        throw new CedarProcessingException("The continuation cannot be resumed. Start the walk again!", e)
            .badRequest();
      }
      throw e;
    }
  }

  /**
   * The search both deep calls run: the caller's query, ordered totally, inside a point in time.
   */
  private SearchSourceBuilder getDeepSearchSourceBuilder(CedarRequestContext rctx, String query,
                                                         List<String> resourceTypes, ResourceVersionFilter version,
                                                         ResourcePublicationStatusFilter publicationStatus,
                                                         String categoryId, List<String> sortList,
                                                         String pointInTimeId, ModifiedDateRange modified, SearchSharingFilter sharing) throws CedarProcessingException {
    SearchSourceBuilder searchSourceBuilder =
        getSearchSourceBuilder(rctx, query, resourceTypes, version, publicationStatus, categoryId, sortList, modified, sharing);
    searchSourceBuilder.pointInTimeBuilder(new PointInTimeBuilder(pointInTimeId).setKeepAlive(pointInTimeKeepAlive));
    return searchSourceBuilder;
  }

  private String openPointInTime() throws IOException {
    // A partial point in time would silently exclude an unavailable shard from every page below it.
    CreatePitRequest request = new CreatePitRequest(pointInTimeKeepAlive, false, indexName);
    return client.createPit(request, RequestOptions.DEFAULT).getId();
  }

  private void closePointInTime(String pointInTimeId) {
    if (pointInTimeId == null) {
      return;
    }
    try {
      client.deletePit(new DeletePitRequest(pointInTimeId), RequestOptions.DEFAULT);
    } catch (IOException | OpenSearchException e) {
      // The context expires on its own, and a failure here must not replace the outcome of the search.
      log.warn("Unable to delete the OpenSearch point in time context", e);
    }
  }

  private SearchHits executePage(SearchRequest searchRequest, SearchSourceBuilder searchSourceBuilder, int size,
                                 boolean withDocuments, Object[] searchAfter) throws IOException {
    searchSourceBuilder.size(size);
    searchSourceBuilder.fetchSource(withDocuments);
    if (searchAfter != null) {
      searchSourceBuilder.searchAfter(searchAfter);
    }
    return client.search(searchRequest, RequestOptions.DEFAULT).getHits();
  }

  private SearchSourceBuilder getSearchSourceBuilder(CedarRequestContext rctx, String query,
                                                     List<String> resourceTypes,
                                                     ResourceVersionFilter version,
                                                     ResourcePublicationStatusFilter publicationStatus,
                                                     String categoryId, List<String> sortList, ModifiedDateRange modified,
                                                     SearchSharingFilter sharing) throws CedarProcessingException {

    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();

    BoolQueryBuilder mainQuery = QueryBuilders.boolQuery();

    if (query != null && !query.isEmpty()) {
      query = preprocessQuery(query);

      // Compile the syntax tree before Lucene analysis discards quote and escape provenance.
      // The index's analyzers, rather than a whitespace analyzer here, tokenize literal phrases.
      try {
        mainQuery.must(rewriteQuery(new StandardSyntaxParser().parse(query, "")));
      } catch (QueryNodeException e) {
        throw invalidQuery("Error processing query: check quotes, parentheses and operators", e);
      }
    }

    String userId = rctx.getCedarUser().getId();
    // Constrain before counting, sorting and paging, never by filtering a returned page. Shared
    // searches include descendants accessible through a shared folder; ownership is still excluded.
    if (sharing == SearchSharingFilter.SHARED_WITH_EVERYBODY) {
      mainQuery.filter(QueryBuilders.termsQuery(COMPUTED_EVERYBODY_PERMISSION,
          NodeSharePermission.READ.getValue(), NodeSharePermission.WRITE.getValue()));
    } else if (sharing == SearchSharingFilter.SHARED_WITH_ME) {
      mainQuery.filter(QueryBuilders.termsQuery(USERS, accessibleRoleKeys(userId, ResourceRole.VIEWER)));
      mainQuery.mustNot(QueryBuilders.termQuery("info.ownedBy", userId));
      mainQuery.mustNot(QueryBuilders.existsQuery(COMPUTED_EVERYBODY_PERMISSION));
    }
    if (!rctx.getCedarUser().has(CedarPermission.READ_NOT_READABLE_NODE)) {
      // Filter by user
      QueryBuilder userIdQuery = QueryBuilders.termsQuery(USERS, accessibleRoleKeys(userId, ResourceRole.VIEWER));
      BoolQueryBuilder permissionQuery = QueryBuilders.boolQuery();

      QueryBuilder everybodyReadQuery = QueryBuilders.termsQuery(COMPUTED_EVERYBODY_PERMISSION,
          NodeSharePermission.READ.getValue());
      QueryBuilder everybodyWriteQuery = QueryBuilders.termsQuery(COMPUTED_EVERYBODY_PERMISSION,
          NodeSharePermission.WRITE.getValue());

      permissionQuery.should(userIdQuery);
      permissionQuery.should(everybodyReadQuery);
      permissionQuery.should(everybodyWriteQuery);
      mainQuery.must(permissionQuery);
    }

    // Filter by resource type
    if (resourceTypes != null && !resourceTypes.isEmpty()) {
      QueryBuilder resourceTypesQuery = QueryBuilders.termsQuery(RESOURCE_TYPE, resourceTypes);
      mainQuery.must(resourceTypesQuery);
    }

    // Filter version
    if (version != null && version != ResourceVersionFilter.ALL) {
      BoolQueryBuilder versionQuery = QueryBuilders.boolQuery();
      BoolQueryBuilder inner1Query = QueryBuilders.boolQuery();
      BoolQueryBuilder inner2Query = QueryBuilders.boolQuery();
      if (version == ResourceVersionFilter.LATEST) {
        QueryBuilder versionEqualsQuery = QueryBuilders.termsQuery(INFO_IS_LATEST_VERSION, true);
        inner1Query.must(versionEqualsQuery);
        QueryBuilder versionExistsQuery = QueryBuilders.existsQuery(INFO_IS_LATEST_VERSION);
        inner2Query.mustNot(versionExistsQuery);
      } else if (version == ResourceVersionFilter.LATEST_BY_STATUS) {
        QueryBuilder versionEquals1Query = QueryBuilders.termsQuery(INFO_IS_LATEST_PUBLISHED_VERSION, true);
        QueryBuilder versionEquals2Query = QueryBuilders.termsQuery(INFO_IS_LATEST_DRAFT_VERSION, true);
        inner1Query.should(versionEquals1Query);
        inner1Query.should(versionEquals2Query);
        QueryBuilder versionExists1Query = QueryBuilders.existsQuery(INFO_IS_LATEST_PUBLISHED_VERSION);
        QueryBuilder versionExists2Query = QueryBuilders.existsQuery(INFO_IS_LATEST_DRAFT_VERSION);
        inner2Query.mustNot(versionExists1Query);
        inner2Query.mustNot(versionExists2Query);
      }
      versionQuery.should(inner1Query);
      versionQuery.should(inner2Query);
      mainQuery.must(versionQuery);
    }

    // Filter publicationStatus
    if (publicationStatus != null && publicationStatus != ResourcePublicationStatusFilter.ALL) {
      BoolQueryBuilder publicationStatusQuery = QueryBuilders.boolQuery();
      BoolQueryBuilder inner1Query = QueryBuilders.boolQuery();
      QueryBuilder publicationStatusEqualsQuery = QueryBuilders.termsQuery(INFO_BIBO_STATUS,
          publicationStatus.getValue());
      inner1Query.must(publicationStatusEqualsQuery);
      BoolQueryBuilder inner2Query = QueryBuilders.boolQuery();
      QueryBuilder publicationStatusExistsQuery = QueryBuilders.existsQuery(INFO_BIBO_STATUS);
      inner2Query.mustNot(publicationStatusExistsQuery);
      publicationStatusQuery.should(inner1Query);
      publicationStatusQuery.should(inner2Query);
      mainQuery.must(publicationStatusQuery);
    }

    // Filter by category id
    if (categoryId != null && !categoryId.isEmpty()) {
      QueryBuilder categoryIdQuery = QueryBuilders.termsQuery(CATEGORIES, categoryId);
      mainQuery.must(categoryIdQuery);
    }

    // Set main query
    if (!modified.isUnbounded()) {
      var range = QueryBuilders.rangeQuery(INFO_PAV_LAST_UPDATED_ON);
      if (modified.after() != null) range.gte(modified.after());
      if (modified.before() != null) range.lt(modified.before());
      mainQuery.filter(range);
    }
    searchSourceBuilder.query(mainQuery);

    // Sort by field
    if (sortList != null && !sortList.isEmpty()) {
      for (String s : sortList) {
        SortOrder sortOrder = SortOrder.ASC;
        if (s.startsWith(ES_SORT_DESC_PREFIX)) {
          sortOrder = SortOrder.DESC;
          s = s.substring(1);
        }
        switch (s) {
          case "foldersFirst" -> searchSourceBuilder.sort(SortBuilders.scriptSort(
              new Script("doc['" + RESOURCE_TYPE + "'].size() != 0 && doc['" + RESOURCE_TYPE
                  + "'].value == 'folder' ? 0 : 1"), ScriptSortBuilder.ScriptSortType.NUMBER).order(sortOrder));
          case SORT_BY_NAME -> searchSourceBuilder.sort(INFO_SCHEMA_NAME, sortOrder);
          case SORT_LAST_UPDATED_ON_FIELD -> searchSourceBuilder.sort(INFO_PAV_LAST_UPDATED_ON, sortOrder);
          case SORT_CREATED_ON_FIELD -> searchSourceBuilder.sort(INFO_PAV_CREATED_ON, sortOrder);
        }
      }
    }
    // Use the same total ordering for ordinary pages and search_after. Equal names or
    // timestamps must not move between pages or change order when switching endpoints.
    if (searchSourceBuilder.sorts() == null || searchSourceBuilder.sorts().isEmpty()) {
      searchSourceBuilder.sort(SortBuilders.scoreSort());
    }
    searchSourceBuilder.sort(SortBuilders.fieldSort(DOCUMENT_CEDAR_ID).order(SortOrder.ASC));
    return searchSourceBuilder;
  }

  private boolean enclosedByQuotes(String keyword) {
    return keyword.startsWith("\"") && keyword.endsWith("\"");
  }

  private CedarProcessingException invalidQuery(String message, Exception cause) {
    CedarProcessingException error = new CedarProcessingException(message, cause);
    error.badRequest();
    return error;
  }

  /** Preserve the Boolean grammar while compiling each original, unanalysed search atom. */
  private QueryBuilder rewriteQuery(QueryNode node) throws CedarProcessingException {
    if (node instanceof GroupQueryNode group) return rewriteQuery(group.getChild());
    if (node instanceof BooleanQueryNode bool) {
      BoolQueryBuilder result = QueryBuilders.boolQuery();
      for (QueryNode child : bool.getChildren()) {
        if (child instanceof ModifierQueryNode modifier) {
          QueryBuilder clause = rewriteQuery(modifier.getChild());
          if (modifier.getModifier() == ModifierQueryNode.Modifier.MOD_NOT) result.mustNot(clause);
          else if (modifier.getModifier() == ModifierQueryNode.Modifier.MOD_REQ || node instanceof AndQueryNode)
            result.must(clause);
          else result.should(clause);
        } else if (node instanceof AndQueryNode) result.must(rewriteQuery(child));
        else result.should(rewriteQuery(child));
      }
      return result;
    }
    if (node instanceof ModifierQueryNode modifier) {
      QueryBuilder child = rewriteQuery(modifier.getChild());
      return modifier.getModifier() == ModifierQueryNode.Modifier.MOD_NOT
          ? QueryBuilders.boolQuery().mustNot(child) : child;
    }
    if (node.getClass() == FieldQueryNode.class || node instanceof QuotedFieldQueryNode)
      return rewriteTermQuery((FieldQueryNode) node);
    throw invalidQuery("Unsupported search syntax: " + node.getClass().getSimpleName(), null);
  }

  /** Re-escape literal punctuation, preserving only the wildcards the caller did not escape. */
  private String queryPattern(CharSequence text) {
    StringBuilder pattern = new StringBuilder();
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if ((c == '*' || c == '?') && !UnescapedCharSequence.wasEscaped(text, i)) pattern.append(c);
      else pattern.append(QueryParser.escape(String.valueOf(c)));
    }
    return pattern.toString();
  }

  private boolean hasWildcard(CharSequence text) {
    for (int i = 0; i < text.length(); i++)
      if ((text.charAt(i) == '*' || text.charAt(i) == '?') && !UnescapedCharSequence.wasEscaped(text, i)) return true;
    return false;
  }

  private QueryBuilder rewriteTermQuery(FieldQueryNode term) throws CedarProcessingException {
    String fieldName = term.getFieldAsString();
    String value = term.getTextAsString();
    boolean quoted = term instanceof QuotedFieldQueryNode;
    if (value.isEmpty()) throw invalidQuery("A search term must not be empty", null);
    if (fieldName == null || fieldName.isEmpty()) {
      if (value.startsWith(POSSIBLE_VALUES_PREFIX_ENCODED)) {
        boolean exact = value.startsWith(POSSIBLE_VALUES_EXACT_MATCH_PREFIX_ENCODED);
        String prefix = exact ? POSSIBLE_VALUES_EXACT_MATCH_PREFIX_ENCODED : POSSIBLE_VALUES_PREFIX_ENCODED;
        String literal = value.substring(prefix.length());
        if (literal.isEmpty()) throw invalidQuery("A possible-value search requires a value", null);
        if (exact) return QueryBuilders.boolQuery()
            .should(QueryBuilders.termQuery(VALUE_LABELS_KEYWORD, literal))
            .should(QueryBuilders.termQuery(VALUE_CONCEPTS, literal));
        if (quoted) return QueryBuilders.boolQuery()
            .should(QueryBuilders.matchPhraseQuery(VALUE_LABELS, literal))
            .should(QueryBuilders.matchPhraseQuery(VALUE_CONCEPTS, literal));
        CharSequence text = term.getText().subSequence(prefix.length(), term.getText().length());
        return QueryBuilders.queryStringQuery(queryPattern(text)).field(VALUE_LABELS).field(VALUE_CONCEPTS);
      }
      if (quoted) return QueryBuilders.boolQuery()
          .should(QueryBuilders.matchPhraseQuery(INFO_SCHEMA_NAME + ".raw", value).boost(10))
          .should(QueryBuilders.matchPhraseQuery(SUMMARY_RAW_TEXT, value));
      String pattern = queryPattern(term.getText());
      if (!hasWildcard(term.getText())) pattern = "*" + pattern + "*";
      return QueryBuilders.boolQuery()
          .should(QueryBuilders.queryStringQuery(pattern).field(INFO_SCHEMA_NAME + ".raw").boost(10))
          .should(QueryBuilders.queryStringQuery(pattern).field(SUMMARY_RAW_TEXT));
    }
    BoolQueryBuilder fields = QueryBuilders.boolQuery();
    if (!fieldName.equals("*") || UnescapedCharSequence.wasEscaped(term.getField(), 0)) {
      String pattern = enclosedByQuotes(fieldName)
          ? "\"" + QueryParser.escape(removeEnclosingQuotes(fieldName)) + "\""
          : queryPattern(term.getField());
      fields.must(QueryBuilders.queryStringQuery(pattern).field(INFO_FIELDS_FIELD_NAME).field(INFO_FIELDS_FIELD_PREFERRED_LABEL));
    }
    if (quoted || !value.equals("*") || UnescapedCharSequence.wasEscaped(term.getText(), 0)) {
      if (value.startsWith("http%3A%2F%2F") || value.startsWith("https%3A%2F%2F"))
        fields.must(QueryBuilders.termQuery(INFO_FIELDS_FIELD_VALUE_URI, value));
      else if (!quoted && hasWildcard(term.getText()))
        fields.must(QueryBuilders.queryStringQuery(queryPattern(term.getText())).field(INFO_FIELDS_FIELD_VALUE));
      else fields.must(QueryBuilders.matchPhraseQuery(INFO_FIELDS_FIELD_VALUE, value));
    }
    return QueryBuilders.nestedQuery(INFO_FIELDS, fields, ScoreMode.None);
  }

  private String preprocessQuery(String query) throws CedarProcessingException {
    query = encodeUrls(query);
    query = encodeWildcards(query);
    query = escapeDoubleQuotesInFieldName(query);
    query = escapeForwardSlashes(query);
    query = preprocessPossibleValuesQuery(query);
    query = escapeSpecialSymbols(query);
    return query;
  }

  private String encodeWildcards(String query) {
    StringBuilder processedQuery = new StringBuilder();
    StringBuilder unquotedSegment = new StringBuilder();
    boolean insideQuotes = false;
    for (int i = 0; i < query.length(); i++) {
      char current = query.charAt(i);
      boolean unescapedQuote = current == '"' && (i == 0 || query.charAt(i - 1) != '\\');
      if (unescapedQuote) {
        if (!insideQuotes) {
          // This segment ends at an opening quote, not at the end of a value. In publisher:"City",
          // inserting the existence wildcard here would detach the phrase from its field.
          processedQuery.append(encodeWildcardsInUnquotedSegment(unquotedSegment.toString(), processedQuery.isEmpty(), false));
          unquotedSegment.setLength(0);
        }
        processedQuery.append(current);
        insideQuotes = !insideQuotes;
      } else if (insideQuotes) {
        processedQuery.append(current);
      } else {
        unquotedSegment.append(current);
      }
    }
    processedQuery.append(encodeWildcardsInUnquotedSegment(unquotedSegment.toString(), processedQuery.isEmpty(), true));
    return processedQuery.toString();
  }

  private String encodeWildcardsInUnquotedSegment(String query, boolean startOfQuery, boolean endOfQuery) {
    String processedQuery = query;

    /**
     * Insert a star if missing for the cases t1: and :t1
     */
    if (startOfQuery) processedQuery = processedQuery.replaceAll("^:", "*:");
    processedQuery = processedQuery.replace("(:", "(*:");
    processedQuery = processedQuery.replaceAll("\\s+:", " *:");
    if (endOfQuery) processedQuery = processedQuery.replaceAll(":$", ":*");
    processedQuery = processedQuery.replaceAll(":(?=\\))", ":*");
    processedQuery = processedQuery.replaceAll(":(?=\\s+(?:AND|OR|NOT)\\b)", ":*");


    return processedQuery;
  }

  /**
   * Encode URLs
   */
  private String encodeUrls(String query) throws CedarProcessingException {

    final String URL_REGEX = "(((https?)://)" +
        "(%[0-9A-Fa-f]{2}|[-()_.!~*';/?:@&=+$,#A-Za-z0-9])+)" +
        "([).!';/?:,][[:blank:]])?";

    Matcher matcher = Pattern.compile(URL_REGEX).matcher(query);
    StringBuffer processedQuery = new StringBuffer();
    while (matcher.find()) {
      String matchString = query.substring(matcher.start(), matcher.end());
      String originalMatch = matchString;
      // A URI may contain balanced parentheses, but the closing parenthesis of the surrounding
      // query is syntax. Encoding that delimiter both changes the URI and leaves the query open.
      long opening = matchString.chars().filter(c -> c == '(').count();
      long closing = matchString.chars().filter(c -> c == ')').count();
      while (closing > opening && matchString.endsWith(")")) {
        matchString = matchString.substring(0, matchString.length() - 1);
        closing--;
      }
      try {
        // Encode Url
        String replacement = URLEncoder.encode(matchString, StandardCharsets.UTF_8.toString());
        matcher.appendReplacement(processedQuery, Matcher.quoteReplacement(
            replacement + originalMatch.substring(matchString.length())));
      } catch (UnsupportedEncodingException e) {
        throw new CedarProcessingException(e);
      }
    }
    matcher.appendTail(processedQuery);
    return processedQuery.toString();
  }

  /**
   * If the field name is enclosed in double quotes and (optionally) white spaces, encode them
   * <p>
   * Example 1: "title":"A nice study" -> \"title\":"A nice study"
   * Example 2: "study title":"A nice study" -> \"study\ title\":"A nice study"
   */
  private String escapeDoubleQuotesInFieldName(String query) {
    // The following regex will find all field names between double quotes, assuming that the field name itself does
    // not contain any quotes. Example:
    //    Input query: "studyidA":"aaa aaa" "studyid B":"bbb bbb" "study id C":"ccc ccc"
    //    Match 1: "studyidA":
    //    Match 2: "studyid B":
    //    Match 3: "study id C":
    Matcher matcherQuotesFieldName = Pattern.compile("\"([^\"]*)\":").matcher(query);
    String processedQuery = query;
    while (matcherQuotesFieldName.find()) {
      String matchString = query.substring(matcherQuotesFieldName.start(), matcherQuotesFieldName.end());
      // Escape quotes
      String replacement = matchString.replace("\"", "\\\"");
      // Escape white spaces (if there are any). Example: \"study id\": -> \"study\ id\"
      replacement = replacement.replaceAll("\\s+", "\\\\ ");
      processedQuery = processedQuery.replace(matchString, replacement);
    }
    return processedQuery;
  }

  private String escapeForwardSlashes(String query) {
    StringBuilder result = new StringBuilder();
    boolean escaped = false;
    for (int i = 0; i < query.length(); i++) {
      char c = query.charAt(i);
      if (c == '/' && !escaped) result.append('\\');
      result.append(c);
      escaped = c == '\\' && !escaped;
    }
    return result.toString();
  }

  /**
   * Preprocess possible values queries
   *
   * Syntax used for possible values queries:
   * Partial match syntax
   * [pv]term
   * [pv]"term1 term2"
   * Exact match syntax (full match between the query and the value in the index, ignoring case):
   * [pv]=term, [pv]="term"
   * [pv]="term1 term2"
   *
   * @param query
   * @return
   */
  private String preprocessPossibleValuesQuery(String query) {
    // Convert to lower case [PV] -> [pv]
    query = query.replace(POSSIBLE_VALUES_PREFIX.toUpperCase(Locale.ROOT), POSSIBLE_VALUES_PREFIX);
    if (query.contains(POSSIBLE_VALUES_PREFIX)) {
      // Translates [pv]"Not available" -> "[pv]Not available", so that the query is not identified as a boolean query
      // Also [pv]="Not available" -> "[pv]=Not available"
      query = query.replace(POSSIBLE_VALUES_EXACT_MATCH_PREFIX + "\"", "\"" + POSSIBLE_VALUES_EXACT_MATCH_PREFIX);
      query = query.replace(POSSIBLE_VALUES_PREFIX + "\"", "\"" + POSSIBLE_VALUES_PREFIX);

      // [pv]=term -> _pv_exact_term
      query = query.replace(POSSIBLE_VALUES_EXACT_MATCH_PREFIX, POSSIBLE_VALUES_EXACT_MATCH_PREFIX_ENCODED);
      // [pv]term -> _pv_term
      query = query.replace(POSSIBLE_VALUES_PREFIX, POSSIBLE_VALUES_PREFIX_ENCODED);
    }
    return query;
  }

  private String escapeSpecialSymbols(String query) {
    query = query.replace("[", "\\[");
    query = query.replace("]", "\\]");
    return query;
  }

  private String removeEnclosingQuotes(String query) {
    return query.substring(1, query.length() - 1);
  }

  public long searchAccessibleResourceCountByUser(List<String> resourceTypes,
                                                  ResourceRole role,
                                                  CedarUser user) throws CedarProcessingException {
    try {
      SearchRequest searchRequest = new SearchRequest(indexName);

      BoolQueryBuilder mainQuery = QueryBuilders.boolQuery();

      if (!user.has(CedarPermission.READ_NOT_READABLE_NODE)) {
        // Filter by user
        QueryBuilder userIdQuery = QueryBuilders.termsQuery(USERS, accessibleRoleKeys(user.getId(), role));
        BoolQueryBuilder permissionQuery = QueryBuilders.boolQuery();

        permissionQuery.should(userIdQuery);
        if (role == ResourceRole.VIEWER) {
          permissionQuery.should(QueryBuilders.termsQuery(COMPUTED_EVERYBODY_PERMISSION,
              NodeSharePermission.READ.getValue()));
        }
        if (role == ResourceRole.VIEWER || role == ResourceRole.EDITOR || role == ResourceRole.MANAGER) {
          permissionQuery.should(QueryBuilders.termsQuery(COMPUTED_EVERYBODY_PERMISSION,
              NodeSharePermission.WRITE.getValue()));
        }
        mainQuery.must(permissionQuery);
      }

      // Filter by resource type
      QueryBuilder resourceTypesQuery = QueryBuilders.termsQuery(RESOURCE_TYPE, resourceTypes);
      mainQuery.must(resourceTypesQuery);

      // Set main query
      SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
      searchSourceBuilder.query(mainQuery);
      searchSourceBuilder.trackTotalHits(true);
      searchRequest.source(searchSourceBuilder);

      // Execute request
      SearchResponse response = client.search(searchRequest, RequestOptions.DEFAULT);

      SearchHits hits = response.getHits();
      return hits.getTotalHits().value;
    } catch (IOException e) {
      throw new CedarDependencyUnavailableException("OpenSearch is unavailable", e);
    }
  }

  private static List<String> accessibleRoleKeys(String principalId, ResourceRole requiredRole) {
    java.util.ArrayList<String> keys = new java.util.ArrayList<>();
    for (ResourceRole candidate : ResourceRole.values()) {
      if (candidate.includes(requiredRole)) {
        keys.add(CedarNodeMaterializedPermissions.getKey(principalId, candidate));
      }
    }
    if (requiredRole == ResourceRole.VIEWER) {
      keys.add(principalId + "|read");
    }
    keys.add(principalId + "|write");
    return keys;
  }
}
