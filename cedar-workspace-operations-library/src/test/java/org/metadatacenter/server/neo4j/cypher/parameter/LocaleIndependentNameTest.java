package org.metadatacenter.server.neo4j.cypher.parameter;

import org.junit.jupiter.api.Test;
import org.metadatacenter.server.neo4j.parameter.CypherParameters;
import java.util.Locale;
import static org.junit.jupiter.api.Assertions.*;

class LocaleIndependentNameTest {
  @Test void storedNamesAndLookupKeysAgreeAcrossHostLocalesWithoutChangingTheName() {
    Locale original = Locale.getDefault();
    try {
      for (String language : new String[] {"en", "tr", "lt"}) {
        Locale.setDefault(Locale.forLanguageTag(language));
        var created = CypherParamBuilderGroup.createGroup(null, "IDENTITY", "", null, "").asMap();
        assertTrue(created.containsValue("IDENTITY"));
        assertTrue(created.containsValue("identity"));
        for (CypherParameters lookup : new CypherParameters[] {
            CypherParamBuilderGroup.getGroupByName("IDENTITY"),
            CypherParamBuilderCategory.getCategoryByParentAndName(null, "IDENTITY"),
            CypherParamBuilderFilesystemResource.getResourceByParentIdAndName(null, "IDENTITY")}) {
          assertTrue(lookup.asMap().containsValue("identity"), lookup.asMap().toString());
          assertFalse(lookup.asMap().containsValue("ıdentıty"));
        }
      }
    } finally { Locale.setDefault(original); }
  }
}
