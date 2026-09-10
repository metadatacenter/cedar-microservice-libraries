package org.metadatacenter.util.test;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.id.CedarUserId;
import org.metadatacenter.model.SystemComponent;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.metadatacenter.server.security.model.user.CedarUserApiKey;
import org.metadatacenter.server.security.model.user.CedarUserUIPreferences;
import org.metadatacenter.server.service.UserService;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Named.named;

/**
 * The rules both user services must obey, asserted against each of them.
 *
 * <p>CEDAR has two: the stored one every deployment authenticates through, and the in-memory one
 * nearly every suite installs in its place. A double that is stricter than the thing it stands in
 * for is worse than no double at all, because every suite that uses it passes while production does
 * not. That is not hypothetical here. The in-memory service refused a disabled API key from the day
 * it was written; the stored one authenticated it, and no suite could tell, because the only suites
 * that reach the stored service are the handful that skip the double.
 *
 * <p>The rules below are about credentials and the keys that carry them, which is where the two had
 * drifted. Each runs twice, once against each implementation, from a user registered the way that
 * implementation registers one.
 */
public class UserServiceContractTest {

  private static final int MAX_KEYS = 3;

  /** Registers a user with one implementation and hands back the service holding it. */
  @FunctionalInterface
  private interface Registrar {
    UserService register(CedarUser user);
  }

  @BeforeAll
  public static void oneTimeSetUp() throws Exception {
    EmbeddedCedarNeo4j.startAndRedirectEnvironment(Map.of("CEDAR_REDIS_PERSISTENT_PORT", "1"));
    EmbeddedCedarNeo4j.startRedirectAndSeed(SystemComponent.SERVER_USER);
  }

  private static Stream<Arguments> implementations() {
    Registrar stored = user -> {
      UserService service = CedarDataServices.getInstance().getNeoUserService();
      service.createUser(user);
      return service;
    };
    Registrar inMemory = InMemoryUserService::new;
    return Stream.of(
        Arguments.of(named("the stored user service", stored)),
        Arguments.of(named("the in-memory user service", inMemory)));
  }

  @ParameterizedTest
  @MethodSource("implementations")
  public void anEnabledKeyResolvesToItsUser(Registrar registrar) {
    CedarUser user = userWith(key("live", true));
    UserService service = registrar.register(user);

    CedarUser found = service.findUserByApiKey(valueOf(user, "live"));

    Assertions.assertNotNull(found, "an enabled key must resolve to its user");
    Assertions.assertEquals(user.getId(), found.getId());
  }

  @ParameterizedTest
  @MethodSource("implementations")
  public void aDisabledKeyResolvesToNobody(Registrar registrar) {
    CedarUser user = userWith(key("live", true), key("withdrawn", false));
    UserService service = registrar.register(user);

    Assertions.assertNotNull(service.findUserByApiKey(valueOf(user, "live")));
    Assertions.assertNull(service.findUserByApiKey(valueOf(user, "withdrawn")),
        "a disabled key must authenticate nobody");
  }

  @ParameterizedTest
  @MethodSource("implementations")
  public void aKeyNobodyHoldsResolvesToNobody(Registrar registrar) {
    CedarUser user = userWith(key("live", true));
    UserService service = registrar.register(user);

    Assertions.assertNull(service.findUserByApiKey("not-a-key-" + UUID.randomUUID()));
    Assertions.assertNull(service.findUserByApiKey(null));
  }

  @ParameterizedTest
  @MethodSource("implementations")
  public void aKeyIsUsableAsSoonAsItIsAdded(Registrar registrar) {
    CedarUser user = userWith(key("live", true));
    UserService service = registrar.register(user);
    CedarUserApiKey added = key("second", true);

    Assertions.assertFalse(service.addApiKey(user.getResourceId(), added, MAX_KEYS).isError());

    Assertions.assertNotNull(service.findUserByApiKey(added.getKey()));
  }

  @ParameterizedTest
  @MethodSource("implementations")
  public void aDeletedKeyStopsResolving(Registrar registrar) {
    CedarUser user = userWith(key("live", true), key("spare", true));
    UserService service = registrar.register(user);
    String spare = valueOf(user, "spare");

    Assertions.assertFalse(service.deleteApiKey(user.getResourceId(), idOf(user, "spare")).isError());

    Assertions.assertNull(service.findUserByApiKey(spare), "a deleted key must authenticate nobody");
    Assertions.assertNotNull(service.findUserByApiKey(valueOf(user, "live")));
  }

  @ParameterizedTest
  @MethodSource("implementations")
  public void regeneratingAKeyRetiresItsOldValue(Registrar registrar) {
    CedarUser user = userWith(key("live", true), key("rotated", true));
    UserService service = registrar.register(user);
    String before = valueOf(user, "rotated");
    String after = "rotated-" + UUID.randomUUID();

    Assertions.assertFalse(service.regenerateApiKey(user.getResourceId(), idOf(user, "rotated"), after,
        OffsetDateTime.now()).isError());

    Assertions.assertNull(service.findUserByApiKey(before), "the rotated value must stop working");
    Assertions.assertNotNull(service.findUserByApiKey(after));
  }

  /**
   * A disabled key is not the account's working key, so it cannot stand in for the one the account
   * is required to keep.
   */
  @ParameterizedTest
  @MethodSource("implementations")
  public void theLastEnabledKeyCanNotBeDeletedBehindADisabledOne(Registrar registrar) {
    CedarUser user = userWith(key("live", true), key("withdrawn", false));
    UserService service = registrar.register(user);

    Assertions.assertTrue(service.deleteApiKey(user.getResourceId(), idOf(user, "live")).isError(),
        "the account's only enabled key must not be removable");
    Assertions.assertNotNull(service.findUserByApiKey(valueOf(user, "live")));

    Assertions.assertFalse(service.deleteApiKey(user.getResourceId(), idOf(user, "withdrawn")).isError(),
        "a disabled key is removable, since it is not the key the account works with");
  }

  @ParameterizedTest
  @MethodSource("implementations")
  public void aKeyBeyondTheMaximumIsRefused(Registrar registrar) {
    CedarUser user = userWith(key("live", true));
    UserService service = registrar.register(user);
    for (int i = 1; i < MAX_KEYS; i++) {
      Assertions.assertFalse(service.addApiKey(user.getResourceId(), key("extra" + i, true), MAX_KEYS).isError());
    }

    CedarUserApiKey refused = key("one too many", true);
    Assertions.assertTrue(service.addApiKey(user.getResourceId(), refused, MAX_KEYS).isError());

    Assertions.assertNull(service.findUserByApiKey(refused.getKey()), "a refused key must not authenticate");
  }

  @ParameterizedTest
  @MethodSource("implementations")
  public void aMissingKeyIsReportedRatherThanRemovingAnother(Registrar registrar) {
    CedarUser user = userWith(key("live", true));
    UserService service = registrar.register(user);

    Assertions.assertTrue(service.deleteApiKey(user.getResourceId(), UUID.randomUUID().toString()).isError());
    Assertions.assertTrue(service.regenerateApiKey(user.getResourceId(), UUID.randomUUID().toString(),
        "unused", OffsetDateTime.now()).isError());

    Assertions.assertNotNull(service.findUserByApiKey(valueOf(user, "live")));
  }

  /** A user each test owns outright, so nothing it does to its keys reaches another test. */
  private static CedarUser userWith(CedarUserApiKey... keys) {
    CedarUser user = new CedarUser();
    user.setId("https://metadatacenter.org/users/" + UUID.randomUUID());
    user.setFirstName("Contract");
    user.setLastName("Subject");
    user.setEmail("contract-" + UUID.randomUUID() + "@test.com");
    user.setUiPreferences(new CedarUserUIPreferences());
    for (CedarUserApiKey key : keys) {
      user.getApiKeys().add(key);
    }
    return user;
  }

  /** The description is the handle a test refers to a key by; the value and id are opaque. */
  private static CedarUserApiKey key(String description, boolean enabled) {
    CedarUserApiKey key = new CedarUserApiKey();
    key.setId(UUID.randomUUID().toString());
    key.setKey(description.replace(' ', '-') + "-" + UUID.randomUUID());
    key.setServiceName("CEDAR");
    key.setDescription(description);
    key.setCreationDate(OffsetDateTime.now());
    key.setEnabled(enabled);
    return key;
  }

  private static String valueOf(CedarUser user, String description) {
    return keyNamed(user, description).getKey();
  }

  private static String idOf(CedarUser user, String description) {
    return keyNamed(user, description).getId();
  }

  private static CedarUserApiKey keyNamed(CedarUser user, String description) {
    return user.getApiKeys().stream()
        .filter(key -> description.equals(key.getDescription()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("No key described as " + description));
  }
}
