package org.metadatacenter.server.neo4j.proxy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.commons.codec.digest.DigestUtils;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.id.CedarArtifactId;
import org.metadatacenter.id.CedarResourceId;
import org.metadatacenter.model.CedarResource;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.model.RelationLabel;
import org.metadatacenter.model.folderserver.FolderServerArc;
import org.metadatacenter.model.folderserver.basic.FileSystemResource;
import org.metadatacenter.model.folderserver.basic.FolderServerArtifact;
import org.metadatacenter.model.folderserver.basic.FolderServerFolder;
import org.metadatacenter.model.folderserver.result.ResultTuple;
import org.metadatacenter.server.logging.AppLogger;
import org.metadatacenter.server.logging.filter.LoggingContext;
import org.metadatacenter.server.logging.filter.ThreadLocalRequestIdHolder;
import org.metadatacenter.server.logging.model.AppLogMessage;
import org.metadatacenter.server.logging.model.AppLogParam;
import org.metadatacenter.server.logging.model.AppLogSubType;
import org.metadatacenter.server.logging.model.AppLogType;
import org.metadatacenter.server.neo4j.CypherQuery;
import org.metadatacenter.server.neo4j.CypherQueryLiteral;
import org.metadatacenter.server.neo4j.CypherQueryWithParameters;
import org.metadatacenter.server.neo4j.log.CypherQueryLog;
import org.metadatacenter.server.neo4j.util.Neo4JUtil;
import org.metadatacenter.util.json.JsonMapper;
import org.neo4j.driver.*;
import org.neo4j.driver.exceptions.ClientException;
import org.neo4j.driver.types.Node;
import org.neo4j.driver.types.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class AbstractNeo4JProxy {

  protected final Neo4JProxies proxies;
  protected final CedarConfig cedarConfig;

  protected final Driver driver;

  protected static final Logger log = LoggerFactory.getLogger(AbstractNeo4JProxy.class);

  protected AbstractNeo4JProxy(Neo4JProxies proxies, CedarConfig cedarConfig) {
    this.proxies = proxies;
    this.cedarConfig = cedarConfig;
    driver = GraphDatabase.driver(proxies.config.getUri(), AuthTokens.basic(proxies.config.getUserName(), proxies.config.getUserPassword()));
  }

  private void reportQueryError(ClientException ex, CypherQuery q) {
    log.error("Error executing Cypher query:", ex);
    log.error(q.getOriginalQuery());
    if (q instanceof CypherQueryWithParameters) {
      log.error(((CypherQueryWithParameters) q).getParameterMap().toString());
    }
    log.error(q.getRunnableQuery());
    throw new RuntimeException("Error executing Cypher query:" + ex.getMessage());
  }

  protected boolean executeWrite(CypherQuery q, String eventDescription) {
    boolean result = false;
    CypherQueryLog queryLog = null;
    try (Session session = driver.session()) {
      if (q instanceof CypherQueryWithParameters) {
        CypherQueryWithParameters qp = (CypherQueryWithParameters) q;
        final String runnableQuery = qp.getRunnableQuery();
        final Map<String, Object> parameterMap = qp.getParameterMap();
        queryLog = prepareQueryLog("write", qp);
        result = session.writeTransaction(tx -> {
          tx.run(runnableQuery, parameterMap);
          return true;
        });
      } else if (q instanceof CypherQueryLiteral) {
        final String runnableQuery = q.getRunnableQuery();
        queryLog = prepareQueryLog("write", q);
        result = session.writeTransaction(tx -> {
          tx.run(runnableQuery);
          return true;
        });
      }
    } catch (ClientException ex) {
      log.error("Error while " + eventDescription, ex);
      reportQueryError(ex, q);
    } finally {
      if (queryLog != null) {
        commitQueryLog(queryLog);
      }
    }
    return result;
  }

  private CypherQueryLog prepareQueryLog(String operation, CypherQueryWithParameters qp) {
    CypherQueryLog log = new CypherQueryLog(operation,
        qp.getOriginalQuery(),
        qp.getRunnableQuery(),
        qp.getParameterMap(),
        qp.getInterpolatedParamsQuery());
    log.setStart(Instant.now());
    return log;
  }

  private CypherQueryLog prepareQueryLog(String operation, CypherQuery q) {
    CypherQueryLog log = new CypherQueryLog(operation,
        q.getOriginalQuery(),
        q.getRunnableQuery(),
        null,
        q.getRunnableQuery());
    log.setStart(Instant.now());
    return log;
  }

  private void commitQueryLog(CypherQueryLog log) {
    log.setEnd(Instant.now());

    String paramMapString = null;
    try {
      paramMapString = JsonMapper.PRETTY_MAPPER.writeValueAsString(log.getParameterMap());
    } catch (JsonProcessingException e) {
      e.printStackTrace();
    }

    LoggingContext loggingContext = ThreadLocalRequestIdHolder.getLoggingContext();
    String globalRequestId = null;
    String localRequestId = null;
    if (loggingContext != null) {
      globalRequestId = loggingContext.getGlobalRequestId();
      localRequestId = loggingContext.getLocalRequestId();
    }

    AppLogMessage appLog =
        AppLogger.message(AppLogType.CYPHER_QUERY, AppLogSubType.FULL, globalRequestId, localRequestId)
            .param(AppLogParam.ORIGINAL_QUERY, log.getOriginalQuery())
            .param(AppLogParam.RUNNABLE_QUERY, log.getRunnableQuery())
            .param(AppLogParam.INTERPOLATED_QUERY, log.getInterpolatedParamsQuery())
            .param(AppLogParam.QUERY_PARAMETERS, log.getParameterMap())
            .param(AppLogParam.RUNNABLE_QUERY_HASH, DigestUtils.md5Hex(log.getRunnableQuery()))
            .param(AppLogParam.QUERY_PARAMETERS_HASH, DigestUtils.md5Hex(paramMapString))
            .param(AppLogParam.OPERATION, log.getOperation());
    appLog.setStartTime(log.getStart());
    appLog.setEndTime(log.getEnd());
    appLog.setDuration(Duration.between(log.getStart(), log.getEnd()));

    StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
    int count = 0;
    for (int i = stackTrace.length - 1; i >= 0; i--) {
      StackTraceElement ste = stackTrace[i];
      if (count == 0 && (ste.getClassName().contains("Neo4JUser") || ste.getClassName().contains("Neo4JProxy"))) {
        appLog.param(AppLogParam.CLASS_NAME, ste.getClassName());
        appLog.param(AppLogParam.METHOD_NAME, ste.getMethodName());
        appLog.param(AppLogParam.LINE_NUMBER, ste.getLineNumber());
        count++;
      }
    }
    appLog.enqueue();
  }

  protected <T extends CedarResource> T executeWriteGetOne(CypherQuery q, Class<T> type) {
    org.neo4j.driver.Record record = null;
    CypherQueryLog queryLog = null;
    try (Session session = driver.session()) {
      if (q instanceof CypherQueryWithParameters) {
        CypherQueryWithParameters qp = (CypherQueryWithParameters) q;
        final String runnableQuery = qp.getRunnableQuery();
        final Map<String, Object> parameterMap = qp.getParameterMap();
        queryLog = prepareQueryLog("writeGetOne", qp);
        record = session.writeTransaction(tx -> {
          Result result = tx.run(runnableQuery, parameterMap);
          return result.hasNext() ? result.next() : null;
        });
      } else if (q instanceof CypherQueryLiteral) {
        final String runnableQuery = q.getRunnableQuery();
        queryLog = prepareQueryLog("writeGetOne", q);
        record = session.writeTransaction(tx -> {
          Result result = tx.run(runnableQuery);
          return result.hasNext() ? result.next() : null;
        });
      }
    } catch (ClientException ex) {
      reportQueryError(ex, q);
    } finally {
      if (queryLog != null) {
        commitQueryLog(queryLog);
      }
    }

    return extractClassFromRecord(record, type);
  }

  private <T extends CedarResource> T extractClassFromRecord(org.neo4j.driver.Record record, Class<T> type) {
    if (record != null) {
      Node n = record.get(0).asNode();
      if (n != null) {
        JsonNode node = JsonMapper.MAPPER.valueToTree(n.asMap());
        return buildClass(node, type);
      }
    }
    return null;
  }

  private org.neo4j.driver.Record executeQueryGetRecord(Session session, CypherQuery q) {
    org.neo4j.driver.Record record = null;
    CypherQueryLog queryLog = null;
    if (q instanceof CypherQueryWithParameters) {
      CypherQueryWithParameters qp = (CypherQueryWithParameters) q;
      final String runnableQuery = qp.getRunnableQuery();
      final Map<String, Object> parameterMap = qp.getParameterMap();
      queryLog = prepareQueryLog("getRecord", qp);
      record = session.readTransaction(tx -> {
        Result result = tx.run(runnableQuery, parameterMap);
        return result.hasNext() ? result.next() : null;
      });
    } else if (q instanceof CypherQueryLiteral) {
      final String runnableQuery = q.getRunnableQuery();
      queryLog = prepareQueryLog("getRecord", q);
      record = session.readTransaction(tx -> {
        Result result = tx.run(runnableQuery);
        return result.hasNext() ? result.next() : null;
      });
    }
    if (queryLog != null) {
      commitQueryLog(queryLog);
    }
    return record;
  }

  protected long executeReadGetLong(CypherQuery q) {
    try (Session session = driver.session()) {
      org.neo4j.driver.Record record = executeQueryGetRecord(session, q);
      if (record != null) {
        Value value = record.get(0);
        value.type().name();
        if (value.type().equals(driver.defaultTypeSystem().INTEGER())) {
          return value.asLong();
        }
      }
    } catch (ClientException ex) {
      reportQueryError(ex, q);
    }

    return -1;
  }

  protected String executeReadGetString(CypherQuery q) {
    try (Session session = driver.session()) {
      org.neo4j.driver.Record record = executeQueryGetRecord(session, q);
      if (record != null) {
        Value value = record.get(0);
        if (value.type().equals(driver.defaultTypeSystem().STRING())) {
          return value.asString();
        }
      }
    } catch (ClientException ex) {
      reportQueryError(ex, q);
    }

    return null;
  }

  protected boolean executeReadGetBoolean(CypherQuery q) {
    try (Session session = driver.session()) {
      org.neo4j.driver.Record record = executeQueryGetRecord(session, q);
      if (record != null) {
        Value value = record.get(0);
        if (value.type().equals(driver.defaultTypeSystem().BOOLEAN())) {
          return value.asBoolean();
        }
      }
    } catch (ClientException ex) {
      reportQueryError(ex, q);
    }

    return false;
  }

  protected <T extends CedarResource> T executeReadGetOne(CypherQuery q, Class<T> type) {
    try (Session session = driver.session()) {
      org.neo4j.driver.Record record = executeQueryGetRecord(session, q);
      return extractClassFromRecord(record, type);
    } catch (ClientException ex) {
      reportQueryError(ex, q);
    }
    return null;
  }

  private List<org.neo4j.driver.Record> executeQueryGetRecordList(Session session, CypherQuery q) {
    List<org.neo4j.driver.Record> records = null;
    CypherQueryLog queryLog = null;
    if (q instanceof CypherQueryWithParameters) {
      CypherQueryWithParameters qp = (CypherQueryWithParameters) q;
      final String runnableQuery = qp.getRunnableQuery();
      final Map<String, Object> parameterMap = qp.getParameterMap();
      queryLog = prepareQueryLog("getRecordList", qp);
      records = session.readTransaction(tx -> {
        Result result = tx.run(runnableQuery, parameterMap);
        List<org.neo4j.driver.Record> nodes = new ArrayList<>();
        while (result.hasNext()) {
          nodes.add(result.next());
        }
        return nodes;
      });
    } else if (q instanceof CypherQueryLiteral) {
      final String runnableQuery = q.getRunnableQuery();
      queryLog = prepareQueryLog("getRecordList", q);
      records = session.readTransaction(tx -> {
        Result result = tx.run(runnableQuery);
        List<org.neo4j.driver.Record> nodes = new ArrayList<>();
        while (result.hasNext()) {
          nodes.add(result.next());
        }
        return nodes;
      });
    }
    if (queryLog != null) {
      commitQueryLog(queryLog);
    }
    return records;
  }


  protected <T extends CedarResource> List<T> executeReadGetList(CypherQuery q, Class<T> type) {
    List<T> folderServerNodeList = new ArrayList<>();
    try (Session session = driver.session()) {
      List<org.neo4j.driver.Record> records = executeQueryGetRecordList(session, q);
      if (records != null) {
        for (org.neo4j.driver.Record r : records) {
          if (r.size() == 1) {
            Value value = r.get(0);
            if (value.type().equals(driver.defaultTypeSystem().NODE())) {
              Node n = value.asNode();
              if (n != null) {
                JsonNode node = JsonMapper.MAPPER.valueToTree(n.asMap());
                T folderServerNode = buildClass(node, type);
                folderServerNodeList.add(folderServerNode);
              }
            } else if (value.type().equals(driver.defaultTypeSystem().PATH())) {
              Path segments = value.asPath();
              for (Node n : segments.nodes()) {
                JsonNode node = JsonMapper.MAPPER.valueToTree(n.asMap());
                T folderServerNode = buildClass(node, type);
                folderServerNodeList.add(folderServerNode);
              }
            } else if (value.type().equals(driver.defaultTypeSystem().LIST())) {
              List<Object> list = value.asList();
              for (Object o : list) {
                if (o instanceof Node) {
                  Node n = (Node) o;
                  JsonNode node = JsonMapper.MAPPER.valueToTree(n.asMap());
                  T folderServerNode = buildClass(node, type);
                  folderServerNodeList.add(folderServerNode);
                }
              }
            }
          } else {
            for (Value value : r.values()) {
              if (value.type().equals(driver.defaultTypeSystem().NODE())) {
                Node n = value.asNode();
                if (n != null) {
                  JsonNode node = JsonMapper.MAPPER.valueToTree(n.asMap());
                  T folderServerNode = buildClass(node, type);
                  folderServerNodeList.add(folderServerNode);
                }
              }
            }
          }
        }
        return folderServerNodeList;
      }
    } catch (ClientException ex) {
      reportQueryError(ex, q);
    }

    return folderServerNodeList;
  }

  protected <T extends CedarResourceId> List<T> executeReadGetIdList(CypherQuery q, Class<T> type) {
    List<T> folderServerIdList = new ArrayList<>();
    try (Session session = driver.session()) {
      List<org.neo4j.driver.Record> records = executeQueryGetRecordList(session, q);
      if (records != null) {
        for (org.neo4j.driver.Record r : records) {
          if (r.size() == 1) {
            Value value = r.get(0);
            if (value.type().equals(driver.defaultTypeSystem().STRING())) {
              String sv = value.asString();
              T folderServerId = buildIdClass(sv, type);
              folderServerIdList.add(folderServerId);
            }
          }
        }
        return folderServerIdList;
      }
    } catch (ClientException ex) {
      reportQueryError(ex, q);
    }

    return folderServerIdList;
  }

  protected <T extends ResultTuple> List<T> executeReadGetToupleList(CypherQuery q, Class<T> type) {
    List<T> tupleList = new ArrayList<>();
    try (Session session = driver.session()) {
      List<org.neo4j.driver.Record> records = executeQueryGetRecordList(session, q);
      if (records != null) {
        for (org.neo4j.driver.Record r : records) {
          Map m = r.asMap();
          JsonNode node = JsonMapper.MAPPER.valueToTree(m);
          T tuple = buildToupleClass(node, type);
          tupleList.add(tuple);
        }
        return tupleList;
      }
    } catch (ClientException ex) {
      reportQueryError(ex, q);
    }

    return tupleList;
  }

  protected List<FolderServerArc> executeReadGetArcList(CypherQuery q) {
    List<FolderServerArc> folderServerArcList = new ArrayList<>();
    try (Session session = driver.session()) {
      List<org.neo4j.driver.Record> records = executeQueryGetRecordList(session, q);
      if (records != null) {
        for (org.neo4j.driver.Record r : records) {
          Map<String, Object> recordMap = r.asMap();
          if (recordMap != null) {
            JsonNode node = JsonMapper.MAPPER.valueToTree(recordMap);
            FolderServerArc rel = buildArc(node);
            folderServerArcList.add(rel);
          }
        }
        return folderServerArcList;
      }
    } catch (ClientException ex) {
      reportQueryError(ex, q);
    }

    return folderServerArcList;
  }

  protected List<Map<String, Object>> executeReadGetMapList(CypherQuery q, List<String> fieldNameList) {
    Map<String, String> fieldNameMap = new HashMap<>();
    if (fieldNameList != null) {
      for (String fieldName : fieldNameList) {
        fieldNameMap.put(Neo4JUtil.escapePropertyName(fieldName), fieldName);
      }
    }
    fieldNameMap.put(Neo4JUtil.escapePropertyName("@id"), "@id");
    fieldNameMap.put(Neo4JUtil.escapePropertyName("resourceType"), "resourceType");

    List<Map<String, Object>> folderServerNodeList = new ArrayList<>();
    try (Session session = driver.session()) {
      List<org.neo4j.driver.Record> records = executeQueryGetRecordList(session, q);
      if (records != null) {
        for (org.neo4j.driver.Record r : records) {
          if (r.size() == 1) {
            Value value = r.get(0);
            if (value.type().equals(driver.defaultTypeSystem().NODE())) {
              Node n = value.asNode();
              if (n != null) {
                Map<String, Object> m = convertToMap(n, fieldNameMap);
                folderServerNodeList.add(m);
              }
            }
          } else {
            for (Value value : r.values()) {
              if (value.type().equals(driver.defaultTypeSystem().NODE())) {
                Node n = value.asNode();
                if (n != null) {
                  Map<String, Object> m = convertToMap(n, fieldNameMap);
                  folderServerNodeList.add(m);
                }
              }
            }
          }
        }
        return folderServerNodeList;
      }
    } catch (ClientException ex) {
      reportQueryError(ex, q);
    }

    return folderServerNodeList;
  }

  private Map<String, Object> convertToMap(Node n, Map<String, String> fieldNameMap) {
    Map<String, Object> m = n.asMap();
    Map<String, Object> filtered = new HashMap<>();
    for (String escapedName : fieldNameMap.keySet()) {
      String regularName = fieldNameMap.get(escapedName);
      if (m.containsKey(escapedName)) {
        filtered.put(regularName, m.get(escapedName));
      }
    }
    return filtered;
  }

  private <T extends CedarResource> T buildClass(JsonNode node, Class<T> type) {
    T cn = null;
    if (node != null && !node.isMissingNode()) {
      try {
        JsonNode unescaped = Neo4JUtil.unescapeTopLevelPropertyNames(node);
        cn = JsonMapper.MAPPER.treeToValue(unescaped, type);
      } catch (JsonProcessingException e) {
        log.error("Error deserializing resource into " + type.getSimpleName(), e);
      }
    }
    return cn;
  }

  private <T extends CedarResourceId> T buildIdClass(String idValue, Class<T> type) {
    T cn = (T) CedarResourceId.build(idValue, CedarResourceType.forResourceIdClass(type));
    return cn;
  }

  private <T extends ResultTuple> T buildToupleClass(JsonNode node, Class<T> type) {
    T cn = null;
    if (node != null && !node.isMissingNode()) {
      try {
        JsonNode unescaped = Neo4JUtil.unescapeTopLevelPropertyNames(node);
        cn = JsonMapper.MAPPER.treeToValue(unescaped, type);
      } catch (JsonProcessingException e) {
        log.error("Error deserializing touple into " + type.getSimpleName(), e);
      }
    }
    return cn;
  }

  protected FolderServerFolder buildFolder(JsonNode f) {
    return buildClass(f, FolderServerFolder.class);
  }

  public FolderServerArtifact buildResource(JsonNode r) {
    return buildClass(r, FolderServerArtifact.class);
  }

  protected FileSystemResource buildNode(JsonNode n) {
    return buildClass(n, FileSystemResource.class);
  }

  protected FolderServerArc buildArc(JsonNode a) {
    FolderServerArc arc = null;
    if (a != null && !a.isMissingNode()) {
      arc = new FolderServerArc(a.at("/sid").textValue(), RelationLabel.forValue(a.at("/type").textValue()), a.at("/tid").textValue());
    }
    return arc;
  }

}
