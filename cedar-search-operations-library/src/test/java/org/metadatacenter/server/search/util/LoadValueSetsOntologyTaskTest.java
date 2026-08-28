package org.metadatacenter.server.search.util;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LoadValueSetsOntologyTaskTest {

  @Test
  void attributesLogsToItsOwnClass() throws ReflectiveOperationException {
    Field logField = LoadValueSetsOntologyTask.class.getDeclaredField("log");
    logField.setAccessible(true);

    Logger logger = (Logger) logField.get(null);

    assertEquals(LoadValueSetsOntologyTask.class.getName(), logger.getName());
  }
}
