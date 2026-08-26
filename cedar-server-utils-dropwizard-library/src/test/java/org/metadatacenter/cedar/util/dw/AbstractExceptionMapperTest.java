package org.metadatacenter.cedar.util.dw;

import org.junit.jupiter.api.Test;
import org.metadatacenter.error.CedarErrorPack;
import org.metadatacenter.http.CedarResponseStatus;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class AbstractExceptionMapperTest {

  private final AbstractExceptionMapper mapper = new AbstractExceptionMapper() {
  };

  @Test
  void clientCopyKeepsTheContractButRemovesInternalExceptionDetails() {
    CedarErrorPack original = new CedarErrorPack()
        .status(CedarResponseStatus.SERVICE_UNAVAILABLE)
        .message("Downstream service is unavailable")
        .sourceException(new IOException("Connect to http://secret-host:1234 failed"));

    CedarErrorPack client = mapper.clientSafeCopy(original);

    assertEquals(CedarResponseStatus.SERVICE_UNAVAILABLE, client.getStatus());
    assertEquals("Downstream service is unavailable", client.getMessage());
    assertNull(client.getOriginalException());
    assertNull(client.getSourceException());

    assertNotNull(original.getOriginalException(), "sanitizing the response must not erase log detail");
    assertNotNull(original.getSourceException(), "sanitizing the response must not erase log detail");
  }
}
