package org.metadatacenter.server.resource;

import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.id.CedarArtifactId;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.rest.context.CedarRequestContextFactory;
import org.metadatacenter.server.neo4j.ArtifactCreateCleanupOutbox;
import org.metadatacenter.server.service.UserService;
import org.metadatacenter.util.http.ArtifactServiceClient;
import org.apache.hc.core5.http.io.entity.EntityUtils;
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
  public void created(String job, String id, String etag) {
    // Jetty can suffix the response validator (for example, "1--gzip"). Persist the exact
    // datastore revision, using the same parser as conditional writes, never a wildcard.
    String condition = null;
    if (etag != null) {
      var parsed = org.metadatacenter.util.http.RevisionPreconditionParser.parse(etag);
      if (!parsed.anyCurrentRevision() && parsed.revisions().size() == 1) {
        condition = org.metadatacenter.util.http.RevisionPreconditionParser.format(parsed.revisions().iterator().next());
      }
    }
    outbox.created(job,id,condition);
  }
  public void rejected(String job, int status) { outbox.rejected(job,status); }

  public void cleanupNow(String job, CedarRequestContext context) {
    try {
      outbox.attempt(job, pending -> {
        String url=config.getMicroserviceUrlUtil().getArtifact().getArtifactTypeWithId(pending.resourceType(),
            CedarArtifactId.build(pending.resourceId(), pending.resourceType()));
        try (var response=new ArtifactServiceClient(config).delete(url, context, pending.etag())) {
          EntityUtils.consume(response.getEntity());
          return response.getCode();
        } catch (Exception failure) {
          log.warn("Failed-create cleanup remains pending for {}", pending.resourceId(), failure);
          return 503;
        }
      });
    } catch (Exception failure) { log.warn("Create cleanup {} remains recorded for recovery", job, failure); }
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
