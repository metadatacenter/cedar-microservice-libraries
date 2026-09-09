package org.metadatacenter.cedar.util.dw;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.metadatacenter.error.CedarErrorPack;
import org.metadatacenter.exception.CedarException;
import org.metadatacenter.exception.CedarProcessingException;
import org.metadatacenter.rest.exception.CedarAssertionException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The error pack's 500 is a fallback, and a pack nobody statused reported a caller's mistake as a
 * server fault with nothing to show the omission. Every exception type decides its status now, and
 * the mapper flags the one shape that still can fall through: a pack built bare and thrown as an
 * anonymous CedarException.
 */
class UndecidedStatusTest {

  private Logger logger;
  private Level originalLevel;
  private boolean originalAdditivity;
  private ListAppender<ILoggingEvent> appender;

  @BeforeEach
  void captureLog() {
    logger = (Logger) org.slf4j.LoggerFactory.getLogger(CedarCedarExceptionMapper.class);
    originalLevel = logger.getLevel();
    originalAdditivity = logger.isAdditive();
    appender = new ListAppender<>();
    appender.start();
    logger.setLevel(Level.DEBUG);
    logger.setAdditive(false);
    logger.addAppender(appender);
  }

  @AfterEach
  void releaseLog() {
    logger.detachAppender(appender);
    logger.setLevel(originalLevel);
    logger.setAdditive(originalAdditivity);
    appender.stop();
  }

  private List<ILoggingEvent> undecidedWarnings() {
    return appender.list.stream()
        .filter(event -> event.getLevel() == Level.WARN
            && event.getFormattedMessage().contains("no decided status"))
        .toList();
  }

  @Test
  @DisplayName("A bare pack answers 500 and the mapper says nobody decided it")
  void barePackIsFlagged() {
    CedarException undecided = new CedarException(new CedarErrorPack().message("built bare")) {
    };
    try (Response response = new CedarCedarExceptionMapper().toResponse(undecided)) {
      assertEquals(500, response.getStatus());
      assertEquals(1, undecidedWarnings().size());
      assertTrue(undecidedWarnings().get(0).getFormattedMessage().contains(UndecidedStatusTest.class.getName()),
          "the warning should name the throw site");
    }
  }

  @Test
  @DisplayName("An assertion answers 400 and a processing failure 500, both decided")
  void declaredStatusesAreNotFlagged() {
    try (Response response = new CedarCedarExceptionMapper().toResponse(
        new CedarAssertionException("field_names and summary=true exclude each other"))) {
      assertEquals(400, response.getStatus());
    }
    try (Response response = new CedarCedarExceptionMapper().toResponse(
        new CedarProcessingException("the store answered badly"))) {
      assertEquals(500, response.getStatus());
    }
    assertEquals(0, undecidedWarnings().size());
  }
}
