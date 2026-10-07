package org.metadatacenter.util.http;

import com.sun.net.httpserver.HttpServer;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.metadatacenter.rest.context.CedarRequestContext;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProxyRelayContractTest {
  @ParameterizedTest @ValueSource(strings={"Retry-After", "WWW-Authenticate", "Allow", "Location", "Cache-Control"})
  void preservesProtocolHeaders(String name) {
    var upstream=new BasicClassicHttpResponse(429); upstream.addHeader(name,"value");
    var downstream=mock(HttpServletResponse.class);
    ProxyUtil.proxyResponseHeaders(upstream,downstream);
    verify(downstream).setHeader(name,"value");
  }
  @Test void retainsEveryValueWithoutForwardingConnectionHeaders() {
    var upstream=new BasicClassicHttpResponse(200);
    upstream.addHeader("Vary","Accept"); upstream.addHeader("vary","Accept-Encoding");
    upstream.addHeader("Connection","ETag"); upstream.addHeader("ETag","hop-specific");
    upstream.addHeader("Set-Cookie","session=upstream"); upstream.addHeader("Content-Length","100");
    upstream.addHeader("Content-Encoding","gzip");
    var downstream=mock(HttpServletResponse.class); ProxyUtil.proxyResponseHeaders(upstream,downstream);
    verify(downstream).setHeader("Vary","Accept"); verify(downstream).addHeader("vary","Accept-Encoding");
    verifyNoMoreInteractions(downstream);
  }
  @ParameterizedTest @ValueSource(ints={301,302,303,307,308})
  void authenticatedProxiesDoNotFollowRedirects(int code) throws Exception {
    var calls=new AtomicInteger(); var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
    server.createContext("/", e->{ calls.incrementAndGet(); e.getRequestBody().readAllBytes();
      if(e.getRequestURI().getPath().equals("/target")) e.sendResponseHeaders(204,-1);
      else {e.getResponseHeaders().set("Location","/target"); e.sendResponseHeaders(code,-1);} e.close(); });
    server.start();
    var context=mock(CedarRequestContext.class); when(context.getAuthorizationHeader()).thenReturn("apiKey fixture");
    String url="http://127.0.0.1:"+server.getAddress().getPort()+"/source";
    try {
      for(int variant=0;variant<5;variant++) {
        int before=calls.get();
        try(var response=switch(variant) {
          case 0 -> ProxyUtil.proxyGet(url,context);
          case 1 -> ProxyUtil.proxyGet(url,context,Map.of("Accept","application/json"));
          case 2 -> ProxyUtil.proxyPost(url,context,"{}");
          case 3 -> ProxyUtil.proxyPut(url,context,"{}");
          default -> ProxyUtil.proxyDelete(url,context);
        }) {assertEquals(code,response.getCode());assertEquals(before+1,calls.get());}
      }
    } finally {server.stop(0);}
  }
}
