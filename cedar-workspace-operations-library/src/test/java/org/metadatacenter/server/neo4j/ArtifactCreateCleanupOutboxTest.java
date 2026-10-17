package org.metadatacenter.server.neo4j;

import org.junit.jupiter.api.*;
import org.metadatacenter.model.CedarResourceType;
import org.neo4j.driver.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.ToIntFunction;
import org.metadatacenter.server.neo4j.ArtifactCreateCleanupOutbox.Stored;
import static org.junit.jupiter.api.Assertions.*;

/** Persisted transitions and crash windows, rather than a mocked successful cleanup call. */
class ArtifactCreateCleanupOutboxTest {
  static Driver database;
  @BeforeAll static void start() {
    ScratchNeo4j.blank();
    database=GraphDatabase.driver(ScratchNeo4j.boltURI(),AuthTokens.none());
  }
  @AfterAll static void stop() { database.close(); }
  @BeforeEach void clear() { query("MATCH (n) DETACH DELETE n", Map.of()); }
  ArtifactCreateCleanupOutbox open() {
    return new ArtifactCreateCleanupOutbox(GraphDatabase.driver(ScratchNeo4j.boltURI(),AuthTokens.none()),0);
  }
  static void query(String query, Map<String,Object> args) {
    try(var session=database.session()) { session.run(query,args).consume(); }
  }
  static Map<String,Object> state(String job) {
    try(var session=database.session()) {
      var result=session.run("MATCH (j:CedarArtifactCreateCleanup {jobId:$job}) RETURN properties(j) AS job",Map.of("job",job));
      return result.hasNext()?result.single().get("job").asMap():Map.of();
    }
  }
  static void register(String job, boolean commit) {
    try(var session=database.session();var tx=session.beginTransaction()) {
      VersionChainTransaction.lock(tx);
      ArtifactCreateCleanupOutbox.requireRegistration(tx,job,"artifact");
      tx.run("CREATE (:Artifact {_id:'artifact'})").consume();
      ArtifactCreateCleanupOutbox.registered(tx,job);
      if(commit) tx.commit();
    }
  }
  static String created(ArtifactCreateCleanupOutbox box) {
    String job=box.prepare(CedarResourceType.TEMPLATE,"create");
    box.created(job,"artifact","\"7\"");
    return job;
  }
  /** A create at the identity its caller chose, whose outcome is not yet known. */
  static String atIdentity(ArtifactCreateCleanupOutbox box) {
    return box.prepare(CedarResourceType.TEMPLATE,"copy","artifact");
  }
  /** Moves the job past the time any write it awaits could still land. */
  static void endWindow(String job) {
    query("MATCH (j:CedarArtifactCreateCleanup {jobId:$job}) SET j.recheckUntil=timestamp()-1",Map.of("job",job));
  }
  static ToIntFunction<ArtifactCreateCleanupOutbox.Job> neverDelete(String why) {
    return ignored -> fail(why);
  }

  @Test void aFailedDeleteSurvivesRestartAndRetainsItsExactRevision() {
    String job;
    try(var box=open()) {
      job=created(box);
      box.attempt(job, ignored -> 503);
      assertEquals(1L,state(job).get("attempts"));
    }
    try(var restarted=open()) {
      restarted.attempt(job, pending -> {
        assertEquals("artifact",pending.resourceId()); assertEquals("\"7\"",pending.etag()); return 204;
      });
      assertTrue(state(job).isEmpty());
    }
  }

  @Test void crashingAfterContentCreationBeforeGraphRegistrationIsRecoverable() {
    String job;
    try(var box=open()) { job=created(box); }
    try(var restarted=open()) {
      assertEquals(List.of(job),restarted.pending(10));
      restarted.attempt(job, ignored -> 204);
      assertTrue(state(job).isEmpty());
      assertThrows(IllegalStateException.class,() -> register(job,true));
    }
  }

  @Test void graphCommitRetiresCleanupEvenIfItsAcknowledgementIsLost() {
    String job;
    try(var box=open()) { job=created(box); register(job,true); }
    try(var restarted=open()) {
      restarted.attempt(job, ignored -> fail("A committed create must never be deleted"));
      assertTrue(state(job).isEmpty());
    }
  }

  @Test void graphRollbackRestoresCleanupInTheSameTransaction() {
    try(var box=open()) {
      String job=created(box); register(job,false);
      assertFalse(state(job).isEmpty());
      box.attempt(job,ignored -> 204);
      assertTrue(state(job).isEmpty());
    }
  }

  @Test void lostDeleteAcknowledgementCannotAllowLateRegistration() {
    String job;
    try(var box=open()) {
      job=created(box);
      assertThrows(AssertionError.class,() -> box.attempt(job,ignored -> {
        // Content DELETE committed, then the process died before acknowledging in Neo4j.
        throw new AssertionError("simulated process termination");
      }));
    }
    try(var restarted=open()) {
      assertEquals(true,state(job).get("cleanupStarted"));
      assertThrows(IllegalStateException.class,() -> register(job,true));
      restarted.attempt(job,ignored -> 404);
      assertTrue(state(job).isEmpty());
    }
  }

  @Test void registrationWaitingBehindCleanupCannotCreateAGraphOrphan() throws Exception {
    try(var box=open()) {
      String job=created(box);
      var deleting=new CountDownLatch(1); var release=new CountDownLatch(1);
      var pool=Executors.newFixedThreadPool(2);
      try {
        var cleanup=pool.submit(() -> box.attempt(job,ignored -> {
          deleting.countDown();
          try { assertTrue(release.await(10,TimeUnit.SECONDS)); }
          catch(InterruptedException e) { throw new AssertionError(e); }
          return 204;
        }));
        assertTrue(deleting.await(10,TimeUnit.SECONDS));
        var registration=pool.submit(() -> register(job,true));
        assertThrows(TimeoutException.class,() -> registration.get(200,TimeUnit.MILLISECONDS));
        release.countDown(); cleanup.get(10,TimeUnit.SECONDS);
        var error=assertThrows(ExecutionException.class,() -> registration.get(10,TimeUnit.SECONDS));
        assertInstanceOf(IllegalStateException.class,error.getCause());
      } finally { release.countDown();pool.shutdownNow(); }
    }
  }

  @Test void aLaterRevisionSurvivesCleanupAndRemainsVisibleForInspection() {
    String job;
    try(var box=open()) { job=created(box); }
    // The content store preserves monotonic revisions across both edits and delete/recreate.
    AtomicInteger current=new AtomicInteger(8);
    try(var restarted=open()) {
      restarted.attempt(job,pending -> {
        if(!pending.etag().equals("\""+current.get()+"\"")) return 412;
        current.set(0);return 204;
      });
      assertEquals(8,current.get(),"The later document must survive");
      assertEquals(true,state(job).get("parked"));
      assertTrue(state(job).get("parkedReason").toString().contains("recreated"));
      restarted.attempt(job,ignored -> fail("Parked jobs must not automatically retry"));
    }
  }

  @Test void aRegisteredArtifactIsNeverDeletedByAnOlderUnfinishedCreate() {
    try(var box=open()) {
      String oldJob=created(box),newJob=created(box);
      register(newJob,true);
      box.attempt(oldJob,ignored -> fail("Registered content must be retained"));
      assertEquals(true,state(oldJob).get("parked"));
    }
  }

  @Test void anUnknownCreateOutcomeSurvivesRestartWithoutGuessingADelete() {
    String job;
    try(var box=open()) { job=box.prepare(CedarResourceType.INSTANCE,"worker-clone"); }
    try(var restarted=open()) {
      restarted.attempt(job,ignored -> fail("A missing response is not proof of what was created"));
      assertEquals(true,state(job).get("parked"));
      assertEquals("worker-clone",state(job).get("operation"));
      // A late successful response supplies evidence; it may still complete normally.
      restarted.created(job,"artifact","\"7\""); register(job,true);
      assertTrue(state(job).isEmpty());
    }
  }

  @Test void missingValidatorIsParkedInsteadOfDeletingUnconditionally() {
    try(var box=open()) {
      String job=box.prepare(CedarResourceType.TEMPLATE,"copy");box.created(job,"artifact",null);
      box.attempt(job,ignored -> fail("Never issue a DELETE without its exact validator"));
      assertEquals(true,state(job).get("parked"));
    }
  }

  @Test void transientFailuresEventuallyParkAndCannotStarveOtherWork() {
    try(var box=open()) {
      String bad=created(box),good=created(box);
      for(int i=0;i<60;i++) box.attempt(bad,ignored -> 503);
      assertEquals(60L,state(bad).get("attempts"));
      assertEquals(true,state(bad).get("parked"));
      assertEquals(List.of(good),box.pending(25));
      box.attempt(good,ignored -> 204);
      assertTrue(box.pending(25).isEmpty());
    }
  }

  @Test void anIdentityWhoseDocumentNeverAppearsIsReadUntilNoWriteCanLandThenRetired() {
    try(var box=open()) {
      String job=atIdentity(box);
      AtomicInteger reads=new AtomicInteger();
      box.attempt(job,pending -> {
        reads.incrementAndGet(); assertEquals("artifact",pending.resourceId()); assertNull(pending.etag());
        return new Stored.Nothing();
      },neverDelete("Nothing was stored, so nothing may be deleted"));
      assertEquals(false,state(job).get("parked"),"An unknown outcome at a known identity is read again");
      assertEquals(List.of(job),box.pending(10));
      endWindow(job);
      box.attempt(job,pending -> { reads.incrementAndGet(); return new Stored.Nothing(); },
          neverDelete("Nothing was stored, so nothing may be deleted"));
      assertTrue(state(job).isEmpty(),"The job outlived the window");
      assertEquals(2,reads.get());
    }
  }

  @Test void aDocumentStoredAfterTheReadBackIsDeletedAtTheRevisionJustRead() {
    String job;
    try(var box=open()) {
      job=atIdentity(box);
      box.attempt(job,ignored -> new Stored.Nothing(),neverDelete("Nothing is stored yet"));
    }
    // The lost write lands after the read-back, and a restarted relay reads the identity again.
    try(var restarted=open()) {
      List<String> conditions=new ArrayList<>();
      restarted.attempt(job,ignored -> new Stored.Revision("\"9\""),pending -> {
        assertEquals("artifact",pending.resourceId()); conditions.add(pending.etag()); return 204;
      });
      assertEquals(List.of("\"9\""),conditions,"The delete must be conditional on the revision just read");
      assertTrue(state(job).isEmpty());
      assertThrows(IllegalStateException.class,() -> register(job,true),"A removed create registered");
    }
  }

  @Test void aDocumentAlreadyGoneWhenItsDeleteArrivesRetiresTheJob() {
    try(var box=open()) {
      String job=atIdentity(box);
      box.attempt(job,ignored -> new Stored.Revision("\"9\""),ignored -> 404);
      assertTrue(state(job).isEmpty());
    }
  }

  @Test void aDocumentTheGraphRegisteredIsNeverDeleted() {
    try(var box=open()) {
      String lost=atIdentity(box),retry=atIdentity(box);
      box.created(retry,"artifact","\"8\""); register(retry,true);
      box.attempt(lost,ignored -> new Stored.Revision("\"8\""),neverDelete("Registered content must be retained"));
      assertEquals(false,state(lost).get("parked"));
      endWindow(lost);
      box.attempt(lost,ignored -> new Stored.Revision("\"8\""),neverDelete("Registered content must be retained"));
      assertTrue(state(lost).isEmpty(),"Nothing is left to remove once no write can land");
    }
  }

  @Test void aRegistrationThatLandsWhileTheIdentityIsReadIsSeenBeforeAnyDelete() {
    try(var box=open()) {
      String job=atIdentity(box);
      box.attempt(job,ignored -> {
        query("CREATE (:Artifact {_id:'artifact'})",Map.of());
        return new Stored.Revision("\"8\"");
      },neverDelete("The graph registered the document after it was read"));
      assertEquals(false,state(job).get("parked"),"A registered identity is read again until the window ends");
      assertNotEquals(true,state(job).get("cleanupStarted"));
    }
  }

  @Test void aRetryAtTheIdentityCannotRegisterTheDocumentTheRelayDeletes() {
    try(var box=open()) {
      String lost=atIdentity(box),retry=atIdentity(box);
      // The retry's write stored revision 8 and its answer is on its way when the relay reads it.
      box.attempt(lost,ignored -> new Stored.Revision("\"8\""),ignored -> 204);
      assertTrue(state(lost).isEmpty());
      box.created(retry,"artifact","\"8\"");
      assertThrows(IllegalStateException.class,() -> register(retry,true),
          "The retry registered a document the relay deleted");
      box.attempt(retry,ignored -> fail("The retry knows its revision"),ignored -> 404);
      assertTrue(state(retry).isEmpty());
    }
  }

  @Test void aDocumentAnotherCreateRecordedIsLeftToThatCreate() {
    try(var box=open()) {
      String lost=atIdentity(box),retry=atIdentity(box);
      box.created(retry,"artifact","\"8\"");
      box.attempt(lost,ignored -> new Stored.Revision("\"8\""),neverDelete("The retry answers for its own document"));
      assertNotEquals(true,state(retry).get("cleanupStarted"));
      register(retry,true);
      assertTrue(state(retry).isEmpty());
    }
  }

  @Test void aDocumentThatChangedAfterTheRelayReadItIsParkedRatherThanDeleted() {
    try(var box=open()) {
      String job=atIdentity(box);
      AtomicInteger deletes=new AtomicInteger();
      box.attempt(job,ignored -> new Stored.Revision("\"9\""),pending -> { deletes.incrementAndGet(); return 412; });
      assertEquals(1,deletes.get());
      assertEquals(true,state(job).get("parked"));
      assertTrue(state(job).get("parkedReason").toString().contains("recreated"));
      box.attempt(job,ignored -> fail("A parked job is not read again"),neverDelete("Parked jobs must not retry"));
      assertEquals(1,deletes.get());
    }
  }

  @Test void aDocumentWithoutAnExactRevisionIsParkedRatherThanDeleted() {
    try(var box=open()) {
      String job=atIdentity(box);
      box.attempt(job,ignored -> new Stored.Unversioned(),neverDelete("Never delete without an exact validator"));
      assertEquals(true,state(job).get("parked"));
    }
  }

  @Test void anIdentityThatCannotBeReadIsParkedOnlyOnceNoWriteCanLand() {
    try(var box=open()) {
      String job=atIdentity(box);
      for(int i=0;i<60;i++) box.attempt(job,ignored -> new Stored.Unknown(503),neverDelete("Nothing was read"));
      assertEquals(false,state(job).get("parked"),"A write may still land, so the job is read again");
      endWindow(job);
      box.attempt(job,ignored -> { throw new IllegalStateException("connection refused"); },
          neverDelete("Nothing was read"));
      assertEquals(true,state(job).get("parked"));
    }
  }

  @Test void aJobWithoutAnIdentityIsNeverReadAndKeepsItsHandling() {
    try(var box=open()) {
      String unknown=box.prepare(CedarResourceType.INSTANCE,"worker-clone");
      box.attempt(unknown,ignored -> fail("A job without an identity has nothing to read"),
          neverDelete("A missing response is not proof of what was created"));
      assertEquals(true,state(unknown).get("parked"));
      assertTrue(state(unknown).get("parkedReason").toString().contains("unknown"));

      String unvalidated=box.prepare(CedarResourceType.TEMPLATE,"copy");box.created(unvalidated,"artifact",null);
      box.attempt(unvalidated,ignored -> fail("Only a job that held its identity from the start is read"),
          neverDelete("Never issue a DELETE without its exact validator"));
      assertEquals(true,state(unvalidated).get("parked"));

      String known=created(box);
      box.attempt(known,ignored -> fail("A known revision needs no read"),pending -> {
        assertEquals("\"7\"",pending.etag()); return 204;
      });
      assertTrue(state(known).isEmpty());
    }
  }

  @Test void aRelayThatCannotReadParksAnIdentityRatherThanGuessing() {
    try(var box=open()) {
      String job=atIdentity(box);
      box.attempt(job,neverDelete("The outcome is unknown"));
      assertEquals(true,state(job).get("parked"));
    }
  }

  @Test void aCreateAtAnIdentityTakesOnlyThatIdentitysOutcome() {
    try(var box=open()) {
      String refused=atIdentity(box);
      box.rejected(refused,412);
      assertTrue(state(refused).isEmpty(),"A refused create stored nothing to look for");

      String other=atIdentity(box);
      assertThrows(IllegalStateException.class,() -> box.created(other,"elsewhere","\"1\""));
      box.created(other,"artifact","\"1\"");
      assertEquals("\"1\"",state(other).get("etag"));
      assertThrows(IllegalStateException.class,() -> box.created(other,"artifact","\"2\""));
    }
  }
}
