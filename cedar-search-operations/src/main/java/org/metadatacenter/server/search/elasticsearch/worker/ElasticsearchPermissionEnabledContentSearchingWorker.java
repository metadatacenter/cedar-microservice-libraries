package org.metadatacenter.server.search.elasticsearch.worker;

import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.unit.Fuzziness;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.*;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.sort.SortOrder;
import org.metadatacenter.config.ElasticsearchConfig;
import org.metadatacenter.error.CedarErrorKey;
import org.metadatacenter.exception.CedarProcessingException;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.search.IndexedDocumentType;
import org.metadatacenter.server.security.model.auth.CedarNodeMaterializedPermissions;
import org.metadatacenter.server.security.model.auth.CedarPermission;
import org.metadatacenter.server.security.model.auth.NodeSharePermission;
import org.metadatacenter.server.security.model.permission.resource.FilesystemResourcePermission;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.metadatacenter.server.security.model.user.ResourcePublicationStatusFilter;
import org.metadatacenter.server.security.model.user.ResourceVersionFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.metadatacenter.constant.ElasticsearchConstants.*;

public class ElasticsearchPermissionEnabledContentSearchingWorker {

  private static final Logger log = LoggerFactory.getLogger(ElasticsearchPermissionEnabledContentSearchingWorker.class);

  private final Client client;
  private final String indexName;

  public ElasticsearchPermissionEnabledContentSearchingWorker(ElasticsearchConfig config, Client client) {
    this.client = client;
    this.indexName = config.getIndexes().getSearchIndex().getName();
  }

  public SearchResponseResult search(CedarRequestContext rctx, String query, List<String> resourceTypes, ResourceVersionFilter version,
                                     ResourcePublicationStatusFilter publicationStatus, String categoryId, List<String> sortList, int limit,
                                     int offset) throws CedarProcessingException {

    SearchRequestBuilder searchRequest = getSearchRequestBuilder(rctx, query, resourceTypes, version, publicationStatus, categoryId, sortList);

    searchRequest.setFrom(offset);
    searchRequest.setSize(limit);

    // Execute request
    SearchResponse response = searchRequest.execute().actionGet();

    SearchResponseResult result = new SearchResponseResult();
    result.setTotalCount(response.getHits().getTotalHits());

    for (SearchHit hit : response.getHits()) {
      result.add(hit);
    }

    return result;
  }

  // It uses the scroll API. It retrieves all results. No pagination and therefore no offset. Scrolling is not
  // intended for real time user requests, but rather for processing large amounts of data.
  // More info: https://www.elastic.co/guide/en/elasticsearch/reference/2.3/search-request-scroll.html
  public SearchResponseResult searchDeep(CedarRequestContext rctx, String query, List<String> resourceTypes, ResourceVersionFilter version,
                                         ResourcePublicationStatusFilter publicationStatus, String categoryId, List<String> sortList, int limit,
                                         int offset) throws CedarProcessingException {

    SearchRequestBuilder searchRequest = getSearchRequestBuilder(rctx, query, resourceTypes, version, publicationStatus, categoryId, sortList);

    // Set scroll and scroll size
    TimeValue timeout = TimeValue.timeValueMinutes(2);
    searchRequest.setScroll(timeout);
    searchRequest.setSize(offset + limit);

    //log.debug("Search query in Query DSL: " + searchRequest);

    // Execute request
    SearchResponse response = searchRequest.execute().actionGet();

    SearchResponseResult result = new SearchResponseResult();
    result.setTotalCount(response.getHits().getTotalHits());

    int counter = 0;
    while (response.getHits().getHits().length != 0) {
      for (SearchHit hit : response.getHits().getHits()) {
        if (counter >= offset && counter < offset + limit) {
          result.add(hit);
        }
        counter++;
      }
      //next scroll
      response = client.prepareSearchScroll(response.getScrollId()).setScroll(timeout).execute().actionGet();
    }
    return result;
  }

  private SearchRequestBuilder getSearchRequestBuilder(CedarRequestContext rctx, String query, List<String> resourceTypes,
                                                       ResourceVersionFilter version, ResourcePublicationStatusFilter publicationStatus,
                                                       String categoryId, List<String> sortList) throws CedarProcessingException {

    SearchRequestBuilder searchRequestBuilder = client.prepareSearch(indexName).setTypes(IndexedDocumentType.DOC.getValue());

    BoolQueryBuilder mainQuery = QueryBuilders.boolQuery();

    if (query != null && query.length() > 0) {

      query = preprocessQuery(query);

      // QUERY TYPE: General query (Query artifact title and/or description - stored as 'summaryText' in the index)
//      if (!query.contains(FIELD_NAME_VALUE_SEPARATOR) && !query.contains(POSSIBLE_VALUES_PREFIX_ENCODED)) {
//        // Query enclosed by quotes: Example: "My Study Template"
//        if (enclosedByQuotes(query)) {
//          query = removeEnclosingQuotes(query);
//          QueryBuilder summaryTextQuery = QueryBuilders.matchPhraseQuery(SUMMARY_RAW_TEXT, query);
//          mainQuery.must(summaryTextQuery);
//        }
//        // Not enclosed by quotes: Example: T1
//        else {
//          QueryParser parser = new QueryParser("", new WhitespaceAnalyzer());
//          try {
//            parser.parse(query); // will throw a ParseException if it cannot parse it
//            QueryBuilder summaryTextQuery = QueryBuilders.queryStringQuery(query).field(SUMMARY_TEXT);
//            mainQuery.must(summaryTextQuery);
//          } catch (ParseException e) {
//            CedarProcessingException ex = new CedarProcessingException("Error processing query: " + query, e);
//            ex.getErrorPack().errorKey(CedarErrorKey.MALFORMED_SEARCH_SYNTAX);
//            throw ex;
//          }
//        }
//      }
      // QUERY TYPE: Field query or Possible values query
      // Query field name/value (infoFields) or possible field values, optionally combined with artifact id and description (summaryText).
      // Sample query 1: 'disease:cancer AND Template3'
      // Sample query 2: '[pv]female OR [pv]male'
      //else {
        // Parse the query and rewrite it to query the right index fields. The whitespace analyzer divides text into
        // terms whenever it encounters any whitespace character. It does not lowercase terms.
        QueryParser parser = new QueryParser("", new WhitespaceAnalyzer());
        try {
          Query queryParsed = parser.parse(query);
          mainQuery.must(rewriteQuery(queryParsed));
        } catch (ParseException e) {
          throw new CedarProcessingException("Error processing query: " + query, e);
        }
      //}
    }

    String userId = rctx.getCedarUser().getId();
    if (!rctx.getCedarUser().has(CedarPermission.READ_NOT_READABLE_NODE)) {
      // Filter by user
      QueryBuilder userIdQuery = QueryBuilders.termQuery(USERS, CedarNodeMaterializedPermissions.getKey(userId, FilesystemResourcePermission.READ));
      BoolQueryBuilder permissionQuery = QueryBuilders.boolQuery();

      QueryBuilder everybodyReadQuery = QueryBuilders.termsQuery(COMPUTED_EVERYBODY_PERMISSION, NodeSharePermission.READ.getValue());
      QueryBuilder everybodyWriteQuery = QueryBuilders.termsQuery(COMPUTED_EVERYBODY_PERMISSION, NodeSharePermission.WRITE.getValue());

      permissionQuery.should(userIdQuery);
      permissionQuery.should(everybodyReadQuery);
      permissionQuery.should(everybodyWriteQuery);
      mainQuery.must(permissionQuery);
    }

    // Filter by resource type
    if (resourceTypes != null && resourceTypes.size() > 0) {
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
      QueryBuilder publicationStatusEqualsQuery = QueryBuilders.termsQuery(INFO_BIBO_STATUS, publicationStatus.getValue());
      inner1Query.must(publicationStatusEqualsQuery);
      BoolQueryBuilder inner2Query = QueryBuilders.boolQuery();
      QueryBuilder publicationStatusExistsQuery = QueryBuilders.existsQuery(INFO_BIBO_STATUS);
      inner2Query.mustNot(publicationStatusExistsQuery);
      publicationStatusQuery.should(inner1Query);
      publicationStatusQuery.should(inner2Query);
      mainQuery.must(publicationStatusQuery);
    }

    // Filter by category id
    if (categoryId != null && categoryId.length() > 0) {
      QueryBuilder categoryIdQuery = QueryBuilders.termsQuery(CATEGORIES, categoryId);
      mainQuery.must(categoryIdQuery);
    }

    // Set main query
    searchRequestBuilder.setQuery(mainQuery);
    log.info("Search query in Query DSL:\n" + mainQuery);

    // Sort by field
    // The name is stored on the resource, so we can sort by that
    if (sortList != null && sortList.size() > 0) {
      for (String s : sortList) {
        SortOrder sortOrder = SortOrder.ASC;
        if (s.startsWith(ES_SORT_DESC_PREFIX)) {
          sortOrder = SortOrder.DESC;
          s = s.substring(1);
        }
        if (SORT_BY_NAME.equals(s)) {
          searchRequestBuilder.addSort(INFO_SCHEMA_NAME, sortOrder);
        } else if (SORT_LAST_UPDATED_ON_FIELD.equals(s)) {
          searchRequestBuilder.addSort(INFO_PAV_LAST_UPDATED_ON, sortOrder);
        } else if (SORT_CREATED_ON_FIELD.equals(s)) {
          searchRequestBuilder.addSort(INFO_PAV_CREATED_ON, sortOrder);
        }
      }
    }
    return searchRequestBuilder;
  }

  private boolean enclosedByQuotes(String keyword) {
    return keyword.startsWith("\"") && keyword.endsWith("\"");
  }

  private boolean enclosedByParentheses(String keyword) {
    return keyword.startsWith("(") && keyword.endsWith(")");
  }

  /**
   * Rewrites the query generated by the QueryParser to work with our index. This is a recursive method that iterates
   * over all the query clauses and rewrites them.
   *
   * @param inputQuery
   * @return
   */
  private QueryBuilder rewriteQuery(Query inputQuery) throws CedarProcessingException {

    // Example: 't1 AND (disease:crc OR tissue:kidney)' -> This query has two main clauses
    if (inputQuery instanceof BooleanQuery) {
      BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();

      for (BooleanClause clause : ((BooleanQuery) inputQuery).clauses()) {
        if (clause.getOccur().equals(BooleanClause.Occur.SHOULD)) {
          boolQueryBuilder = boolQueryBuilder.should(rewriteQuery(clause.getQuery()));
        } else if (clause.getOccur().equals(BooleanClause.Occur.MUST)) {
          boolQueryBuilder = boolQueryBuilder.must(rewriteQuery(clause.getQuery()));
        } else if (clause.getOccur().equals(BooleanClause.Occur.MUST_NOT)) {
          boolQueryBuilder = boolQueryBuilder.mustNot(rewriteQuery(clause.getQuery()));
        }
      }
      return boolQueryBuilder;
    }
    // Examples: 'kidney', 'tissue:kidney', '[pv]female'
    else if (inputQuery instanceof TermQuery) {
      Term term = ((TermQuery) inputQuery).getTerm();
      return rewriteTermQuery(term.field(), term.bytes().utf8ToString());
    }
    // Examples: 'colorectal cancer', 'disease:"colorectal cancer"', '[pv]"Abnormal - Not clinically significant" '
    else if (inputQuery instanceof PhraseQuery) {
      return rewritePhraseQuery((PhraseQuery) inputQuery);
    }
    // Example: 'disease:influ*'
    else if (inputQuery instanceof MultiTermQuery) {
      if (inputQuery instanceof PrefixQuery) {
        Term prefix = ((PrefixQuery) inputQuery).getPrefix();
        return rewriteTermQuery(prefix.field(), prefix.bytes().utf8ToString(), true);
      } else if (inputQuery instanceof WildcardQuery) {
        Term term = ((WildcardQuery) inputQuery).getTerm();
        return rewriteTermQuery(term.field(), term.bytes().utf8ToString());
      } else {
        throw new CedarProcessingException("Query type not supported: " + inputQuery.getClass());
      }
    } else {
      throw new CedarProcessingException("Query type not supported: " + inputQuery.getClass());
    }
  }

  private QueryBuilder rewritePhraseQuery(PhraseQuery inputQuery) throws CedarProcessingException {
    String fieldName = inputQuery.getTerms()[0].field();

    if (fieldName.isEmpty()) { // Example: "colorectal carcinoma"
      return rewriteTermQuery(fieldName, inputQuery.toString());
    } else { // Example: disease:"colorectal carcinoma"
      String[] phraseQueryTokens = inputQuery.toString().split(":");
      if (phraseQueryTokens.length >= 2) {
        String fieldValue = phraseQueryTokens[1];
        return rewriteTermQuery(fieldName, fieldValue);
      } else {
        throw new IllegalArgumentException("Could not parse query");
      }
    }
  }

  private QueryBuilder rewriteTermQuery(String fieldName, String fieldValue) throws CedarProcessingException {
    return rewriteTermQuery(fieldName, fieldValue, false);
  }

  /**
   *
   * @param fieldName
   * @param fieldValue
   * @param withPrefix Indicates if it is a prefix query. A PrefixQuery is built by QueryParser for inputs like app*.
   * @return
   * @throws CedarProcessingException
   */
  private QueryBuilder rewriteTermQuery(String fieldName, String fieldValue, boolean withPrefix) throws CedarProcessingException {
    QueryBuilder query;
    if (fieldName == null || fieldName.isEmpty()) {
      // QUERY TYPE: Possible-values
      if (fieldValue.contains(POSSIBLE_VALUES_PREFIX_ENCODED)) {
        // Exact-match
        // - [pv]=term (which was previously translated to _pv_exact_term)
        // - [pv]="term1 term2" (which was previously translated to _pv_exact_"term1 term2")
        if (fieldValue.contains(POSSIBLE_VALUES_EXACT_MATCH_PREFIX_ENCODED)) {
          // Remove possible-values-exact prefix
          fieldValue = fieldValue.replace(POSSIBLE_VALUES_EXACT_MATCH_PREFIX_ENCODED, "");
          if (enclosedByQuotes(fieldValue)) {
            fieldValue = removeEnclosingQuotes(fieldValue);
          }
          // Build term queries
          query = QueryBuilders.boolQuery();
          ((BoolQueryBuilder) query).should(QueryBuilders.termQuery(VALUE_LABELS_KEYWORD, fieldValue)); // query value labels
          ((BoolQueryBuilder) query).should(QueryBuilders.termQuery(VALUE_CONCEPTS, fieldValue)); // query value concepts
        }
        // Non-exact match
        // - [pv]term
        // - [pv]"term1 term2"
        else {
          // Remove possible-values prefix
          fieldValue = fieldValue.replace(POSSIBLE_VALUES_PREFIX_ENCODED, "");
          query = QueryBuilders.queryStringQuery(fieldValue);
          // There is no need to query value concepts, only value labels.
          // The concepts query will be addressed by the exact-match query above
          ((QueryStringQueryBuilder) query).field(VALUE_LABELS);
          ((QueryStringQueryBuilder) query).field(VALUE_CONCEPTS);
        }
      }
      // QUERY TYPE: General (schema:name, summaryText)
      else {
//        if (enclosedByQuotes(fieldValue)) {
//          fieldValue = removeEnclosingQuotes(fieldValue);
//        }
        //query = QueryBuilders.matchPhraseQuery(SUMMARY_RAW_TEXT, fieldValue);
        query = QueryBuilders.boolQuery();
        ((BoolQueryBuilder) query).should(QueryBuilders.matchPhraseQuery(SUMMARY_RAW_TEXT, fieldValue)); // summary text
        ((BoolQueryBuilder) query).should((QueryBuilders.queryStringQuery(fieldValue)).field(INFO_SCHEMA_NAME + ".raw")); // schema:name
      }
    }
    // QUERY TYPE: Field name/value
    else {
      QueryBuilder infoFieldsQueryString = QueryBuilders.queryStringQuery(generateInfoFieldsQueryString(fieldName, fieldValue, withPrefix));
      query = QueryBuilders.nestedQuery(INFO_FIELDS, infoFieldsQueryString, ScoreMode.None);
    }
    return query;
  }

  private String generateInfoFieldsQueryString(String fieldName, String fieldValue, boolean withPrefix) throws CedarProcessingException {
    String result;
    // Generate field name query. Example: '(infoFields.fieldName:title1 OR infoFields.fieldName:title2)'
    String fieldNameQueryFragment = "(".concat(INFO_FIELDS_FIELD_NAME).concat(":").concat(fieldName)
        .concat(" OR ").concat(INFO_FIELDS_FIELD_PREFERRED_LABEL).concat(":").concat(fieldName).concat(")");
    String infoFieldsFieldValue = INFO_FIELDS_FIELD_VALUE;
    if (fieldValue.contains("http")) {
      infoFieldsFieldValue = INFO_FIELDS_FIELD_VALUE_URI;
    }
    // Generate field value query. Example: 'infoFields.fieldValue:value1'
    String fieldValueQueryFragment = infoFieldsFieldValue.concat(":").concat(fieldValue);
    String andQueryFragment = " AND ";
    if ((fieldName.compareTo(ANY_STRING) != 0) && (fieldValue.compareTo(ANY_STRING) != 0)) {
      result = fieldNameQueryFragment.concat(andQueryFragment).concat(fieldValueQueryFragment);
    } else if ((fieldName.compareTo(ANY_STRING) != 0) && (fieldValue.compareTo(ANY_STRING) == 0)) {
      result = fieldNameQueryFragment;
    } else if ((fieldName.compareTo(ANY_STRING) == 0) && (fieldValue.compareTo(ANY_STRING) != 0)) {
      result = fieldValueQueryFragment;
    } else { //fieldName:_any_ AND fieldValue:_any -> Return everything
      return "*";
    }

    if (withPrefix) {
      result = result.concat("*");
    }
    return result;
  }

  private String preprocessQuery(String query) throws CedarProcessingException {
    query = encodeWildcards(query);
    query = encodeUrls(query);
    query = escapeDoubleQuotesInFieldName(query);
    query = removeForwardSlashes(query);
    query = preprocessPossibleValuesQuery(query);
    query = escapeSpecialSymbols(query);
    return query;
  }

  private String encodeWildcards(String query) {
    String processedQuery = query;

    /**
     * Insert a star if missing for the cases t1: and :t1
     */
    processedQuery = processedQuery.replaceAll("^:", "*:");
    processedQuery = processedQuery.replaceAll("\\s+:", " *:");
    processedQuery = processedQuery.replaceAll(":$", ":*");
    processedQuery = processedQuery.replaceAll(":\\s+", ":* ");


    /**
     * Replace stars by '_any_' for the cases *:v1, f1:*, and *:*
     */
    processedQuery = processedQuery.replaceAll("(^|\\()\\*:", ANY_STRING + ":");
    processedQuery = processedQuery.replaceAll("\\s\\*:", ANY_STRING + " :");
    processedQuery = processedQuery.replaceAll(":\\*($|\\()", ":" + ANY_STRING);
    processedQuery = processedQuery.replaceAll(":\\*\\s", ":" + ANY_STRING + " ");

    /**
     * Encode stars and question marks embedded into fieldName and/or fieldValue
     * Elasticsearch does not accept unencoded wildcards in the field name (e.g. disea*:crc), so
     * we will encode them all, including those that are part of the field value for simplicity
     */

    /* Encode stars. Example: aaa*aaa:b\*bb as aaa\*aaa:b\*bb */
    processedQuery = processedQuery.replace("\\*", "*");
    processedQuery = processedQuery.replace("*", "\\*");

    /* Encode question marks. Example: aaa?aaa:b\?bb as aaa\?aaa:b\?bb */
    processedQuery = processedQuery.replace("\\?", "?");
    processedQuery = processedQuery.replace("?", "\\?");

    return processedQuery;
  }

  /**
   * Encode URLs
   */
  private String encodeUrls(String query) throws CedarProcessingException {

    final String URL_REGEX = "(((https?)://)" +
        "(%[0-9A-Fa-f]{2}|[-()_.!~*';/?:@&=+$,A-Za-z0-9])+)" +
        "([).!';/?:,][[:blank:]])?";

    Matcher matcher = Pattern.compile(URL_REGEX).matcher(query);
    String processedQuery = query;
    while (matcher.find()) {
      String matchString = query.substring(matcher.start(), matcher.end());
      try {
        // Encode Url
        String replacement = URLEncoder.encode(matchString, StandardCharsets.UTF_8.toString());
        processedQuery = processedQuery.replace(matchString, replacement);
      } catch (UnsupportedEncodingException e) {
        throw new CedarProcessingException(e);
      }
    }
    return processedQuery;
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

  private String removeForwardSlashes(String query) {
    return query.replaceAll("\\/", "");
  }

  /**
   * Preprocess possible values queries
   *
   * Syntax used for possible values queries:
   *    Partial match syntax
   *      [pv]term
   *      [pv]"term1 term2"
   *    Exact match syntax (full match between the query and the value in the index, ignoring case):
   *      [pv]=term, [pv]="term"
   *      [pv]="term1 term2"
   *
   * @param query
   * @return
   */
  private String preprocessPossibleValuesQuery(String query) {
    // Convert to lower case [PV] -> [pv]
    query = query.replace(POSSIBLE_VALUES_PREFIX.toUpperCase(), POSSIBLE_VALUES_PREFIX);
    if (query.contains(POSSIBLE_VALUES_PREFIX)) {
      // Translates [pv]"Not available" -> "[pv]Not available", so that the query is not identified as a boolean query
      // Also [pv]="Not available" -> "[pv]=Not available"
      query = query.replace(POSSIBLE_VALUES_EXACT_MATCH_PREFIX + "\"", "\"" + POSSIBLE_VALUES_EXACT_MATCH_PREFIX);
      query = query.replace(POSSIBLE_VALUES_PREFIX + "\"", "\"" + POSSIBLE_VALUES_PREFIX);

      if (enclosedByQuotes(query) && query.trim().split("\\s+").length == 1) {
        query = removeEnclosingQuotes(query);
      }

      if (query.contains(POSSIBLE_VALUES_EXACT_MATCH_PREFIX)) {
        // [pv]=term -> _pv_exact_term
        query = query.replace(POSSIBLE_VALUES_EXACT_MATCH_PREFIX, POSSIBLE_VALUES_EXACT_MATCH_PREFIX_ENCODED);
      }
      else {
        // [pv]term -> _pv_term
        query = query.replace(POSSIBLE_VALUES_PREFIX, POSSIBLE_VALUES_PREFIX_ENCODED);
      }
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

  public long searchAccessibleResourceCountByUser(List<String> resourceTypes, FilesystemResourcePermission permission, CedarUser user) {

    SearchRequestBuilder searchRequest = client.prepareSearch(indexName).setTypes(IndexedDocumentType.DOC.getValue());

    BoolQueryBuilder mainQuery = QueryBuilders.boolQuery();

    if (!user.has(CedarPermission.READ_NOT_READABLE_NODE)) {
      // Filter by user
      QueryBuilder userIdQuery = QueryBuilders.termQuery(USERS, CedarNodeMaterializedPermissions.getKey(user.getId(), permission));
      BoolQueryBuilder permissionQuery = QueryBuilders.boolQuery();

      QueryBuilder everybodyReadQuery = QueryBuilders.termsQuery(COMPUTED_EVERYBODY_PERMISSION, NodeSharePermission.READ.getValue());
      QueryBuilder everybodyWriteQuery = QueryBuilders.termsQuery(COMPUTED_EVERYBODY_PERMISSION, NodeSharePermission.WRITE.getValue());

      permissionQuery.should(userIdQuery);
      permissionQuery.should(everybodyReadQuery);
      permissionQuery.should(everybodyWriteQuery);
      mainQuery.must(permissionQuery);
    }

    // Filter by resource type
    QueryBuilder resourceTypesQuery = QueryBuilders.termsQuery(RESOURCE_TYPE, resourceTypes);
    mainQuery.must(resourceTypesQuery);

    // Set main query
    searchRequest.setQuery(mainQuery);

    // Execute request
    SearchResponse response = searchRequest.execute().actionGet();
    return response.getHits().getTotalHits();
  }
}
