package org.metadatacenter.server.cache.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.CacheStats;
import com.google.common.cache.LoadingCache;
import org.apache.commons.codec.EncoderException;
import org.apache.commons.codec.net.URLCodec;
import org.apache.commons.lang.CharEncoding;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.exception.CedarProcessingException;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.rest.context.CedarRequestContextFactory;
import org.metadatacenter.server.security.model.user.CedarUserSummary;
import org.metadatacenter.server.service.UserService;
import org.metadatacenter.server.url.MicroserviceUrlUtil;
import org.metadatacenter.util.http.ProxyUtil;
import org.metadatacenter.util.json.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class UserSummaryCache {

  private static final Logger log = LoggerFactory.getLogger(UserSummaryCache.class);

  private static UserSummaryCache instance = new UserSummaryCache();

  private static LoadingCache<String, CedarUserSummary> userSummaryCache;

  private static CedarConfig cedarConfig;
  private static UserService userService;
  private static MicroserviceUrlUtil microserviceUrlUtil;

  public static UserSummaryCache getInstance() {
    return instance;
  }

  public static void init(CedarConfig cedarConfig, UserService userService) {
    UserSummaryCache.cedarConfig = cedarConfig;
    UserSummaryCache.userService = userService;
    UserSummaryCache.microserviceUrlUtil = cedarConfig.getMicroserviceUrlUtil();
    if (userSummaryCache == null) {
      userSummaryCache =
          CacheBuilder.newBuilder()
              .concurrencyLevel(10)
              .maximumSize(10000)
              .expireAfterAccess(30, TimeUnit.MINUTES)
              .recordStats()
              .build(new CacheLoader<>() {
                @Override
                public CedarUserSummary load(String id) throws Exception {
                  log.info("Fetching CedarUserSummary from microservice/ Cache Miss");
                  return instance.getUserSummary(id);
                }
              });
    }
  }

  public CedarUserSummary getUser(String id) {
    if (id == null) {
      return null;
    }
    try {
      return userSummaryCache.get(id);
    } catch (ExecutionException e) {
      log.error("Error Retrieving Elements from the CedarUserSummary Cache" + e.getMessage());
    }
    return null;
  }

  public CacheStats getStats() {
    return userSummaryCache.stats();
  }

  private CedarUserSummary getUserSummary(String id) throws CedarProcessingException {
    CedarRequestContext context = CedarRequestContextFactory.fromAdminUser(cedarConfig, userService);
    String uuid = extractUserUUID(id);
    String url = microserviceUrlUtil.getUser().UuidSummary(uuid);
    HttpResponse proxyResponse = null;
    try {
      proxyResponse = ProxyUtil.proxyGet(url, context);
      HttpEntity entity = proxyResponse.getEntity();
      if (entity != null) {
        String userSummaryString = EntityUtils.toString(entity, CharEncoding.UTF_8);
        if (userSummaryString != null && !userSummaryString.isEmpty()) {
          JsonNode jsonNode = JsonMapper.MAPPER.readTree(userSummaryString);
          JsonNode at = jsonNode.at("/screenName");
          if (at != null && !at.isMissingNode()) {
            CedarUserSummary summary = new CedarUserSummary();
            summary.setScreenName(at.asText());
            summary.setId(id);
            return summary;
          }
        }
      }
    } catch (IOException e) {
      throw new CedarProcessingException(e);
    }
    return null;
  }

  private static String extractUserUUID(String userURL) {
    String id = userURL;
    try {
      int pos = userURL.lastIndexOf('/');
      if (pos > -1) {
        id = userURL.substring(pos + 1);
      }
      id = new URLCodec().encode(id);
    } catch (EncoderException e) {
      log.error("Error while extracting user UUID", e);
    }
    return id;
  }

}
