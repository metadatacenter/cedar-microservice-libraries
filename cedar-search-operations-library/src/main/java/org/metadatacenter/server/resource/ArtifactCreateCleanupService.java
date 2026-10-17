package org.metadatacenter.server.resource;

import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.id.CedarArtifactId;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.rest.context.CedarRequestContextFactory;
import org.metadatacenter.server.neo4j.ArtifactCreateCleanupOutbox;
import org.metadatacenter.server.neo4j.ArtifactCreateCleanupOutbox.Stored;
import org.metadatacenter.server.service.UserService;
import org.metadatacenter.util.http.ArtifactServiceClient;
import org.metadatacenter.util.http.RevisionPreconditionParser;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import java.util.Optional;
import java.util.concurrent.*;

/** Shared producer and relay for resource requests and background instance clones. */
public final class ArtifactCreateCleanupService implements AutoCloseable {
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ArtifactCreateCleanupService.class);
  private final CedarConfig config;
  private final UserService users;
  private final ArtifactCreateCleanupOutbox outbox;
  private ScheduledExecutorService executor;

  public ArtifactCreateCleanupService(CedarConfig config, UserService users) {
    this.config=config; this.users=users; this.outbox=new ArtifactCreateCleanupOutbox(config);
  }

  public String prepare(CedarResourceType type, String operation) { return outbox.prepare(type, operation); }

  /**
   * Records a create's intent before its content write, with the identity its caller chose when
   * there is one. The cleanup then holds the identity from the start, so it can remove a document
   * stored there after the create's answer was lost and reading the identity back found nothing.
   */
  public String prepare(CedarResourceType type, String operation, Optional<CedarArtifactId> identity) {
    return identity.isPresent() ? outbox.prepare(type, operation, identity.get().getId()) : outbox.prepare(type, operation);
  }

  public void created(String job, String id, String etag) { outbox.created(job,id,exactRevision(etag)); }
  public void rejected(String job, int status) { outbox.rejected(job,status); }

  /**
   * The single datastore revision a validator names, formatted as a conditional write names it, or
   * null when it names none or several. Jetty can suffix a response validator, as in "1--gzip", so
   * the validator is read with the parser conditional writes use, and a wildcard never counts.
   */
  static String exactRevision(String etag) {
    if (etag == null) return null;
    var parsed = RevisionPreconditionParser.parse(etag);
    return !parsed.anyCurrentRevision() && parsed.revisions().size() == 1
        ? RevisionPreconditionParser.format(parsed.revisions().iterator().next()) : null;
  }

  /** What an answer to reading a job's identity says is stored there. */
  static Stored stored(int status, String etag) {
    if (status == HttpStatus.SC_NOT_FOUND) return new Stored.Nothing();
    if (status != HttpStatus.SC_OK) return new Stored.Unknown(status);
    String revision = exactRevision(etag);
    return revision == null ? new Stored.Unversioned() : new Stored.Revision(revision);
  }

  public void cleanupNow(String job, CedarRequestContext context) {
    try {
      outbox.attempt(job, pending -> {
        try (var response=new ArtifactServiceClient(config).get(url(pending), context)) {
          EntityUtils.consume(response.getEntity());
          var etag = response.getFirstHeader(HttpHeaders.ETAG);
          return stored(response.getCode(), etag == null ? null : etag.getValue());
        } catch (Exception failure) {
          log.warn("The identity {} of an unsettled create could not be read; its cleanup reads it again",
              pending.resourceId(), failure);
          return new Stored.Unknown(503);
        }
      }, pending -> {
        try (var response=new ArtifactServiceClient(config).delete(url(pending), context, pending.etag())) {
          EntityUtils.consume(response.getEntity());
          return response.getCode();
        } catch (Exception failure) {
          log.warn("Failed-create cleanup remains pending for {}", pending.resourceId(), failure);
          return 503;
        }
      });
    } catch (Exception failure) { log.warn("Create cleanup {} remains recorded for recovery", job, failure); }
  }

  private String url(ArtifactCreateCleanupOutbox.Job pending) {
    return config.getMicroserviceUrlUtil().getArtifact().getArtifactTypeWithId(pending.resourceType(),
        CedarArtifactId.build(pending.resourceId(), pending.resourceType()));
  }

  public void resume() {
    var admin=CedarRequestContextFactory.fromAdminUser(config,users);
    for (String job:outbox.pending(25)) cleanupNow(job,admin);
  }

  public synchronized void start() {
    if(executor!=null) return;
    executor=Executors.newSingleThreadScheduledExecutor(r -> {
      var thread=new Thread(r,"artifact-create-cleanup-relay"); thread.setDaemon(true); return thread;
    });
    executor.scheduleWithFixedDelay(() -> {
      try { resume(); } catch(Exception failure) { log.warn("Create cleanup relay failed",failure); }
    },5,5,TimeUnit.SECONDS);
  }

  @Override public synchronized void close() {
    if(executor!=null) executor.shutdownNow();
    outbox.close();
  }
}
