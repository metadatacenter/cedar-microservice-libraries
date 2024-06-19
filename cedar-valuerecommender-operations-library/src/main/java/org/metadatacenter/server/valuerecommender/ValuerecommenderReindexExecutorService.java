package org.metadatacenter.server.valuerecommender;

import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.fluent.Request;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.config.WorkerValuerecommenderConfig;
import org.metadatacenter.id.CedarTemplateId;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.metadatacenter.server.service.UserService;
import org.metadatacenter.server.url.MicroserviceUrlUtil;
import org.metadatacenter.server.valuerecommender.model.RulesGenerationStatus;
import org.metadatacenter.server.valuerecommender.model.ValuerecommenderReindexMessage;
import org.metadatacenter.util.json.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static org.metadatacenter.constant.HttpConnectionConstants.CONNECTION_TIMEOUT;
import static org.metadatacenter.constant.HttpConnectionConstants.SOCKET_TIMEOUT;
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
      log.error("Error while loading admin user by apiKey:" + adminUserApiKey);
    }
    if (adminUser == null) {
      log.error("Admin user not found by apiKey:" + adminUserApiKey);
      log.error("Valuerecommender reindex will not be possible!");
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
        try {
          Thread.sleep(valuerecommenderConfig.getSleepMillisAfterTooManyProcessing());
        } catch (InterruptedException e) {
          log.error("Error while sleeping", e);
        }
      }
      if (processingIds.contains(templateId)) {
        log.debug("TemplateId currently reindexing:" + templateId);
        addBackMessages(uniqueTemplateIdMap.get(templateId));
        try {
          Thread.sleep(valuerecommenderConfig.getSleepMillisAfterCurrentIdProcessing());
        } catch (InterruptedException e) {
          log.error("Error while sleeping", e);
        }
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
      Request request = Request.Get(url)
          .connectTimeout(CONNECTION_TIMEOUT)
          .socketTimeout(SOCKET_TIMEOUT)
          .addHeader(HTTP_HEADER_AUTHORIZATION, authString);
      HttpResponse response = request.execute().returnResponse();
      int statusCode = response.getStatusLine().getStatusCode();
      if (statusCode == HttpStatus.SC_OK) {
        List<RulesGenerationStatus> list = JsonMapper.MAPPER
            .readValue(response.getEntity().getContent(), new TypeReference<List<RulesGenerationStatus>>() {
            });
        for (RulesGenerationStatus status : list) {
          if (status.getStatus() == RulesGenerationStatus.Status.PROCESSING) {
            idSet.add(status.getTemplateId());
          }
        }
        log.info("Currently executing reindexes:" + idSet);
      } else {
        log.error("Error while requesting reindexing rule set. HTTP status code: " + statusCode);
      }
    } catch (Exception e) {
      log.error("Error while requesting reindexing rule set", e);
    }

    return idSet;
  }

  private void addBackMessages(List<ValuerecommenderReindexMessage> messages) {
    log.debug("Adding back reindex messages");
    for (ValuerecommenderReindexMessage message : messages) {
      valuerecommenderQueueService.enqueueEvent(message);
    }
  }

  private void launchReindex(CedarTemplateId templateId) {
    String url = microserviceUrlUtil.getValuerecommender().getCommandGenerateRules(templateId);
    String authString = adminUser.getFirstApiKeyAuthHeader();
    log.debug(url);
    try {
      Request request = Request.Post(url)
          .connectTimeout(CONNECTION_TIMEOUT)
          .socketTimeout(SOCKET_TIMEOUT)
          .addHeader(HTTP_HEADER_AUTHORIZATION, authString);
      HttpResponse response = request.execute().returnResponse();
      int statusCode = response.getStatusLine().getStatusCode();
      if (statusCode == HttpStatus.SC_OK) {
        log.info("The rule regeneration was successfully requested.");
      } else {
        log.error("Error while requesting rule index regeneration. HTTP status code: " + statusCode);
      }
    } catch (Exception e) {
      log.error("Error while requesting rule regeneration", e);
    }
  }
}
