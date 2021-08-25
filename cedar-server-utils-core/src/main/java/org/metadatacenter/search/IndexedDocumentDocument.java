package org.metadatacenter.search;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.metadatacenter.model.folderserver.info.FolderServerNodeInfo;
import org.metadatacenter.model.folderserver.extract.FolderServerResourceExtract;
import org.metadatacenter.server.security.model.auth.NodeSharePermission;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class IndexedDocumentDocument {

  protected String cid;

  protected String summaryText;

  protected FolderServerNodeInfo info;

  protected List<InfoField> infoFields;

  protected PossibleValues possibleValues;

  protected NodeSharePermission computedEverybodyPermission;

  protected List<String> users;

  protected List<String> categories;

  public IndexedDocumentDocument() { }

  public IndexedDocumentDocument(String cid) {
    this.cid = cid;
  }

  public String getCid() {
    return cid;
  }

  public String getSummaryText() {
    return summaryText;
  }

  public void setSummaryText(String summaryText) {
    this.summaryText = summaryText;
  }

  public FolderServerNodeInfo getInfo() {
    return info;
  }

  @JsonIgnore
  public FolderServerResourceExtract getInfoExtract() {
    return FolderServerResourceExtract.fromNodeInfo(info);
  }

  public void setInfo(FolderServerNodeInfo info) {
    this.info = info;
  }

  public List<InfoField> getInfoFields() { return infoFields; }

  public void setInfoFields(List<InfoField> infoFields) { this.infoFields = infoFields; }

  public void setPossibleValues(PossibleValues possibleValues) { this.possibleValues = possibleValues; }

  public PossibleValues getPossibleValues() { return this.possibleValues; }

  public NodeSharePermission getComputedEverybodyPermission() {
    return computedEverybodyPermission;
  }

  public void setComputedEverybodyPermission(NodeSharePermission computedEverybodyPermission) {
    this.computedEverybodyPermission = computedEverybodyPermission;
  }

  public List<String> getUsers() {
    return users;
  }

  public List<String> getCategories() {
    return categories;
  }
}
