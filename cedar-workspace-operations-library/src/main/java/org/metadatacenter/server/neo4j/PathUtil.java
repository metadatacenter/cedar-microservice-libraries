package org.metadatacenter.server.neo4j;

public interface PathUtil {
  String getSeparator();

  String getRootPath();

  String getChildPath(String parent, String name);

  String normalizePath(String path);

  String sanitizeName(String name);

  String extractName(String path);

  int getPathDepth(String path);
}
