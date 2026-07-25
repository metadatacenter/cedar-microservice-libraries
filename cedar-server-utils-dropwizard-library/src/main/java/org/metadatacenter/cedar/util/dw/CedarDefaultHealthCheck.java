package org.metadatacenter.cedar.util.dw;

import com.codahale.metrics.health.HealthCheck;

/**
 * A liveness placeholder shared by the CEDAR microservices: it reports healthy whenever the
 * application is up. Servers with a real dependency to probe should register their own check
 * instead of, or in addition to, this one.
 */
public class CedarDefaultHealthCheck extends HealthCheck {

  @Override
  protected Result check() {
    return Result.healthy();
  }

}
