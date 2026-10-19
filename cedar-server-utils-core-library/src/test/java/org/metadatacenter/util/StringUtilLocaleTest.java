package org.metadatacenter.util;

import org.junit.jupiter.api.Test;
import java.util.Locale;
import static org.junit.jupiter.api.Assertions.assertEquals;

class StringUtilLocaleTest {
  @Test void searchNormalizationDoesNotDependOnTheServerLocale() {
    Locale original = Locale.getDefault();
    try {
      for (String language : new String[] {"en", "tr", "lt"}) {
        Locale.setDefault(Locale.forLanguageTag(language));
        assertEquals("identity", StringUtil.basicNormalization("IDENTITY"));
      }
    } finally { Locale.setDefault(original); }
  }
}
