package org.metadatacenter.cedar.util.dw;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;
import org.metadatacenter.util.http.CedarError;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CedarExceptionMapperTest {

  @Test
  void ordinaryJaxRsNotFoundIsDebugRatherThanAnOperationalWarning() {
    Logger logger = (Logger) org.slf4j.LoggerFactory.getLogger(CedarExceptionMapper.class);
    Level originalLevel = logger.getLevel();
    boolean originalAdditivity = logger.isAdditive();
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.setLevel(Level.DEBUG);
    logger.setAdditive(false);
    logger.addAppender(appender);

    try (Response response = new CedarExceptionMapper().toResponse(new NotFoundException("missing"))) {
      assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
      CedarError error = (CedarError) response.getEntity();
      assertEquals("NOT_FOUND", error.status);
      assertEquals(404, error.statusCode);
      assertEquals(1, appender.list.size());
      assertEquals(Level.DEBUG, appender.list.get(0).getLevel());
      assertNull(appender.list.get(0).getThrowableProxy());
    } finally {
      logger.detachAppender(appender);
      logger.setLevel(originalLevel);
      logger.setAdditive(originalAdditivity);
      appender.stop();
    }
  }

  @Test
  void uncommonFrameworkStatusStillGetsTheCanonicalBody() {
    WebApplicationException exception = new WebApplicationException(Response.status(429).build());

    try (Response response = new CedarExceptionMapper().toResponse(exception)) {
      assertEquals(429, response.getStatus());
      CedarError error = (CedarError) response.getEntity();
      assertNotNull(error.status);
      assertEquals(429, error.statusCode);
      assertNotNull(error.parameters);
      assertNotNull(error.objects);
      assertNotNull(error.entities);
    }
  }
}
