package org.metadatacenter.cedar.util.dw;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
}
