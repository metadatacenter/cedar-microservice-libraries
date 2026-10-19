package org.metadatacenter.cedar.util.dw;

import jakarta.ws.rs.ext.ParamConverter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The conversion behind every true-or-false request parameter. The HTTP answer to a refused value is
 * exercised by {@link ResponseContractMatrixTest}.
 */
class CedarBooleanParamConverterProviderTest {

  private static final Annotation[] NONE = new Annotation[0];
  private final CedarBooleanParamConverterProvider provider = new CedarBooleanParamConverterProvider();

  /** The declared types a parameter can take, read as Jersey reads them. */
  @SuppressWarnings("unused")
  private static void declared(boolean primitive, Boolean wrapper, Optional<Boolean> optional,
                               Optional<String> text, List<Boolean> each) {
  }

  private static Type declaredType(int position) throws Exception {
    return CedarBooleanParamConverterProviderTest.class.getDeclaredMethod("declared", boolean.class,
        Boolean.class, Optional.class, Optional.class, List.class).getGenericParameterTypes()[position];
  }

  private ParamConverter<Boolean> primitive() {
    return provider.getConverter(boolean.class, boolean.class, NONE);
  }

  private ParamConverter<Boolean> wrapper() {
    return provider.getConverter(Boolean.class, Boolean.class, NONE);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private ParamConverter<Optional<Boolean>> optional() throws Exception {
    return (ParamConverter) provider.getConverter(Optional.class, declaredType(2), NONE);
  }

  @ParameterizedTest @ValueSource(strings = {"true", "TRUE", "True", "tRuE"})
  void trueInAnyCaseIsTrue(String value) throws Exception {
    assertEquals(Boolean.TRUE, primitive().fromString(value));
    assertEquals(Boolean.TRUE, wrapper().fromString(value));
    assertEquals(Optional.of(true), optional().fromString(value));
  }

  @ParameterizedTest @ValueSource(strings = {"false", "FALSE", "False", "fAlSe"})
  void falseInAnyCaseIsFalse(String value) throws Exception {
    assertEquals(Boolean.FALSE, primitive().fromString(value));
    assertEquals(Boolean.FALSE, wrapper().fromString(value));
    assertEquals(Optional.of(false), optional().fromString(value));
  }

  /**
   * Every other value is refused, where Jersey read it as false. That includes the empty value, a
   * value with surrounding space, and a word with a letter that only case-folds to a letter of true
   * or false.
   */
  @ParameterizedTest @ValueSource(strings = {"abc", "1", "0", "yes", "no", "on", "", " ", " true", "false ",
      "falſe", "t", "f"})
  void everyOtherValueIsRefused(String value) throws Exception {
    assertThrows(IllegalArgumentException.class, () -> primitive().fromString(value));
    assertThrows(IllegalArgumentException.class, () -> wrapper().fromString(value));
    ParamConverter<Optional<Boolean>> optional = optional();
    assertThrows(IllegalArgumentException.class, () -> optional.fromString(value));
  }

  /**
   * Jersey asks for the value of an absent parameter that declares no default by converting null. The
   * answer is the one Jersey gave before: false for a boolean, null for a Boolean, and an empty
   * Optional for an Optional.
   */
  @Test
  void anAbsentParameterKeepsTheValueJerseyGaveIt() throws Exception {
    assertEquals(Boolean.FALSE, primitive().fromString(null));
    assertNull(wrapper().fromString(null));
    assertEquals(Optional.empty(), optional().fromString(null));
  }

  /**
   * Only the three declarations are claimed. A list of Boolean is converted value by value through the
   * Boolean converter, and every other type is left to Jersey.
   */
  @Test
  void onlyTrueOrFalseDeclarationsAreClaimed() throws Exception {
    assertNull(provider.getConverter(String.class, String.class, NONE));
    assertNull(provider.getConverter(Integer.class, Integer.class, NONE));
    assertNull(provider.getConverter(int.class, int.class, NONE));
    assertNull(provider.getConverter(Optional.class, declaredType(3), NONE));
    assertNull(provider.getConverter(Optional.class, Optional.class, NONE));
    assertNull(provider.getConverter(List.class, declaredType(4), NONE));
  }

  @Test
  void aValueIsWrittenAsTrueOrFalse() throws Exception {
    assertEquals("true", primitive().toString(true));
    assertEquals("false", wrapper().toString(false));
    assertEquals("true", optional().toString(Optional.of(true)));
    assertThrows(IllegalArgumentException.class, () -> wrapper().toString(null));
    ParamConverter<Optional<Boolean>> optional = optional();
    assertThrows(IllegalArgumentException.class, () -> optional.toString(Optional.empty()));
  }

}
