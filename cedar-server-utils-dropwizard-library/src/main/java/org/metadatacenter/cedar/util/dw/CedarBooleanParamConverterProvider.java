package org.metadatacenter.cedar.util.dw;

import jakarta.ws.rs.ext.ParamConverter;
import jakarta.ws.rs.ext.ParamConverterProvider;
import jakarta.ws.rs.ext.Provider;

import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Locale;
import java.util.Optional;

/**
 * Converts a request parameter declared as a boolean, a Boolean or an Optional of Boolean, and
 * refuses every value other than true and false.
 *
 * <p>Jersey converts such a parameter with {@code Boolean.valueOf}, which reads any text other than
 * "true" as false. A request that carried {@code compact=yes} or {@code compact=1} was therefore
 * served as though it carried {@code compact=false}, and nothing told the caller. Here "true" and
 * "false", in any case, become the value, and anything else, the empty value included, is refused.
 * Jersey answers the refusal as it answers any parameter it cannot convert, and
 * {@link CedarExceptionMapper} names the parameter and the form it takes.
 *
 * <p>A parameter the request does not carry keeps its default. Jersey converts the declared default
 * value when there is one, and otherwise asks the converter to convert null. The answer to null is
 * the one Jersey gave before: false for a boolean, null for a Boolean, and an empty Optional for an
 * Optional.
 *
 * <p>The Optional is converted here rather than by Jersey. Jersey's own Optional conversion reads an
 * empty value as an absent one, and it takes the conversion of the element from whichever provider
 * it finds first, in an order this provider cannot rely on.
 */
@Provider
public final class CedarBooleanParamConverterProvider implements ParamConverterProvider {

  private static final ParamConverter<Boolean> PRIMITIVE = new BooleanConverter(Boolean.FALSE);
  private static final ParamConverter<Boolean> WRAPPER = new BooleanConverter(null);
  private static final ParamConverter<Optional<Boolean>> OPTIONAL = new OptionalBooleanConverter();

  @Override
  @SuppressWarnings("unchecked")
  public <T> ParamConverter<T> getConverter(Class<T> rawType, Type genericType, Annotation[] annotations) {
    if (rawType == boolean.class) return (ParamConverter<T>) PRIMITIVE;
    if (rawType == Boolean.class) return (ParamConverter<T>) WRAPPER;
    if (rawType == Optional.class && isOptionalOfBoolean(genericType)) return (ParamConverter<T>) OPTIONAL;
    return null;
  }

  /**
   * The value of a parameter the request carries, which must be true or false in any case. The
   * comparison is made in the root locale, so a letter that only case-folds to a letter of either
   * word, such as the long s in "falſe", is refused.
   *
   * @throws IllegalArgumentException when the value is neither true nor false
   */
  static boolean parse(String value) {
    String folded = value.toLowerCase(Locale.ROOT);
    if (folded.equals("true")) return true;
    if (folded.equals("false")) return false;
    throw new IllegalArgumentException("The value is neither true nor false");
  }

  private static boolean isOptionalOfBoolean(Type genericType) {
    return genericType instanceof ParameterizedType optional
        && optional.getActualTypeArguments().length == 1
        && optional.getActualTypeArguments()[0] == Boolean.class;
  }

  private static final class BooleanConverter implements ParamConverter<Boolean> {

    /** The value of a parameter that is absent and declares no default. */
    private final Boolean absent;

    private BooleanConverter(Boolean absent) {
      this.absent = absent;
    }

    @Override
    public Boolean fromString(String value) {
      if (value == null) return absent;
      return parse(value);
    }

    @Override
    public String toString(Boolean value) {
      if (value == null) throw new IllegalArgumentException("No value to write");
      return value.toString();
    }
  }

  private static final class OptionalBooleanConverter implements ParamConverter<Optional<Boolean>> {

    @Override
    public Optional<Boolean> fromString(String value) {
      return value == null ? Optional.empty() : Optional.of(parse(value));
    }

    @Override
    public String toString(Optional<Boolean> value) {
      if (value == null || value.isEmpty()) throw new IllegalArgumentException("No value to write");
      return value.get().toString();
    }
  }

}
