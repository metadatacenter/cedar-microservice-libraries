package org.metadatacenter.server.neo4j.proxy;

import org.apache.commons.lang.StringUtils;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.server.neo4j.PathUtil;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("IndexOfReplaceableByContains")
public class Neo4JPathUtil implements PathUtil {

  private static final String SEPARATOR = "/";
  private static final String FOLDER_NAME_SANITIZER_REGEX = "[^a-zA-Z0-9.\\-_' ]";
  private static final Pattern FOLDER_NAME_SANITIZER_PATTERN = Pattern.compile(FOLDER_NAME_SANITIZER_REGEX);

  private final String rootPath;

  Neo4JPathUtil(CedarConfig cedarConfig) {
    rootPath = cedarConfig.getFolderStructureConfig().getRootFolder().getPath();
  }

  private boolean isRoot(String path) {
    String normalizedPath = normalizePath(path);
    return rootPath.equals(normalizedPath);
  }

  @Override
  public String getSeparator() {
    return SEPARATOR;
  }

  @Override
  public String getRootPath() {
    return rootPath;
  }

  @Override
  public String getChildPath(String parent, String name) {
    return parent + SEPARATOR + name;
  }

  @Override
  public String normalizePath(String path) {
    if (path != null) {
      if (!rootPath.equals(path)) {
        while (path.endsWith(SEPARATOR)) {
          path = path.substring(0, path.length() - 1);
        }
        if (!path.startsWith(rootPath)) {
          path = rootPath + path;
        }
        String ds = SEPARATOR + SEPARATOR;
        while (path.contains(ds)) {
          path = path.replace(ds, SEPARATOR);
        }
      }
    }
    return path;
  }

  @Override
  public String sanitizeName(String name) {
    Matcher matcher = FOLDER_NAME_SANITIZER_PATTERN.matcher(name);
    return matcher.replaceAll("_");
  }

  @Override
  public String extractName(String path) {
    if (path != null) {
      if (isRoot(path)) {
        return path;
      } else {
        int i = path.lastIndexOf(SEPARATOR);
        if (i > -1) {
          return path.substring(i + SEPARATOR.length());
        }
      }
    }
    return null;
  }

  @Override
  public int getPathDepth(String path) {
    String normalizedPath = normalizePath(path);
    return isRoot(normalizedPath) ? 1 : StringUtils.countMatches(normalizedPath, SEPARATOR) + 1;
  }

}
