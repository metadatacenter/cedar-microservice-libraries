package org.metadatacenter.util.test;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.Path;
import org.junit.jupiter.api.Assertions;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reflection over the declared JAX-RS surface of a set of resource classes, plus a probe that
 * checks every declared route still answers on a booted application.
 *
 * <p>This is the generalized form of the route safety net first written for the resource server's
 * artifact resources. The point is not to test business logic: it is to catch a route silently
 * vanishing, changing verb, or losing its authentication assertion — the failure modes a framework
 * upgrade or a refactor introduces, which unit tests and a happy-path smoke both miss.
 *
 * <p>Typical use, for a server whose endpoints all require authentication:
 * <pre>
 *   RouteSurface.assertEveryRouteAnswers(
 *       "http://localhost:" + SERVER.getLocalPort(),
 *       RouteSurface.endpoints(FooResource.class, BarResource.class),
 *       401);
 * </pre>
 */
public final class RouteSurface {

  private static final Pattern PATH_TEMPLATE_VARIABLE = Pattern.compile("\\{([^}]+)}");

  private RouteSurface() {
  }

  /** One endpoint: an HTTP-verb-annotated method on a resource class. */
  public static final class Endpoint {
    public final String declaringClass;
    public final String methodName;
    public final String verb;
    public final String fullPath;
    /** The first media type the endpoint declares via {@code @Consumes}, or null if it declares none. */
    public final String consumes;

    private Endpoint(String declaringClass, String methodName, String verb, String fullPath, String consumes) {
      this.declaringClass = declaringClass;
      this.methodName = methodName;
      this.verb = verb;
      this.fullPath = fullPath;
      this.consumes = consumes;
    }

    /** Verb + path, the stable identity of a route. */
    public String key() {
      return verb + " " + fullPath;
    }

    @Override
    public String toString() {
      return key() + " (" + declaringClass + "." + methodName + ")";
    }
  }

  /** Every endpoint declared by the given resource classes, sorted by verb + path. */
  public static List<Endpoint> endpoints(Class<?>... resourceClasses) {
    return endpoints(List.of(resourceClasses));
  }

  /** Every endpoint declared by the given resource classes, sorted by verb + path. */
  public static List<Endpoint> endpoints(List<Class<?>> resourceClasses) {
    List<Endpoint> endpoints = new ArrayList<>();
    for (Class<?> resourceClass : resourceClasses) {
      Path classPathAnnotation = resourceClass.getAnnotation(Path.class);
      String classPath = classPathAnnotation == null ? "" : classPathAnnotation.value();
      Consumes classConsumes = resourceClass.getAnnotation(Consumes.class);
      // getMethods() includes inherited public methods, so endpoints declared on an abstract
      // ancestor are captured too.
      for (Method method : resourceClass.getMethods()) {
        String verb = httpVerbOf(method);
        if (verb == null) {
          continue;
        }
        Path methodPath = method.getAnnotation(Path.class);
        String fullPath = joinPaths(classPath, methodPath == null ? "" : methodPath.value());
        Consumes methodConsumes = method.getAnnotation(Consumes.class);
        Consumes effectiveConsumes = methodConsumes != null ? methodConsumes : classConsumes;
        String consumes = effectiveConsumes == null || effectiveConsumes.value().length == 0
            ? null : effectiveConsumes.value()[0];
        endpoints.add(new Endpoint(method.getDeclaringClass().getSimpleName(), method.getName(), verb, fullPath, consumes));
      }
    }
    endpoints.sort(Comparator.comparing(Endpoint::key));
    return endpoints;
  }

  /**
   * Returns the JAX-RS resource classes among the instances a booted Jersey application actually
   * registered. This keeps route probes tied to runtime wiring: adding a resource to the
   * application automatically adds all of its endpoints to the probe without requiring a second,
   * hand-maintained class list.
   *
   * @param registeredComponents resources from Jersey's {@code ResourceConfig#getResources()}
   * @param packagePrefix       limits discovery to the application's own resources and excludes
   *                            framework-provided endpoints
   */
  public static List<Class<?>> registeredResourceClasses(Iterable<?> registeredComponents,
                                                          String packagePrefix) {
    List<Class<?>> resourceClasses = new ArrayList<>();
    for (Object component : registeredComponents) {
      if (component instanceof Class<?> componentClass) {
        addResourceClass(resourceClasses, componentClass, packagePrefix);
      } else {
        addResourceClass(resourceClasses, component.getClass(), packagePrefix);
      }
      try {
        Iterable<?> handlerClasses = (Iterable<?>) component.getClass().getMethod("getHandlerClasses").invoke(component);
        for (Object handlerClass : handlerClasses) {
          addResourceClass(resourceClasses, (Class<?>) handlerClass, packagePrefix);
        }
        Iterable<?> handlerInstances =
            (Iterable<?>) component.getClass().getMethod("getHandlerInstances").invoke(component);
        for (Object handlerInstance : handlerInstances) {
          addResourceClass(resourceClasses, handlerInstance.getClass(), packagePrefix);
        }
      } catch (NoSuchMethodException ignored) {
        // A directly registered resource instance/class has already been handled above.
      } catch (ReflectiveOperationException e) {
        throw new IllegalArgumentException("Registered component does not expose Jersey resource handlers", e);
      }
    }
    resourceClasses = resourceClasses.stream().distinct().sorted(Comparator.comparing(Class::getName)).toList();
    return resourceClasses;
  }

  private static void addResourceClass(List<Class<?>> resourceClasses, Class<?> resourceClass,
                                       String packagePrefix) {
    if (resourceClass.getName().startsWith(packagePrefix) && resourceClass.isAnnotationPresent(Path.class)) {
      resourceClasses.add(resourceClass);
    }
  }

  /**
   * Probes every endpoint against the booted application and asserts each answers
   * {@code expectedStatus}. A 404 or 405 is called out separately: it means the route vanished or
   * changed verb, which is the regression this net exists to catch.
   *
   * <p>All failures are collected so one run reports the complete divergence rather than the first
   * mismatch.
   */
  public static void assertEveryRouteAnswers(String baseUrl, List<Endpoint> endpoints, int expectedStatus) {
    Assertions.assertFalse(endpoints.isEmpty(),
        "No endpoints were found by reflection — the resource class list is wrong, "
            + "which would make this test vacuously pass");

    StringBuilder failures = new StringBuilder();
    for (Endpoint endpoint : endpoints) {
      int status;
      try {
        status = probe(baseUrl, endpoint);
      } catch (Exception e) {
        failures.append(endpoint.key()).append(": request failed - ").append(e).append('\n');
        continue;
      }
      if (status == 404 || status == 405) {
        failures.append(endpoint.key()).append(": got ").append(status)
            .append(" - the route vanished or changed verb\n");
      } else if (status != expectedStatus) {
        failures.append(endpoint.key()).append(": expected ").append(expectedStatus)
            .append(" but got ").append(status).append('\n');
      }
    }
    Assertions.assertEquals(0, failures.length(),
        "Route responses diverged from the expected status " + expectedStatus + ":\n" + failures);
  }

  /**
   * The endpoint's path with every template variable replaced by a concrete value, ready to be
   * requested. Callers building their own requests (for example to add an Authorization header)
   * should use this rather than {@link Endpoint#fullPath}, which still contains {@code {name}}
   * placeholders that would not match the route.
   */
  public static String resolvedPath(Endpoint endpoint) {
    return substitutePathParameters(endpoint.fullPath);
  }

  /** Sends one request at a route, substituting any path parameters, and returns the status. */
  public static int probe(String baseUrl, Endpoint endpoint) throws Exception {
    HttpRequest.Builder builder = HttpRequest.newBuilder()
        .uri(URI.create(baseUrl + substitutePathParameters(endpoint.fullPath)));
    if (endpoint.verb.equals("POST") || endpoint.verb.equals("PUT") || endpoint.verb.equals("PATCH")) {
      builder.header("Content-Type", contentTypeFor(endpoint));
      builder.method(endpoint.verb, HttpRequest.BodyPublishers.ofString("{}"));
    } else {
      builder.method(endpoint.verb, HttpRequest.BodyPublishers.noBody());
    }
    HttpResponse<String> response = ProbeClient.send(builder.build());
    return response.statusCode();
  }

  /**
   * The Content-Type to send so the request survives media-type matching and actually reaches the
   * endpoint. Honouring the endpoint's own {@code @Consumes} matters: a mismatch is rejected by the
   * framework before the method body runs, so the probe would measure content negotiation instead
   * of the endpoint's authentication assertion. The body itself is never parsed — these probes are
   * expected to stop at the authentication check.
   */
  public static String contentTypeFor(Endpoint endpoint) {
    if (endpoint.consumes == null) {
      return "application/json";
    }
    if (endpoint.consumes.startsWith("multipart/")) {
      // Multipart requires a boundary parameter or the request is malformed before dispatch.
      return endpoint.consumes + "; boundary=RouteSurfaceProbeBoundary";
    }
    return endpoint.consumes;
  }

  /**
   * Replaces every path template variable with a syntactically plausible URL-encoded CEDAR
   * artifact id. The value only has to survive path matching and parameter binding: these probes
   * are expected to stop at the authentication assertion, before any id is dereferenced.
   */
  private static String substitutePathParameters(String pathTemplate) {
    int nextSlash = pathTemplate.indexOf('/', 1);
    String root = pathTemplate.length() > 1
        ? pathTemplate.substring(1, nextSlash > 0 ? nextSlash : pathTemplate.length())
        : "artifacts";
    String plausibleId = "https://repo.metadatacenter.org/" + root + "/8bc64ab5-df6b-48c8-8c61-6c016245918e";
    String encodedId = URLEncoder.encode(plausibleId, StandardCharsets.UTF_8);
    Matcher matcher = PATH_TEMPLATE_VARIABLE.matcher(pathTemplate);
    return matcher.replaceAll(Matcher.quoteReplacement(encodedId));
  }

  private static String httpVerbOf(Method method) {
    for (Annotation annotation : method.getAnnotations()) {
      HttpMethod httpMethod = annotation.annotationType().getAnnotation(HttpMethod.class);
      if (httpMethod != null) {
        return httpMethod.value();
      }
    }
    return null;
  }

  private static String joinPaths(String classPath, String methodPath) {
    String left = classPath.endsWith("/") ? classPath.substring(0, classPath.length() - 1) : classPath;
    if (methodPath.isEmpty()) {
      return left.isEmpty() ? "/" : left;
    }
    return methodPath.startsWith("/") ? left + methodPath : left + "/" + methodPath;
  }

}
