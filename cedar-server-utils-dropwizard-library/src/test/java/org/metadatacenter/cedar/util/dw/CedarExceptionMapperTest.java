package org.metadatacenter.cedar.util.dw;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.glassfish.jersey.server.ParamException;
import org.junit.jupiter.api.Test;
import org.metadatacenter.server.jsonld.UnresolvableIdentifierException;
import org.metadatacenter.util.http.CedarError;
import org.metadatacenter.util.http.CedarResponse;

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

  /**
   * An identifier the request supplied that cannot be resolved is answered with a 400 whose shared
   * error body carries the refusal's message. The refusal has no entity, and the body used to carry no
   * message.
   */
  @Test
  void anUnresolvableIdentifierIsAnsweredWithItsMessage() {
    try (Response response = new CedarExceptionMapper().toResponse(
        new UnresolvableIdentifierException("Invalid template identifier"))) {
      assertEquals(400, response.getStatus());
      assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getMediaType());
      CedarError error = (CedarError) response.getEntity();
      assertEquals("BAD_REQUEST", error.status);
      assertEquals(400, error.statusCode);
      assertEquals("Invalid template identifier", error.message);
      assertEquals("invalidInput", error.errorKey);
    }
  }

  /**
   * Any other bodyless refusal is answered exactly as before, without its message. Jersey's own
   * exceptions carry generic text, or the text of a failed conversion, that is not written for a
   * caller.
   */
  @Test
  void aPlainBodylessBadRequestKeepsItsMessageOutOfTheBody() throws Exception {
    ObjectMapper json = new ObjectMapper();
    try (Response response = new CedarExceptionMapper().toResponse(new BadRequestException("internal detail"));
         Response before = CedarResponse.status(400).build()) {
      assertEquals(400, response.getStatus());
      assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getMediaType());
      CedarError error = (CedarError) response.getEntity();
      assertNull(error.message);
      assertNull(error.errorKey);
      assertEquals(json.valueToTree(before.getEntity()), json.valueToTree(error));
    }
  }

  /**
   * A parameter refusal that reaches the mapper outside a request Jersey matched has no route to read a
   * declared type from, and is still answered with a message that identifies the parameter.
   */
  @Test
  void aConversionRefusalWithNoRouteStillIdentifiesTheParameter() {
    ParamException refusal = new ParamException.QueryParamException(
        new NumberFormatException("For input string: \"abc\""), "page", null);
    try (Response response = new CedarExceptionMapper().toResponse(refusal)) {
      assertEquals(400, response.getStatus());
      CedarError error = (CedarError) response.getEntity();
      assertEquals("The query parameter page is not in a form this route accepts", error.message);
      assertNull(error.errorKey);
    }
  }
}
