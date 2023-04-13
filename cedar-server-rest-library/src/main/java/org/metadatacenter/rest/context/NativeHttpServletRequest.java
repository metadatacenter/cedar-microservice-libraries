package org.metadatacenter.rest.context;

import com.fasterxml.jackson.databind.JsonNode;
import org.metadatacenter.exception.CedarBadRequestException;
import org.metadatacenter.rest.assertion.noun.CedarRequestBody;
import org.metadatacenter.rest.assertion.noun.CedarRequestNoun;
import org.metadatacenter.util.json.JsonMapper;

import javax.servlet.http.HttpServletRequest;
import java.io.InputStreamReader;
import java.io.PushbackInputStream;

import static org.metadatacenter.constant.HttpConstants.HTTP_HEADER_CONTENT_TYPE;

public class NativeHttpServletRequest extends CedarRequestNoun {

  private final HttpServletRequest nativeRequest;
  private JsonNode jsonBodyNode;

  NativeHttpServletRequest(HttpServletRequest request) {
    if (request == null) {
      throw new IllegalArgumentException("The HttpServletRequest should never be null at this point");
    }
    this.nativeRequest = request;
  }

  @Override
  public CedarRequestBody getRequestBody() throws CedarBadRequestException {
    if (jsonBodyNode == null) {
      try {
        PushbackInputStream pushbackInputStream = new PushbackInputStream(nativeRequest.getInputStream());
        int b;
        b = pushbackInputStream.read();
        if (b == -1) {
          return new HttpRequestEmptyBody();
        }
        pushbackInputStream.unread(b);
        jsonBodyNode = JsonMapper.MAPPER.readTree(new InputStreamReader(pushbackInputStream));
      } catch (Exception e) {
        throw new CedarBadRequestException("There was an error deserializing the request body", e);
      }
    }

    return new HttpRequestJsonBody(jsonBodyNode);
  }

  @Override
  public String getContentType() {
    if (nativeRequest != null) {
      return nativeRequest.getHeader(HTTP_HEADER_CONTENT_TYPE);
    }
    return null;
  }
}
