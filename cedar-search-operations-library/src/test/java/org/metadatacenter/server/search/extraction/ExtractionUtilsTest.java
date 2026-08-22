package org.metadatacenter.server.search.extraction;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.exception.CedarProcessingException;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.util.http.ProxyUtil;
import org.mockito.MockedStatic;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Retrieval of artifact content for indexing.
 * <p>
 * The distinction this pins down is between an artifact that is not there and an artifact server
 * that cannot answer. Both used to collapse into one exception, which made a deleted artifact
 * indistinguishable from an outage: the first is routine and must not fail the index write, the
 * second is a real failure that has to reach the consumer's retry and dead-letter handling.
 */
class ExtractionUtilsTest {

  private static final String ARTIFACT_ID = "https://repo.example.org/templates/artifact-1";

  private ExtractionUtils extractionUtils;
  private CedarRequestContext requestContext;
  private MockedStatic<ProxyUtil> proxy;

  @BeforeEach
  void setUp() {
    CedarConfig cedarConfig = mock(CedarConfig.class, RETURNS_DEEP_STUBS);
    when(cedarConfig.getMicroserviceUrlUtil().getArtifact().getResourceType(any()))
        .thenReturn("http://artifact/templates");
    extractionUtils = new ExtractionUtils(cedarConfig);
    requestContext = mock(CedarRequestContext.class);
    proxy = mockStatic(ProxyUtil.class);
  }

  @AfterEach
  void tearDown() {
    proxy.close();
  }

  private void stubResponse(int code, String body) throws Exception {
    ClassicHttpResponse response = mock(ClassicHttpResponse.class);
    when(response.getCode()).thenReturn(code);
    when(response.getEntity())
        .thenReturn(body == null ? null : new StringEntity(body, StandardCharsets.UTF_8));
    proxy.when(() -> ProxyUtil.proxyGet(anyString(), any(CedarRequestContext.class))).thenReturn(response);
  }

  private Optional<JsonNode> get() throws CedarProcessingException {
    return extractionUtils.getArtifactById(ARTIFACT_ID, CedarResourceType.TEMPLATE, requestContext);
  }

  @Test
  void anArtifactThatExistsComesBackWithItsContent() throws Exception {
    stubResponse(200, "{\"schema:name\":\"A template\"}");

    Optional<JsonNode> artifact = get();

    assertTrue(artifact.isPresent());
    assertEquals("A template", artifact.get().get("schema:name").asText());
  }

  /**
   * The defect this covers: an artifact the artifact server no longer holds threw, which aborted
   * the whole index write for the resource and every resource behind it in the same event.
   */
  @Test
  void anArtifactTheServerDoesNotHoldIsAbsentRatherThanAFailure() throws Exception {
    stubResponse(404, "not found");

    assertTrue(get().isEmpty(), "a missing artifact is a normal outcome, not an error");
  }

  /**
   * The other half: an outage must stay a failure. Reporting it as absence would silently strip
   * content out of the index and report success.
   */
  @Test
  void anArtifactServerThatFailsIsReportedAsAFailure() throws Exception {
    stubResponse(500, "boom");

    CedarProcessingException error = assertThrows(CedarProcessingException.class, this::get);

    assertTrue(error.getMessage().contains(ARTIFACT_ID), "the message should name the artifact");
    assertTrue(error.getMessage().contains("500"),
        "the message should carry the status code, so the cause is visible rather than guessed at");
  }

  @Test
  void anUnauthorizedRetrievalIsAFailureNotAnAbsence() throws Exception {
    stubResponse(403, "forbidden");

    assertThrows(CedarProcessingException.class, this::get);
  }

  @Test
  void contentThatIsNotJsonIsReportedAsAFailure() throws Exception {
    stubResponse(200, "this is not json");

    assertThrows(CedarProcessingException.class, this::get);
  }
}
