package org.metadatacenter.cedar.util.dw;

import jakarta.ws.rs.CookieParam;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.MatrixParam;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import org.glassfish.jersey.server.ParamException;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.UUID;

/**
 * The message for a request parameter that Jersey could not convert to the type its route declares.
 *
 * <p>Jersey refuses such a parameter with a ParamException that identifies the parameter and keeps
 * the failed conversion as its cause. The cause's text describes Java parsing, as in
 * {@code For input string: "abc"}, so the message is composed from the route's declaration instead.
 * It identifies the kind of parameter and its name, and says what form the declared type takes. A
 * type this class does not describe, or a route that cannot be found, still yields a message that
 * identifies the parameter.
 */
final class ParameterConversionMessage {

  private static final Map<Class<?>, String> FORMS = Map.ofEntries(
      Map.entry(int.class, integerFrom(Integer.MIN_VALUE, Integer.MAX_VALUE)),
      Map.entry(Integer.class, integerFrom(Integer.MIN_VALUE, Integer.MAX_VALUE)),
      Map.entry(OptionalInt.class, integerFrom(Integer.MIN_VALUE, Integer.MAX_VALUE)),
      Map.entry(long.class, integerFrom(Long.MIN_VALUE, Long.MAX_VALUE)),
      Map.entry(Long.class, integerFrom(Long.MIN_VALUE, Long.MAX_VALUE)),
      Map.entry(OptionalLong.class, integerFrom(Long.MIN_VALUE, Long.MAX_VALUE)),
      Map.entry(short.class, integerFrom(Short.MIN_VALUE, Short.MAX_VALUE)),
      Map.entry(Short.class, integerFrom(Short.MIN_VALUE, Short.MAX_VALUE)),
      Map.entry(byte.class, integerFrom(Byte.MIN_VALUE, Byte.MAX_VALUE)),
      Map.entry(Byte.class, integerFrom(Byte.MIN_VALUE, Byte.MAX_VALUE)),
      Map.entry(BigInteger.class, "an integer"),
      Map.entry(double.class, "a number"),
      Map.entry(Double.class, "a number"),
      Map.entry(OptionalDouble.class, "a number"),
      Map.entry(float.class, "a number"),
      Map.entry(Float.class, "a number"),
      Map.entry(BigDecimal.class, "a number"),
      Map.entry(UUID.class, "a UUID"));

  private ParameterConversionMessage() {
  }

  /**
   * The message for a refused parameter of the given route. The route is null when the mapper runs
   * outside a request Jersey matched.
   */
  static String of(ParamException refusal, Method route) {
    String parameter = kind(refusal.getParameterType()) + " " + refusal.getParameterName();
    Type declared = route == null ? null : declaredType(route, refusal);
    boolean everyValue = false;
    if (declared instanceof ParameterizedType parameterized
        && parameterized.getRawType() instanceof Class<?> container
        && parameterized.getActualTypeArguments().length == 1) {
      // Jersey converts each value of a collection, and the value of an Optional, to the type argument.
      if (container == Optional.class) {
        declared = parameterized.getActualTypeArguments()[0];
      } else if (Collection.class.isAssignableFrom(container)) {
        declared = parameterized.getActualTypeArguments()[0];
        everyValue = true;
      }
    }
    String form = declared instanceof Class<?> type ? FORMS.get(type) : null;
    if (form == null) {
      return "The " + parameter + " is not in a form this route accepts";
    }
    return (everyValue ? "Every value of the " : "The ") + parameter + " must be " + form;
  }

  private static String integerFrom(long minimum, long maximum) {
    return "an integer from " + minimum + " to " + maximum;
  }

  private static String kind(Class<? extends Annotation> source) {
    if (source == QueryParam.class) return "query parameter";
    if (source == PathParam.class) return "path parameter";
    if (source == HeaderParam.class) return "header";
    if (source == FormParam.class) return "form parameter";
    if (source == CookieParam.class) return "cookie";
    if (source == MatrixParam.class) return "matrix parameter";
    return "parameter";
  }

  /** The declared type of the route's parameter that Jersey refused, or null when none matches. */
  private static Type declaredType(Method route, ParamException refusal) {
    for (Parameter parameter : route.getParameters()) {
      Annotation source = parameter.getAnnotation(refusal.getParameterType());
      if (source != null && refusal.getParameterName().equals(declaredName(source))) {
        return parameter.getParameterizedType();
      }
    }
    return null;
  }

  private static String declaredName(Annotation source) {
    if (source instanceof QueryParam query) return query.value();
    if (source instanceof PathParam path) return path.value();
    if (source instanceof HeaderParam header) return header.value();
    if (source instanceof FormParam form) return form.value();
    if (source instanceof CookieParam cookie) return cookie.value();
    if (source instanceof MatrixParam matrix) return matrix.value();
    return null;
  }

}
