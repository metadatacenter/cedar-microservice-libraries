package org.metadatacenter.server.queue.util;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reading a server's own version out of its INFO reply, and ordering two of them. The estate
 * spans servers several minor versions apart, and the difference decides whether a command the
 * queues issue exists at all.
 */
class RedisServerVersionTest {

  private static final String INFO = """
      # Server
      redis_version:6.0.16
      redis_git_sha1:00000000
      redis_mode:standalone
      os:Linux 5.15.0-177-generic x86_64
      """;

  @Test
  void theVersionIsReadFromTheServerSection() {
    assertEquals(Optional.of(new RedisServerVersion(6, 0, 16)), RedisServerVersion.parse(INFO));
  }

  /**
   * Some managed and proxied deployments answer INFO without a version. That is not evidence of an
   * unsupported server, so it reads as unknown and leaves the caller to decide.
   */
  @Test
  void aReplyWithoutAVersionReadsAsUnknown() {
    assertEquals(Optional.empty(), RedisServerVersion.parse("# Server\nredis_mode:standalone\n"));
    assertEquals(Optional.empty(), RedisServerVersion.parse(null));
  }

  @Test
  void versionsOrderByMajorThenMinorThenPatch() {
    assertTrue(new RedisServerVersion(6, 0, 16).isAtLeast(new RedisServerVersion(2, 6, 0)));
    assertTrue(new RedisServerVersion(6, 2, 0).isAtLeast(new RedisServerVersion(6, 0, 16)));
    assertTrue(new RedisServerVersion(2, 6, 0).isAtLeast(new RedisServerVersion(2, 6, 0)));
    assertFalse(new RedisServerVersion(6, 0, 16).isAtLeast(new RedisServerVersion(6, 2, 0)));
    assertFalse(new RedisServerVersion(2, 5, 9).isAtLeast(new RedisServerVersion(2, 6, 0)));
  }

  @Test
  void aVersionPrintsAsTheServerReportsIt() {
    assertEquals("6.0.16", new RedisServerVersion(6, 0, 16).toString());
  }
}
