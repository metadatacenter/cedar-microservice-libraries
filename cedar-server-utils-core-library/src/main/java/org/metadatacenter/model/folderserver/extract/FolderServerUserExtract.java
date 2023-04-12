package org.metadatacenter.model.folderserver.extract;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.metadatacenter.id.CedarUserId;
import org.metadatacenter.model.AbstractCedarResourceWithDates;
import org.metadatacenter.model.folderserver.basic.FolderServerUser;
import org.metadatacenter.server.neo4j.cypher.NodeProperty;
import org.metadatacenter.server.security.model.user.CedarUserRepresentation;
import org.metadatacenter.util.json.JsonMapper;

import java.io.IOException;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FolderServerUserExtract extends AbstractCedarResourceWithDates implements CedarUserRepresentation {

  private String firstName;
  private String lastName;
  private String email;

  private FolderServerUserExtract() {
  }

  public static FolderServerUserExtract fromFolderServerUser(FolderServerUser user) {
    try {
      String s = JsonMapper.MAPPER.writeValueAsString(user);
      FolderServerUserExtract extract = JsonMapper.MAPPER.readValue(s, FolderServerUserExtract.class);
      return extract;
    } catch (IOException e) {
      e.printStackTrace();
    }
    return null;
  }

  @Override
  @JsonIgnore
  public CedarUserId getResourceId() {
    return CedarUserId.build(getId());
  }

  @JsonProperty(NodeProperty.Label.FIRST_NAME)
  public String getFirstName() {
    return firstName;
  }

  @JsonProperty(NodeProperty.Label.FIRST_NAME)
  public void setFirstName(String firstName) {
    this.firstName = firstName;
  }

  @JsonProperty(NodeProperty.Label.LAST_NAME)
  public String getLastName() {
    return lastName;
  }

  @JsonProperty(NodeProperty.Label.LAST_NAME)
  public void setLastName(String lastName) {
    this.lastName = lastName;
  }

  @Override
  @JsonProperty(NodeProperty.Label.EMAIL)
  public String getEmail() {
    return email;
  }

  @JsonProperty(NodeProperty.Label.EMAIL)
  public void setEmail(String email) {
    this.email = email;
  }
  
}
