package org.metadatacenter.cedar.util.dw;

import com.codahale.metrics.health.HealthCheck;

/**
 * Reports each artifact collection's unique {@code @id} index without failing on a missing one.
 *
 * <p>Staying healthy is deliberate. A missing index is a provisioning gap rather than a sick
 * process: the server serves every request correctly, and only concurrent creates of one identifier
 * and the cost of a lookup are affected. Turning it into an unhealthy result would stop
 * {@code cedarcli native health}, and with it the smoke gate, on a workstation whose store was
 * never provisioned - which is the very state this check exists to describe.
 */
public class ArtifactIdIndexHealthCheck extends HealthCheck {

  private final ArtifactIdIndexProbe probe;

  public ArtifactIdIndexHealthCheck(ArtifactIdIndexProbe probe) {
    this.probe = probe;
  }

  @Override
  protected Result check() {
    return Result.healthy(probe.inspect().describe());
  }
}
