package org.metadatacenter.model.folderserver.info;

import org.junit.jupiter.api.Test;
import org.metadatacenter.model.folderserver.basic.FolderServerFolder;
import org.metadatacenter.model.folderserver.extract.FolderServerFolderExtract;
import org.metadatacenter.model.folderserver.extract.FolderServerResourceExtract;
import org.metadatacenter.server.security.model.auth.CurrentUserResourcePermissions;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * What an index document keeps of a resource's path.
 *
 * <p>These pin the property that lets indexing ask for the path without its per-element permission
 * decoration: the decoration never reaches the document. Indexing used to compute, for every
 * resource in the repository, what the user running the rebuild could do with each element of that
 * resource's ancestor chain — several graph queries per element — and {@code fromNode} read one
 * identifier off the result and dropped the rest. If a later change starts reading more of the path
 * here, these tests are what should fail.
 */
class FolderServerNodeInfoTest {

  private static FolderServerFolderExtract folder(String id) {
    FolderServerFolderExtract extract = new FolderServerFolderExtract();
    extract.setId(id);
    return extract;
  }

  private static FolderServerFolder nodeWithPath(List<FolderServerResourceExtract> path) {
    FolderServerFolder node = new FolderServerFolder();
    node.setId("https://repo.metadatacenter.net/folders/the-node");
    node.setName("the node");
    node.setPathInfo(new ArrayList<>(path));
    return node;
  }

  @Test
  void theParentFolderIsTheSecondToLastPathElement() {
    FolderServerFolder node = nodeWithPath(List.of(folder("root"), folder("parent"), folder("the-node")));

    assertEquals("parent", FolderServerNodeInfo.fromNode(node).getParentFolderId());
  }

  @Test
  void aPathWithNoParentLeavesTheParentUnset() {
    FolderServerFolder node = nodeWithPath(List.of(folder("root")));

    assertNull(FolderServerNodeInfo.fromNode(node).getParentFolderId());
  }

  @Test
  void decoratingThePathChangesNothingInTheDocument() {
    List<FolderServerResourceExtract> plain = List.of(folder("root"), folder("parent"), folder("the-node"));

    List<FolderServerResourceExtract> decorated = new ArrayList<>();
    for (FolderServerResourceExtract element : plain) {
      FolderServerFolderExtract copy = folder(element.getId());
      // What getResourcePathExtract adds, and what the index has never kept.
      copy.setCurrentUserPermissions(new CurrentUserResourcePermissions());
      copy.setIsOpenImplicitly(true);
      copy.setActiveUserCanRead(true);
      decorated.add(copy);
    }

    FolderServerNodeInfo fromPlain = FolderServerNodeInfo.fromNode(nodeWithPath(plain));
    FolderServerNodeInfo fromDecorated = FolderServerNodeInfo.fromNode(nodeWithPath(decorated));

    assertEquals(fromPlain.getParentFolderId(), fromDecorated.getParentFolderId());
    assertEquals(fromPlain.getId(), fromDecorated.getId());
    assertEquals(fromPlain.getName(), fromDecorated.getName());
    assertEquals(fromPlain.getType(), fromDecorated.getType());
  }
}
