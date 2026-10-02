package org.metadatacenter.server.neo4j;

import org.junit.jupiter.api.*;
import org.metadatacenter.model.CedarResourceType;
import org.neo4j.driver.*;
import org.neo4j.harness.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/** Persisted transitions and crash windows, rather than a mocked successful cleanup call. */
class ArtifactCreateCleanupOutboxTest {
  static Neo4j neo;
  static Driver database;
  @BeforeAll static void start() {
    neo=Neo4jBuilders.newInProcessBuilder().withDisabledServer().build();
    database=GraphDatabase.driver(neo.boltURI(),AuthTokens.none());
  }
  @AfterAll static void stop() { database.close(); neo.close(); }
  @BeforeEach void clear() { query("MATCH (n) DETACH DELETE n", Map.of()); }
  ArtifactCreateCleanupOutbox open() {
    return new ArtifactCreateCleanupOutbox(GraphDatabase.driver(neo.boltURI(),AuthTokens.none()),0);
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
}
