package org.metadatacenter.server.valuerecommender;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.config.WorkerValuerecommenderConfig;
import org.metadatacenter.id.CedarTemplateId;
import org.metadatacenter.id.CedarTemplateInstanceId;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.metadatacenter.server.service.UserService;
import org.metadatacenter.server.valuerecommender.model.ValuerecommenderReindexMessage;
import org.metadatacenter.server.valuerecommender.model.ValuerecommenderReindexMessageActionType;
import org.metadatacenter.server.valuerecommender.model.ValuerecommenderReindexMessageResourceType;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real HTTP responses through the worker's parser and scheduling decisions. */
@Timeout(30)
class ValuerecommenderReindexExecutorServiceTest {
  private static final String TEMPLATE = "https://repo.metadatacenter.orgx/templates/one";
  private static final String OTHER = "https://repo.metadatacenter.orgx/templates/two";
  private final AtomicReference<String> statusBody = new AtomicReference<>("[]");
  private final AtomicInteger statusCode = new AtomicInteger(200);
  private final AtomicInteger launchCode = new AtomicInteger(200);
  private final AtomicInteger launches = new AtomicInteger();
  private HttpServer server;
  private ValuerecommenderReindexExecutorService executor;
  private ValuerecommenderReindexQueueService queue;
  private WorkerValuerecommenderConfig limits;

  @BeforeEach void start() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/status", exchange -> {
      byte[] body = statusBody.get().getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      exchange.sendResponseHeaders(statusCode.get(), body.length);
      try (var output = exchange.getResponseBody()) { output.write(body); }
    });
    server.createContext("/launch", exchange -> {
      launches.incrementAndGet();
      exchange.sendResponseHeaders(launchCode.get(), -1);
      exchange.close();
    });
    server.start();
    String base = "http://127.0.0.1:" + server.getAddress().getPort();
    CedarConfig config = mock(CedarConfig.class, RETURNS_DEEP_STUBS);
    limits = config.getWorkerConfig().getValuerecommender();
    when(limits.getMaxReindexingThreadCount()).thenReturn(2);
    when(config.getMicroserviceUrlUtil().getValuerecommender().getCommandGenerateRulesStatus())
        .thenReturn(base + "/status");
    when(config.getMicroserviceUrlUtil().getValuerecommender().getCommandGenerateRules(any()))
        .thenReturn(base + "/launch");
    queue = mock(ValuerecommenderReindexQueueService.class);
    when(queue.enqueueEventWithResult(any())).thenReturn(true);
    executor = new ValuerecommenderReindexExecutorService(config, queue);
    UserService users = mock(UserService.class);
    CedarUser admin = mock(CedarUser.class);
    when(users.findUserByApiKey(any())).thenReturn(admin);
    when(admin.getFirstApiKeyAuthHeader()).thenReturn("apiKey test-key");
    executor.init(users);
  }

  @AfterEach void stop() { server.stop(0); }

  private static ValuerecommenderReindexMessage message() {
    return new ValuerecommenderReindexMessage(CedarTemplateId.build(TEMPLATE),
        CedarTemplateInstanceId.build("https://repo.metadatacenter.orgx/template-instances/one"),
        ValuerecommenderReindexMessageResourceType.INSTANCE, ValuerecommenderReindexMessageActionType.UPDATED);
  }

  private static String status(String id, String state) {
    return "[{\"templateId\":\"" + id + "\",\"status\":\"" + state + "\"}]";
  }

  @Test void activeTemplateIsDeferredUntilItCompletes() {
    var message = message();
    statusBody.set(status(TEMPLATE, "PROCESSING"));
    executor.handleMessages(List.of(message));
    assertEquals(0, launches.get(), "an active template must not be launched again");
    verify(queue).enqueueEventWithResult(message);

    statusBody.set(status(TEMPLATE, "COMPLETED"));
    executor.handleMessages(List.of(message));
    assertEquals(1, launches.get(), "the deferred update runs once the old generation finishes");
    verifyNoMoreInteractions(queue);
  }

  @Test void fullCapacityDefersExactlyOnceAndDoesNotFallThroughToLaunch() {
    when(limits.getMaxReindexingThreadCount()).thenReturn(1);
    var message = message();
    statusBody.set(status(OTHER, "PROCESSING"));
    executor.handleMessages(List.of(message));
    assertEquals(0, launches.get());
    verify(queue).enqueueEventWithResult(message);
    verifyNoMoreInteractions(queue);

    statusBody.set("[]");
    executor.handleMessages(List.of(message));
    assertEquals(1, launches.get());
  }

  @ParameterizedTest
  @ValueSource(strings = {"null", "[null]", "[{\"status\":\"PROCESSING\"}]",
      "[{\"templateId\":\"a-template\"}]", "not JSON"})
  void invalidStatusCannotMasqueradeAsAnIdleServer(String body) {
    statusBody.set(body);
    assertThrows(IllegalStateException.class, () -> executor.handleMessages(List.of(message())));
    assertEquals(0, launches.get());
    verifyNoInteractions(queue);

    statusBody.set("[]");
    assertDoesNotThrow(() -> executor.handleMessages(List.of(message())));
    assertEquals(1, launches.get(), "the retained message can be retried after status recovers");
  }

  @Test void unavailableStatusIsRetriedWithoutLaunchingBlindly() {
    statusCode.set(503);
    assertThrows(IllegalStateException.class, () -> executor.handleMessages(List.of(message())));
    assertEquals(0, launches.get());
    statusCode.set(200);
    executor.handleMessages(List.of(message()));
    assertEquals(1, launches.get());
  }

  @Test void refusedLaunchCannotAcknowledgeAndLoseTheUpdate() {
    launchCode.set(503);
    assertThrows(IllegalStateException.class, () -> executor.handleMessages(List.of(message())));
    launchCode.set(200);
    executor.handleMessages(List.of(message()));
    assertEquals(2, launches.get());
  }

  @Test void failedDeferralCannotAcknowledgeAndLoseTheUpdate() {
    statusBody.set(status(TEMPLATE, "PROCESSING"));
    when(queue.enqueueEventWithResult(any())).thenReturn(false);
    assertThrows(IllegalStateException.class, () -> executor.handleMessages(List.of(message())));
    assertEquals(0, launches.get());
  }

  @Test void duplicateUpdatesInOneBatchLaunchOnlyOneGeneration() {
    executor.handleMessages(List.of(message(), message()));
    assertEquals(1, launches.get());
    verifyNoInteractions(queue);
  }
}
