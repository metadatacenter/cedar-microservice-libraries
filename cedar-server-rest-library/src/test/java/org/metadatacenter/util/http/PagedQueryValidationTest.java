package org.metadatacenter.util.http;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.metadatacenter.config.PaginationConfig;
import org.metadatacenter.http.CedarResponseStatus;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.rest.exception.CedarAssertionException;
import org.metadatacenter.server.security.model.user.ResourcePublicationStatusFilter;
import org.metadatacenter.server.security.model.user.ResourceVersionFilter;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PagedQueryValidationTest {

  private static final int RESULT_WINDOW = 1000;

  private PaginationConfig config;

  @BeforeEach
  void setUp() {
    config = mock(PaginationConfig.class);
    when(config.getDefaultPageSize()).thenReturn(25);
    when(config.getMaxPageSize()).thenReturn(100);
    when(config.getMaxOffset()).thenReturn(5000);
  }

  @Test
  void barePagedQueryUsesConfiguredLimitAndZeroOffset() throws Exception {
    PagedQuery query = new PagedQuery(config);

    query.validate();

    assertEquals(25, query.getLimit());
    assertEquals(0, query.getOffset());
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 25, 99, 100})
  void acceptsPositiveLimitsThroughConfiguredMaximum(int limit) throws Exception {
    PagedQuery query = new PagedQuery(config).limit(Optional.of(limit));
    query.validate();
    assertEquals(limit, query.getLimit());
  }

  @ParameterizedTest
  @ValueSource(ints = {Integer.MIN_VALUE, -1, 0, 101, Integer.MAX_VALUE})
  void rejectsLimitsOutsideConfiguredRangeAsBadRequests(int limit) {
    CedarAssertionException error = assertThrows(CedarAssertionException.class,
        () -> new PagedQuery(config).limit(Optional.of(limit)).validate());
    assertBadRequest(error);
    assertEquals(limit, error.getErrorPack().getParameters().get("limit"));
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 100, Integer.MAX_VALUE})
  void acceptsEveryNonNegativeOffset(int offset) throws Exception {
    PagedQuery query = new PagedQuery(config).offset(Optional.of(offset));
    query.validate();
    assertEquals(offset, query.getOffset());
  }

  @ParameterizedTest
  @ValueSource(ints = {Integer.MIN_VALUE, -100, -1})
  void rejectsNegativeOffsetsAsBadRequests(int offset) {
    CedarAssertionException error = assertThrows(CedarAssertionException.class,
        () -> new PagedQuery(config).offset(Optional.of(offset)).validate());
    assertBadRequest(error);
    assertEquals(offset, error.getErrorPack().getParameters().get("offset"));
  }

  @Test
  void bareSortedQueryDefaultsToName() throws Exception {
    PagedSortedQuery query = new PagedSortedQuery(config);
    query.validate();
    assertEquals(List.of("name"), query.getSortList());
    assertEquals("name", query.getSortListAsString());
  }

  static Stream<Arguments> validSorts() {
    return Stream.of(
        Arguments.of("name", List.of("name")),
        Arguments.of("-name", List.of("-name")),
        Arguments.of("createdOnTS", List.of("createdOnTS")),
        Arguments.of("-createdOnTS", List.of("-createdOnTS")),
        Arguments.of("lastUpdatedOnTS", List.of("lastUpdatedOnTS")),
        Arguments.of("-lastUpdatedOnTS", List.of("-lastUpdatedOnTS")),
        Arguments.of("name,-createdOnTS,lastUpdatedOnTS", List.of("name", "-createdOnTS", "lastUpdatedOnTS")),
        Arguments.of("  name,-lastUpdatedOnTS  ", List.of("name", "-lastUpdatedOnTS"))
    );
  }

  @ParameterizedTest
  @MethodSource("validSorts")
  void acceptsKnownAscendingDescendingAndCompoundSorts(String input, List<String> expected) throws Exception {
    PagedSortedQuery query = new PagedSortedQuery(config).sort(Optional.of(input));
    query.validate();
    assertEquals(expected, query.getSortList());
    assertEquals(String.join(",", expected), query.getSortListAsString());
  }

  @ParameterizedTest
  @ValueSource(strings = {"bogus", "-bogus", "Name", "createdOn", "--name", "name,bogus", "name,-bogus"})
  void rejectsUnknownSortFieldsAsBadRequests(String sort) {
    CedarAssertionException error = assertThrows(CedarAssertionException.class,
        () -> new PagedSortedQuery(config).sort(Optional.of(sort)).validate());
    assertBadRequest(error);
    assertEquals(sort.contains(",") ? sort.substring(sort.lastIndexOf(',') + 1) : sort,
        error.getErrorPack().getParameters().get("sort"));
  }

  @Test
  void bareTypedQueryDefaultsEveryFilter() throws Exception {
    PagedSortedTypedQuery query = new PagedSortedTypedQuery(config);
    query.validate();
    assertEquals(List.of(CedarResourceType.FOLDER, CedarResourceType.FIELD, CedarResourceType.ELEMENT,
        CedarResourceType.TEMPLATE, CedarResourceType.INSTANCE), query.getResourceTypeList());
    assertEquals(ResourceVersionFilter.ALL, query.getVersion());
    assertEquals(ResourcePublicationStatusFilter.ALL, query.getPublicationStatus());
  }

  static Stream<Arguments> validResourceTypes() {
    return Stream.of(
        Arguments.of("folder", List.of(CedarResourceType.FOLDER)),
        Arguments.of("field", List.of(CedarResourceType.FIELD)),
        Arguments.of("element", List.of(CedarResourceType.ELEMENT)),
        Arguments.of("template", List.of(CedarResourceType.TEMPLATE)),
        Arguments.of("instance", List.of(CedarResourceType.INSTANCE)),
        Arguments.of("field,element,template", List.of(CedarResourceType.FIELD, CedarResourceType.ELEMENT, CedarResourceType.TEMPLATE)),
        Arguments.of(" folder,instance ", List.of(CedarResourceType.FOLDER, CedarResourceType.INSTANCE))
    );
  }

  @ParameterizedTest
  @MethodSource("validResourceTypes")
  void parsesEveryRestResourceTypeAndPreservesRequestedOrder(String input, List<CedarResourceType> expected) throws Exception {
    PagedSortedTypedQuery query = typedQuery().resourceTypes(Optional.of(input));
    query.validate();
    assertEquals(expected, query.getResourceTypeList());
    assertEquals(expected.stream().map(CedarResourceType::getValue).toList(), query.getResourceTypeAsStringList());
    assertEquals(expected.stream().map(CedarResourceType::getValue).reduce((a, b) -> a + "," + b).orElse(""),
        query.getResourceTypesAsString());
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "user", "group", "category", "element-instance", "unknown", "TEMPLATE"})
  void rejectsEmptyInternalAndUnknownResourceTypes(String resourceTypes) {
    CedarAssertionException error = assertThrows(CedarAssertionException.class,
        () -> typedQuery().resourceTypes(Optional.of(resourceTypes)).validate());
    assertBadRequest(error);
  }

  @ParameterizedTest
  @ValueSource(strings = {"latest", "latest-by-status", "all"})
  void acceptsEveryVersionFilter(String value) throws Exception {
    PagedSortedTypedQuery query = typedQuery().version(Optional.of(value));
    query.validate();
    assertEquals(value, query.getVersionAsString());
    assertEquals(ResourceVersionFilter.forValue(value), query.getVersion());
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "LATEST", "draft", "latest_by_status", " all "})
  void rejectsEmptyAndUnknownVersionFilters(String value) {
    CedarAssertionException error = assertThrows(CedarAssertionException.class,
        () -> typedQuery().version(Optional.of(value)).validate());
    assertBadRequest(error);
    assertEquals(value, error.getErrorPack().getParameters().get("version"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"bibo:draft", "bibo:published", "all"})
  void acceptsEveryPublicationStatusFilter(String value) throws Exception {
    PagedSortedTypedQuery query = typedQuery().publicationStatus(Optional.of(value));
    query.validate();
    assertEquals(value, query.getPublicationStatusAsString());
    assertEquals(ResourcePublicationStatusFilter.forValue(value), query.getPublicationStatus());
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "draft", "published", "BIBO:DRAFT", " all "})
  void rejectsEmptyAndUnknownPublicationStatusFilters(String value) {
    CedarAssertionException error = assertThrows(CedarAssertionException.class,
        () -> typedQuery().publicationStatus(Optional.of(value)).validate());
    assertBadRequest(error);
    assertEquals(value, error.getErrorPack().getParameters().get("publicationStatus"));
  }

  @Test
  void bareSearchQueryValidatesWithNullSearchSelectors() throws Exception {
    PagedSortedTypedSearchQuery query = new PagedSortedTypedSearchQuery(config);
    query.validate();
    assertNull(query.getQ());
    assertNull(query.getId());
    assertNull(query.getCategoryId());
    assertNull(query.getMode());
    assertNull(query.getIsBasedOn());
  }

  @ParameterizedTest
  @ValueSource(strings = {"heart", "a b", "x:y", "  retained whitespace  "})
  void acceptsNonBlankSearchText(String value) throws Exception {
    PagedSortedTypedSearchQuery query = searchQuery().q(Optional.of(value));
    query.validate();
    assertEquals(value, query.getQ());
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "\t\n"})
  void rejectsBlankSearchText(String value) {
    assertSearchBadRequest(q -> q.q(Optional.of(value)), "q", value);
  }

  @ParameterizedTest
  @ValueSource(strings = {"artifact-1", "https://repo.example/templates/1", "  opaque id  "})
  void acceptsNonBlankIdSelector(String value) throws Exception {
    PagedSortedTypedSearchQuery query = searchQuery().id(Optional.of(value));
    query.validate();
    assertEquals(value, query.getId());
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "\t"})
  void rejectsBlankIdSelector(String value) {
    assertSearchBadRequest(q -> q.id(Optional.of(value)), "id", value);
  }

  @ParameterizedTest
  @ValueSource(strings = {"category-1", "https://repo.example/categories/1", "  category id  "})
  void acceptsNonBlankCategorySelector(String value) throws Exception {
    PagedSortedTypedSearchQuery query = searchQuery().categoryId(Optional.of(value));
    query.validate();
    assertEquals(value, query.getCategoryId());
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "\n"})
  void rejectsBlankCategorySelector(String value) {
    assertSearchBadRequest(q -> q.categoryId(Optional.of(value)), "categoryId", value);
  }

  @ParameterizedTest
  @ValueSource(strings = {"all", "special", "custom-mode", "  mode  "})
  void acceptsNonBlankMode(String value) throws Exception {
    PagedSortedTypedSearchQuery query = searchQuery().mode(Optional.of(value));
    query.validate();
    assertEquals(value, query.getMode());
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "\r\n"})
  void rejectsBlankMode(String value) {
    assertSearchBadRequest(q -> q.mode(Optional.of(value)), "modeInput", value);
  }

  @ParameterizedTest
  @ValueSource(strings = {"https://repo.example/templates/1", "http://localhost/template", "ftp://example.org/template"})
  void acceptsUrlShapedIsBasedOnSelectorAndForcesInstanceType(String value) throws Exception {
    PagedSortedTypedSearchQuery query = searchQuery().isBasedOn(Optional.of(value));
    query.validate();
    assertEquals(value, query.getIsBasedOn());
    assertEquals(List.of(CedarResourceType.INSTANCE), query.getResourceTypeList());
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "not-a-url", "/templates/1", "repo.example/templates/1"})
  void rejectsMalformedIsBasedOnSelector(String value) {
    assertSearchBadRequest(q -> q.isBasedOn(Optional.of(value)), "template_id", value);
  }

  @Test
  void rejectsResourceTypesWhenIsBasedOnIsPresent() {
    PagedSortedTypedSearchQuery query = searchQuery()
        .isBasedOn(Optional.of("https://repo.example/templates/1"))
        .resourceTypes(Optional.of("instance"));
    CedarAssertionException error = assertThrows(CedarAssertionException.class, query::validate);
    assertBadRequest(error);
    assertEquals("instance", error.getErrorPack().getParameters().get("resource_types"));
  }

  static Stream<Arguments> filterStringProjections() {
    return Stream.of(
        Arguments.of("latest", "bibo:draft"),
        Arguments.of("latest", "bibo:published"),
        Arguments.of("latest-by-status", "all"),
        Arguments.of("all", "bibo:draft"),
        Arguments.of("all", "all"));
  }

  @ParameterizedTest
  @MethodSource("filterStringProjections")
  void exposesValidatedVersionAndPublicationFiltersForDownstreamQueries(String version, String publication) throws Exception {
    PagedSortedTypedQuery query = typedQuery()
        .version(Optional.of(version))
        .publicationStatus(Optional.of(publication));
    query.validate();
    assertEquals(version, query.getVersionAsString());
    assertEquals(publication, query.getPublicationStatusAsString());
  }

  static Stream<Arguments> servableWindows() {
    return Stream.of(Arguments.of(0, 1), Arguments.of(0, 100), Arguments.of(900, 100), Arguments.of(999, 1));
  }

  @ParameterizedTest
  @MethodSource("servableWindows")
  void shallowSearchAcceptsAWindowTheIndexCanServe(int offset, int limit) throws Exception {
    PagedSortedTypedSearchQuery query = searchQuery().limit(Optional.of(limit)).offset(Optional.of(offset));
    query.validate();
    query.validateShallowWindow(RESULT_WINDOW);
    assertEquals(offset, query.getOffset());
  }

  static Stream<Arguments> unservableWindows() {
    return Stream.of(Arguments.of(1000, 1), Arguments.of(901, 100), Arguments.of(Integer.MAX_VALUE, 100));
  }

  @ParameterizedTest
  @MethodSource("unservableWindows")
  void shallowSearchRefusesAWindowWiderThanTheIndexResultWindow(int offset, int limit) throws Exception {
    // OpenSearch rejects from+size past index.max_result_window, which reaches the caller as a 500
    // unless it is refused here. The message has to send them to the call that can serve it.
    PagedSortedTypedSearchQuery query = searchQuery().limit(Optional.of(limit)).offset(Optional.of(offset));
    query.validate();

    CedarAssertionException error = assertThrows(CedarAssertionException.class, () -> query.validateShallowWindow(RESULT_WINDOW));

    assertBadRequest(error);
    assertEquals(offset, error.getErrorPack().getParameters().get("offset"));
    assertTrue(error.getMessage().contains("/search-deep"), error.getMessage());
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1000, 4999, 5000})
  void deepSearchAcceptsAnyOffsetThroughTheConfiguredMaximum(int offset) throws Exception {
    PagedSortedTypedSearchQuery query = searchQuery().offset(Optional.of(offset));
    query.validate();
    query.validateDeepOffset();
    assertEquals(offset, query.getOffset());
  }

  @ParameterizedTest
  @ValueSource(ints = {5001, 250_000, Integer.MAX_VALUE})
  void deepSearchRefusesAnOffsetPastTheWalkItWillPayFor(int offset) throws Exception {
    PagedSortedTypedSearchQuery query = searchQuery().offset(Optional.of(offset));
    query.validate();

    CedarAssertionException error = assertThrows(CedarAssertionException.class, query::validateDeepOffset);

    assertBadRequest(error);
    assertEquals(offset, error.getErrorPack().getParameters().get("offset"));
    assertTrue(error.getMessage().contains("5000"), error.getMessage());
  }

  @Test
  void theDeepMaximumIsTheOnlyOffsetBoundTheDeepCallApplies() throws Exception {
    // The window that stops the shallow call is exactly what the deep one exists to page past.
    PagedSortedTypedSearchQuery query = searchQuery().limit(Optional.of(100)).offset(Optional.of(4000));
    query.validate();

    query.validateDeepOffset();
    assertThrows(CedarAssertionException.class, () -> query.validateShallowWindow(RESULT_WINDOW));
  }

  private PagedSortedTypedQuery typedQuery() {
    return new PagedSortedTypedQuery(config);
  }

  private PagedSortedTypedSearchQuery searchQuery() {
    return new PagedSortedTypedSearchQuery(config);
  }

  private void assertSearchBadRequest(Consumer<PagedSortedTypedSearchQuery> configure, String parameter, String value) {
    PagedSortedTypedSearchQuery query = searchQuery();
    configure.accept(query);
    CedarAssertionException error = assertThrows(CedarAssertionException.class, query::validate);
    assertBadRequest(error);
    assertEquals(value, error.getErrorPack().getParameters().get(parameter));
  }

  private static void assertBadRequest(CedarAssertionException error) {
    assertEquals(CedarResponseStatus.BAD_REQUEST, error.getErrorPack().getStatus());
  }
}
