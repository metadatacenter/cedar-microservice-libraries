package org.metadatacenter.cedar.util.dw;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method that is meant to serve a caller holding no credential.
 *
 * <p>A CEDAR request is authenticated because the method resolved a user, and until this annotation existed
 * nothing recorded which methods were meant to skip that. {@link CedarMicroserviceResource#buildRequestContext()}
 * refuses a request whose credential does not resolve;
 * {@link CedarMicroserviceResource#buildAnonymousRequestContext()} performs the same construction without that
 * refusal, and it now serves only methods carrying this annotation.
 *
 * <p>Apply it to the method that asks for the anonymous context, which may be a resource method or a helper
 * shared by several of them. Whatever a caller can reach through it must expose only what the deployment is
 * willing to publish, and must not spend a credential the deployment holds.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface AnonymousAccess {
}
