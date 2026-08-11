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
import java.util.List;

public class ProvenanceUtil {

  public static final String PAV_CREATED_ON = "pav:createdOn";
  public static final String PAV_CREATED_BY = "pav:createdBy";
  public static final String PAV_LAST_UPDATED_ON = "pav:lastUpdatedOn";
  public static final String OSLC_MODIFIED_BY = "oslc:modifiedBy";

  /** The four keys this class writes, for callers that need to treat provenance as one unit. */
  public static final List<String> PROVENANCE_KEYS =
      List.of(PAV_CREATED_ON, PAV_CREATED_BY, PAV_LAST_UPDATED_ON, OSLC_MODIFIED_BY);

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

  /**
   * Takes all four provenance values from the stored artifact, for a child this write left untouched. Its
   * dates then continue to describe the last write that changed it, rather than this one, and the request
   * cannot restate a value the stored artifact already records.
   * <p>
   * A value the stored artifact does not record is left as the request supplied it, on the same reasoning
   * as {@link #preserveCreationProvenance}: the point is to protect a recorded value from being rewritten,
   * not to protect an absent one from being filled. Deleting the request's value instead made a child
   * missing its provenance unrepairable, since supplying the key without otherwise changing the child put
   * it on this path and the key was dropped again -- after validation had accepted the document with it.
   */
  public void copyProvenance(JsonNode target, JsonNode stored) {
    if (target == null || stored == null || !target.isObject()) {
      return;
    }
    ObjectNode resource = (ObjectNode) target;
    for (String key : PROVENANCE_KEYS) {
      copyIfPresent(resource, stored, key);
    }
  }

  /**
   * Creation provenance belongs to whoever created the artifact, so an update takes it from what is stored
   * rather than from the request. Trusting the request let any caller with write access claim that another
   * user created the artifact, since an update stamps only the last-modified pair.
   * <p>
   * A value absent from the stored artifact is left as the request supplied it. That keeps a repair able to
   * supply creation provenance an older artifact never had, while denying it the ability to overwrite
   * creation provenance that is already recorded.
   */
  public void preserveCreationProvenance(JsonNode target, JsonNode stored) {
    if (target == null || stored == null || !target.isObject()) {
      return;
    }
    ObjectNode resource = (ObjectNode) target;
    copyIfPresent(resource, stored, PAV_CREATED_ON);
    copyIfPresent(resource, stored, PAV_CREATED_BY);
  }

  private static void copyIfPresent(ObjectNode target, JsonNode stored, String key) {
    JsonNode value = stored.get(key);
    if (value != null && !value.isNull()) {
      target.set(key, value.deepCopy());
    }
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
