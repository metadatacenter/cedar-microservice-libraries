package org.metadatacenter.id;

import org.metadatacenter.model.CedarResourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CedarFQResourceId {
  private static final String CEDAR_URL_REGEX = ".*(http.*)://(.*)/(.*)/([0-9a-z-]*)";
  private static final Pattern CEDAR_URL_PATTERN = Pattern.compile(CEDAR_URL_REGEX);

  private static final Logger log = LoggerFactory.getLogger(CedarFQResourceId.class);
  private String protocol;
  private String serverName;
  private CedarResourceType type;
  private String uuid;

  private CedarFQResourceId(String protocol, String serverName, CedarResourceType type, String uuid) {
    this.protocol = protocol;
    this.serverName = serverName;
    this.type = type;
    this.uuid = uuid;
  }

  public static CedarFQResourceId build(String id) {
    Matcher matcher = CEDAR_URL_PATTERN.matcher(id);
    if (matcher.find()) {
      String prefixString = matcher.group(3);
      CedarResourceType candidateType = CedarResourceType.forPrefix(prefixString);
      if (candidateType != null) {
        return new CedarFQResourceId(matcher.group(1), matcher.group(2), candidateType, matcher.group(4));
      }
    }
    return null;
  }

  public String getProtocol() {
    return protocol;
  }

  public String getServerName() {
    return serverName;
  }

  public CedarResourceType getType() {
    return type;
  }

  public String getUuid() {
    return uuid;
  }

  @Override
  public String toString() {
    return protocol + "://" + serverName + "/" + type.getPrefix() + "/" + uuid;
  }
}
