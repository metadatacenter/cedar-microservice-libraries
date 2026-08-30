package org.metadatacenter.rest.context;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NativeHttpServletRequestTest {

  @Test
  void decodesJsonBodiesAsUtf8() throws Exception {
    byte[] body = "{\"name\":\"Café 文字\"}".getBytes(StandardCharsets.UTF_8);
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getInputStream()).thenReturn(servletInputStream(body));

    NativeHttpServletRequest wrappedRequest = new NativeHttpServletRequest(request);

    assertEquals("Café 文字", wrappedRequest.getRequestBody().asJson().get("name").textValue());
  }

  private static ServletInputStream servletInputStream(byte[] body) {
    ByteArrayInputStream input = new ByteArrayInputStream(body);
    return new ServletInputStream() {
      @Override
      public boolean isFinished() {
        return input.available() == 0;
      }

      @Override
      public boolean isReady() {
        return true;
      }

      @Override
      public void setReadListener(ReadListener readListener) {
      }

      @Override
      public int read() {
        return input.read();
      }
    };
  }
}
