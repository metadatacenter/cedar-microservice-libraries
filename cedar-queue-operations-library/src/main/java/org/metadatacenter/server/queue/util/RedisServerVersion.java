package org.metadatacenter.server.queue.util;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The version a Redis server reports for itself, as three numbers that can be ordered.
 * <p>
 * A server is reachable long before it can run everything CEDAR issues, and the two are worth
 * separating: an unreachable server fails on connection, whereas a server two minor versions behind
 * accepts the connection and then rejects a command. Comparing versions turns the second case into
 * a statement about the server rather than a command error inside a retry loop.
 */
public record RedisServerVersion(int major, int minor, int patch)
    implements Comparable<RedisServerVersion> {

  private static final Pattern VERSION_LINE = Pattern.compile(
      "^redis_version:(\\d+)\\.(\\d+)\\.(\\d+)", Pattern.MULTILINE);

  /**
   * Reads the version out of the server section of an INFO reply.
   *
   * @return the version, or empty when the reply carries none. Empty means unknown rather than
   * unsupported: some managed and proxied deployments answer INFO without a version, and refusing
   * to run against those would be a guess dressed as a check.
   */
  public static Optional<RedisServerVersion> parse(String info) {
    if (info == null) {
      return Optional.empty();
    }
    Matcher matcher = VERSION_LINE.matcher(info);
    if (!matcher.find()) {
      return Optional.empty();
    }
    return Optional.of(new RedisServerVersion(
        Integer.parseInt(matcher.group(1)),
        Integer.parseInt(matcher.group(2)),
        Integer.parseInt(matcher.group(3))));
  }

  public boolean isAtLeast(RedisServerVersion other) {
    return compareTo(other) >= 0;
  }

  @Override
  public int compareTo(RedisServerVersion other) {
    if (major != other.major) {
      return Integer.compare(major, other.major);
    }
    if (minor != other.minor) {
      return Integer.compare(minor, other.minor);
    }
    return Integer.compare(patch, other.patch);
  }

  @Override
  public String toString() {
    return major + "." + minor + "." + patch;
  }
}
