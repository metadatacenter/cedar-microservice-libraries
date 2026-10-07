package org.metadatacenter.cedar.util.dw;

import jakarta.ws.rs.container.*;
import jakarta.ws.rs.core.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.metadatacenter.server.logging.*;
import org.metadatacenter.server.logging.filter.*;
import org.metadatacenter.server.logging.model.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.metadatacenter.constant.CedarHeaderParameters.*;

class RequestLoggingLifecycleTest {
  List<AppLogMessage> events;
  AppLoggerQueueService previousQueue;
  String previousSuppression;
  @BeforeEach void capture() {
    events = new ArrayList<>();
    previousQueue = AppLogger.appLoggerQueueService;
    previousSuppression = System.getProperty("cedar.test.suppressAppLogQueue");
    var queue = mock(AppLoggerQueueService.class);
    doAnswer(call -> { events.add(call.getArgument(0)); return null; }).when(queue).enqueueEvent(any());
    AppLogger.appLoggerQueueService = queue;
    System.setProperty("cedar.test.suppressAppLogQueue","false");
  }
  @AfterEach void restore() {
    AppLogger.appLoggerQueueService = previousQueue;
    if (previousSuppression == null) System.clearProperty("cedar.test.suppressAppLogQueue");
    else System.setProperty("cedar.test.suppressAppLogQueue",previousSuppression);
    ThreadLocalRequestIdHolder.setLoggingContext(null);
  }
  ContainerRequestContext request(String method, String global) {
    var request = mock(ContainerRequestContext.class);
    var headers = new MultivaluedHashMap<String,String>();
    if (global != null) headers.putSingle(GLOBAL_REQUEST_ID_KEY,global);
    headers.putSingle(LOCAL_REQUEST_ID_KEY,"untrusted-local");
    when(request.getMethod()).thenReturn(method);
    when(request.getHeaders()).thenReturn(headers);
    when(request.getHeaderString(anyString())).thenAnswer(c -> headers.getFirst(c.getArgument(0)));
    var uri = mock(UriInfo.class); when(uri.getPath()).thenReturn("fixture");
    when(uri.getQueryParameters()).thenReturn(new MultivaluedHashMap<>()); when(request.getUriInfo()).thenReturn(uri);
    Map<String,Object> properties = new HashMap<>();
    when(request.getProperty(anyString())).thenAnswer(c -> properties.get(c.getArgument(0)));
    doAnswer(c -> { properties.put(c.getArgument(0),c.getArgument(1)); return null; }).when(request).setProperty(anyString(),any());
    doAnswer(c -> { properties.remove(c.getArgument(0)); return null; }).when(request).removeProperty(anyString());
    return request;
  }
  void respond(ContainerRequestContext request, int status) throws Exception {
    var response = mock(ContainerResponseContext.class); when(response.getStatus()).thenReturn(status);
    new ResponseLoggerFilter().filter(request,response);
  }
  @ParameterizedTest @ValueSource(ints={200,400,401,403,404,405,429,500,503})
  void reusedWorkerNeverRetainsThePreviousRequest(int status) throws Exception {
    String previousLocal = null;
    for (String global : Arrays.asList("caller",null,"next-caller")) {
      var request = request("GET",global);
      new RequestIdGeneratorFilter().filter(request);
      var context = ThreadLocalRequestIdHolder.getLoggingContext();
      assertNotNull(context);
      if (global != null) assertEquals(global,context.getGlobalRequestId());
      assertNotEquals("untrusted-local",context.getLocalRequestId());
      assertNotEquals(previousLocal,context.getLocalRequestId());
      previousLocal = context.getLocalRequestId();
      respond(request,status);
      assertNull(ThreadLocalRequestIdHolder.getLoggingContext());
      var start = events.get(events.size()-2); var end = events.get(events.size()-1);
      assertEquals(AppLogSubType.START,start.getSubType()); assertEquals(AppLogSubType.END,end.getSubType());
      assertEquals(start.getGlobalRequestId(),end.getGlobalRequestId());
      assertEquals(start.getLocalRequestId(),end.getLocalRequestId());
      assertEquals(status,end.getParamAsInt(AppLogParam.STATUS));
    }
  }
  @ParameterizedTest @ValueSource(booleans={false,true})
  void optionsNeverInheritsContextOrProducesAnOrphanEnd(boolean interned) throws Exception {
    ThreadLocalRequestIdHolder.setLoggingContext(new LoggingContext("old-global","old-local"));
    var request = request(interned ? "OPTIONS" : new String("OPTIONS"),null);
    new RequestIdGeneratorFilter().filter(request);
    respond(request,200);
    assertNull(ThreadLocalRequestIdHolder.getLoggingContext());
    assertTrue(events.isEmpty());
  }
  @ParameterizedTest @ValueSource(booleans={false,true})
  void servletScopeCleansUpEvenWithoutAResponseFilter(boolean fail) throws Exception {
    ThreadLocalRequestIdHolder.setLoggingContext(new LoggingContext("stale","stale"));
    jakarta.servlet.FilterChain chain = (request,response) -> {
      assertNull(ThreadLocalRequestIdHolder.getLoggingContext());
      ThreadLocalRequestIdHolder.setLoggingContext(new LoggingContext("current","current"));
      if(fail) throw new java.io.IOException("fixture serialization failure");
    };
    var scope = new RequestLoggingScopeFilter();
    var request = mock(jakarta.servlet.ServletRequest.class);
    var response = mock(jakarta.servlet.ServletResponse.class);
    if(fail) assertThrows(java.io.IOException.class, () -> scope.doFilter(request,response,chain));
    else scope.doFilter(request,response,chain);
    assertNull(ThreadLocalRequestIdHolder.getLoggingContext());
  }
  @Test void endKeepsTheStartIdsAndIsEmittedOnlyOnce() throws Exception {
    var request = request("GET","caller");
    new RequestIdGeneratorFilter().filter(request);
    request.getHeaders().putSingle(GLOBAL_REQUEST_ID_KEY,"changed-global");
    request.getHeaders().putSingle(LOCAL_REQUEST_ID_KEY,"changed-local");
    respond(request,200); respond(request,200);
    assertEquals(2,events.size());
    assertEquals(events.get(0).getGlobalRequestId(),events.get(1).getGlobalRequestId());
    assertEquals(events.get(0).getLocalRequestId(),events.get(1).getLocalRequestId());
  }
  @Test void aLoggingFailureStillClearsTheWorker() throws Exception {
    var request = request("GET","caller");
    new RequestIdGeneratorFilter().filter(request);
    doThrow(new IllegalStateException("fixture queue failure")).when(AppLogger.appLoggerQueueService).enqueueEvent(any());
    assertThrows(IllegalStateException.class, () -> respond(request,500));
    assertNull(ThreadLocalRequestIdHolder.getLoggingContext());
  }
  @Test void aFailedStartDoesNotProduceAnOrphanEnd() throws Exception {
    var request = request("GET","caller");
    doThrow(new IllegalStateException("fixture start failure")).when(AppLogger.appLoggerQueueService).enqueueEvent(any());
    var scope = new RequestLoggingScopeFilter();
    assertThrows(IllegalStateException.class, () -> scope.doFilter(mock(jakarta.servlet.ServletRequest.class),
        mock(jakarta.servlet.ServletResponse.class), (req,res) -> new RequestIdGeneratorFilter().filter(request)));
    assertNull(ThreadLocalRequestIdHolder.getLoggingContext());
    doAnswer(call -> { events.add(call.getArgument(0)); return null; }).when(AppLogger.appLoggerQueueService).enqueueEvent(any());
    respond(request,500);
    assertTrue(events.isEmpty());
  }
}
