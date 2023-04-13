package org.metadatacenter.util.provenance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.metadatacenter.constant.CedarConstants;
import org.metadatacenter.model.CreateOrUpdate;
import org.metadatacenter.server.model.provenance.ProvenanceInfo;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

public class ProvenanceUtil {

  public static final String PAV_CREATED_ON = "pav:createdOn";
  public static final String PAV_CREATED_BY = "pav:createdBy";
  public static final String PAV_LAST_UPDATED_ON = "pav:lastUpdatedOn";
  public static final String OSLC_MODIFIED_BY = "oslc:modifiedBy";

  private static final Logger log = LoggerFactory.getLogger(ProvenanceUtil.class);

  public ProvenanceUtil() {
  }

  private void setProvenanceInfo(JsonNode node, ProvenanceInfo pi, CreateOrUpdate createOrUpdate) {
    ObjectNode resource = (ObjectNode) node;
    if (createOrUpdate == CreateOrUpdate.CREATE) {
      resource.put(PAV_CREATED_ON, pi.getCreatedOn());
      resource.put(PAV_CREATED_BY, pi.getCreatedBy());
    }
    resource.put(PAV_LAST_UPDATED_ON, pi.getLastUpdatedOn());
    resource.put(OSLC_MODIFIED_BY, pi.getLastUpdatedBy());
  }

  public void addProvenanceInfo(JsonNode node, ProvenanceInfo pi) {
    setProvenanceInfo(node, pi, CreateOrUpdate.CREATE);
  }

  public void patchProvenanceInfo(JsonNode node, ProvenanceInfo pi) {
    setProvenanceInfo(node, pi, CreateOrUpdate.UPDATE);
  }

  private ProvenanceInfo buildFromUserURLId(String userURL) {
    ProvenanceInfo pi = new ProvenanceInfo();
    Instant now = Instant.now();
    String nowString = CedarConstants.xsdDateTimeFormatter.format(now);
    pi.setCreatedOn(nowString);
    pi.setCreatedBy(userURL);
    pi.setLastUpdatedOn(nowString);
    pi.setLastUpdatedBy(userURL);
    return pi;
  }

  public ProvenanceInfo build(CedarUser cu) {
    return buildFromUserURLId(cu.getId());
  }

}
