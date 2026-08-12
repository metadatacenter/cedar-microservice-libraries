package org.metadatacenter.server.cache.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.CacheStats;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.UncheckedExecutionException;
import org.apache.commons.codec.EncoderException;
import org.apache.commons.codec.net.URLCodec;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
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
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class UserSummaryCache {

  private static final Logger log = LoggerFactory.getLogger(UserSummaryCache.class);

  private static UserSummaryCache instance = new UserSummaryCache();

  private static LoadingCache<String, CedarUserSummary> userSummaryCache;

  /**
   * How long an id that could not be resolved is remembered as unresolvable.
   * <p>
   * Guava caches a value, never a failure, so an id the user server cannot answer for was fetched
   * again on every single lookup, each attempt waiting out the 20-second socket timeout. That cost
   * multiplies: a resource carries three such ids — creator, last updater, owner — and
   * {@link ProvenanceNameUtil} walks the same three for every ancestor on its path and for every
   * entry of a listing. One folder read with a three-deep path spent four minutes resolving names
   * that were never going to resolve.
   * <p>
   * Short enough that a user server coming back, or an account being created, shows names again
   * promptly; long enough that a single request cannot pay the timeout more than once per id.
   */
  private static final int UNRESOLVABLE_RETENTION_SECONDS = 60;

  private static Cache<String, Boolean> unresolvableIds;

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
    if (unresolvableIds == null) {
      unresolvableIds =
          CacheBuilder.newBuilder()
              .concurrencyLevel(10)
              .maximumSize(10000)
              .expireAfterWrite(UNRESOLVABLE_RETENTION_SECONDS, TimeUnit.SECONDS)
              .build();
    }
  }

  /**
   * Stores a summary directly in the cache. Integration tests use this to seed their test users,
   * so a lookup never falls through to the loader, which would call the user server.
   */
  public void put(CedarUserSummary userSummary) {
    userSummaryCache.put(userSummary.getId(), userSummary);
    // A summary supplied directly settles the question, so any standing record of the id being
    // unresolvable goes with it. Left in place it would shadow this summary until it expired, since
    // getUser consults it first.
    if (unresolvableIds != null) {
      unresolvableIds.invalidate(userSummary.getId());
    }
  }

  public CedarUserSummary getUser(String id) {
    if (id == null) {
      return null;
    }
    // Asked for again within the retention window, an id already known to be unresolvable answers
    // straight away instead of waiting out another socket timeout.
    if (unresolvableIds != null && unresolvableIds.getIfPresent(id) != null) {
      return null;
    }
    try {
      return userSummaryCache.get(id);
    } catch (CacheLoader.InvalidCacheLoadException e) {
      // The loader returned null: the user service does not know this id, or could not be reached.
      // Every caller already treats a null summary as "no display name available" — see
      // ProvenanceNameUtil — so degrade to that instead of failing the request. Guava reports this
      // case with an unchecked exception, which is why it needs its own catch: without it the
      // exception escaped to the generic mapper and turned every read of a resource whose
      // creator/owner could not be resolved into a 500.
      log.warn("No user summary available for {}; serving without a provenance display name", id);
      rememberUnresolvable(id);
    } catch (UncheckedExecutionException e) {
      log.error("Unchecked error retrieving the user summary for " + id, e);
      rememberUnresolvable(id);
    } catch (ExecutionException e) {
      log.error("Error Retrieving Elements from the CedarUserSummary Cache" + e.getMessage());
      rememberUnresolvable(id);
    }
    return null;
  }

  /**
   * Marks an id as one the user server could not answer for, so the next lookup within
   * {@link #UNRESOLVABLE_RETENTION_SECONDS} returns without calling it again. Every failure is
   * recorded, whatever its cause: an unknown account and an unreachable user server are equally
   * unresolvable for as long as they last, and neither is worth a timeout per lookup.
   */
  private static void rememberUnresolvable(String id) {
    if (unresolvableIds != null) {
      unresolvableIds.put(id, Boolean.TRUE);
    }
  }

  public CacheStats getStats() {
    return userSummaryCache.stats();
  }

  private CedarUserSummary getUserSummary(String id) throws CedarProcessingException {
    CedarRequestContext context = CedarRequestContextFactory.fromAdminUser(cedarConfig, userService);
    String uuid = extractUserUUID(id);
    String url = microserviceUrlUtil.getUser().UuidSummary(uuid);
    ClassicHttpResponse proxyResponse = null;
    try {
      proxyResponse = ProxyUtil.proxyGet(url, context);
      HttpEntity entity = proxyResponse.getEntity();
      if (entity != null) {
        String userSummaryString = EntityUtils.toString(entity, StandardCharsets.UTF_8);
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
    } catch (IOException | ParseException e) {
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
