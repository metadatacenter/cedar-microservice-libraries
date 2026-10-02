package org.metadatacenter.server.valuerecommender;

import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.config.WorkerValuerecommenderConfig;
import org.metadatacenter.id.CedarTemplateId;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.metadatacenter.server.service.UserService;
import org.metadatacenter.server.url.MicroserviceUrlUtil;
import org.metadatacenter.server.valuerecommender.model.RulesGenerationStatus;
import org.metadatacenter.server.valuerecommender.model.ValuerecommenderReindexMessage;
import org.metadatacenter.util.http.HttpTimeouts;
import org.metadatacenter.util.json.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static org.metadatacenter.constant.HttpConstants.HTTP_HEADER_AUTHORIZATION;

public class ValuerecommenderReindexExecutorService {

  private static final Logger log = LoggerFactory.getLogger(ValuerecommenderReindexExecutorService.class);

  private final CedarConfig cedarConfig;
  private final WorkerValuerecommenderConfig valuerecommenderConfig;
  private final ValuerecommenderReindexQueueService valuerecommenderQueueService;
  private final MicroserviceUrlUtil microserviceUrlUtil;
  private CedarUser adminUser;

  public ValuerecommenderReindexExecutorService(CedarConfig cedarConfig, ValuerecommenderReindexQueueService valuerecommenderQueueService) {
    this.cedarConfig = cedarConfig;
    this.valuerecommenderConfig = cedarConfig.getWorkerConfig().getValuerecommender();
    this.valuerecommenderQueueService = valuerecommenderQueueService;
    this.microserviceUrlUtil = cedarConfig.getMicroserviceUrlUtil();
  }

  public void init(UserService userService) {
    String adminUserApiKey = cedarConfig.getAdminUserConfig().getApiKey();
    try {
      adminUser = userService.findUserByApiKey(adminUserApiKey);
    } catch (Exception e) {
      // Never log the API key itself; log the failure with the exception instead.
      log.error("Error while loading the admin user by its configured API key", e);
    }
    if (adminUser == null) {
      log.error("Admin user not found by the configured API key; valuerecommender reindex will not be possible.");
    }
  }

  public void handleMessages(List<ValuerecommenderReindexMessage> messageList) {
    log.debug("Working on a list of valuerecommender messages...");
    Map<CedarTemplateId, List<ValuerecommenderReindexMessage>> uniqueTemplateIdMap = new LinkedHashMap<>();
    log.debug("Generating unique templateId list...");
    for (ValuerecommenderReindexMessage message : messageList) {
      CedarTemplateId templateId = message.getTemplateId();
      if (!uniqueTemplateIdMap.containsKey(templateId)) {
        uniqueTemplateIdMap.put(templateId, new ArrayList<>());
      }
      uniqueTemplateIdMap.get(templateId).add(message);
    }
    log.debug("Iterating over final unique templateId list...");
    Iterator<CedarTemplateId> iterator = uniqueTemplateIdMap.keySet().iterator();
    while (iterator.hasNext()) {
      CedarTemplateId templateId = iterator.next();
      log.debug("Reading currently processing threads...");
      Set<String> processingIds = getCurrentlyProcessingIds();
      if (processingIds.size() >= valuerecommenderConfig.getMaxReindexingThreadCount()) {
        log.debug("Too many currently processing threads (" + processingIds.size() + " vs " +
            valuerecommenderConfig.getMaxReindexingThreadCount());
        addBackMessages(uniqueTemplateIdMap.get(templateId));
        pause(valuerecommenderConfig.getSleepMillisAfterTooManyProcessing());
        continue;
      }
      if (processingIds.contains(templateId.getId())) {
        log.debug("TemplateId currently reindexing:" + templateId);
        addBackMessages(uniqueTemplateIdMap.get(templateId));
        pause(valuerecommenderConfig.getSleepMillisAfterCurrentIdProcessing());
      } else {
        log.debug("Will start reindexing templateId:" + templateId.getId());
        launchReindex(templateId);
      }
      iterator.remove();
    }
  }

  private Set<String> getCurrentlyProcessingIds() {
    String url = microserviceUrlUtil.getValuerecommender().getCommandGenerateRulesStatus();
    String authString = adminUser.getFirstApiKeyAuthHeader();
    log.debug(url);
    Set<String> idSet = new HashSet<>();
    try {
      Request request = Request.get(url)
          .addHeader(HTTP_HEADER_AUTHORIZATION, authString);
      try (ClassicHttpResponse response = HttpTimeouts.BATCH.execute(request)) {
        int statusCode = response.getCode();
        if (statusCode != HttpStatus.SC_OK) {
          throw new IllegalStateException("Rules-generation status returned HTTP " + statusCode);
        }
        List<RulesGenerationStatus> list = JsonMapper.STRICT_MAPPER
            .readValue(response.getEntity().getContent(), new TypeReference<List<RulesGenerationStatus>>() {
            });
        if (list == null) throw new IllegalStateException("Rules-generation status was null");
        for (RulesGenerationStatus status : list) {
          if (status == null || status.getStatus() == null || status.getTemplateId() == null
              || status.getTemplateId().isBlank()) {
            throw new IllegalStateException("Rules-generation status contained an incomplete entry");
          }
          if (status.getStatus() == RulesGenerationStatus.Status.PROCESSING) {
            idSet.add(status.getTemplateId());
          }
        }
        log.info("Currently executing reindexes:" + idSet);
      }
    } catch (Exception e) {
      // Unknown capacity is not an idle server. Let the claim/acknowledge consumer retain
      // the batch for retry instead of launching blindly and acknowledging lost work.
      throw new IllegalStateException("Could not read rules-generation status", e);
    }

    return idSet;
  }

  private void addBackMessages(List<ValuerecommenderReindexMessage> messages) {
    log.debug("Adding back reindex messages");
    for (ValuerecommenderReindexMessage message : messages) {
      if (!valuerecommenderQueueService.enqueueEventWithResult(message)) {
        throw new IllegalStateException("Could not defer a rules-generation update");
      }
    }
  }

  private static void pause(int millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while deferring rules generation", e);
    }
  }

  private void launchReindex(CedarTemplateId templateId) {
    String url = microserviceUrlUtil.getValuerecommender().getCommandGenerateRules(templateId);
    String authString = adminUser.getFirstApiKeyAuthHeader();
    log.debug(url);
    try {
      Request request = Request.post(url)
          .addHeader(HTTP_HEADER_AUTHORIZATION, authString);
      try (ClassicHttpResponse response = HttpTimeouts.BATCH.execute(request)) {
        int statusCode = response.getCode();
        if (statusCode != HttpStatus.SC_OK) {
          throw new IllegalStateException("Rule generation returned HTTP " + statusCode);
        }
        log.info("The rule regeneration was successfully requested.");
      }
    } catch (Exception e) {
      throw new IllegalStateException("Could not request rule generation", e);
    }
  }
}
