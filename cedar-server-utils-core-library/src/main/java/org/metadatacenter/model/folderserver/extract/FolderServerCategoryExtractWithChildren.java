package org.metadatacenter.model.folderserver.extract;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.metadatacenter.model.folderserver.basic.FolderServerCategory;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FolderServerCategoryExtractWithChildren extends FolderServerCategoryExtract {

  protected List<FolderServerCategoryExtractWithChildren> children;

  public FolderServerCategoryExtractWithChildren() {
    super();
    this.children = new ArrayList<>();
  }

  public static FolderServerCategoryExtractWithChildren fromCategory(FolderServerCategory category) {
    FolderServerCategoryExtractWithChildren c = new FolderServerCategoryExtractWithChildren();
    c.setId(category.getId());
    c.setType(category.getType());
    c.setName(category.getName());
    c.setDescription(category.getDescription());
    c.setIdentifier(category.getIdentifier());
    c.setParentCategoryId(category.getParentCategoryId());
    return c;
  }

  public List<FolderServerCategoryExtractWithChildren> getChildren() {
    return this.children;
  }
}
