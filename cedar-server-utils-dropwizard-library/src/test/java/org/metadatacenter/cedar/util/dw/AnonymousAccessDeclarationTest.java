package org.metadatacenter.cedar.util.dw;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves that only a method declaring {@link AnonymousAccess} can obtain an anonymous request context.
 *
 * <p>The check itself is exercised directly rather than through a booted resource: building a real context needs
 * a servlet request, and what is under test is which callers the guard admits.
 */
class AnonymousAccessDeclarationTest {

  private static void check(StackTraceElement caller) throws Exception {
    Method m = CedarMicroserviceResource.class.getDeclaredMethod("requireDeclaredAnonymous", StackTraceElement.class);
    m.setAccessible(true);
    try {
      m.invoke(null, caller);
    } catch (InvocationTargetException e) {
      throw (Exception) e.getCause();
    }
  }

  private static StackTraceElement callerOf(Class<?> type, String method) {
    return new StackTraceElement(type.getName(), method, type.getSimpleName() + ".java", 1);
  }

  static class Annotated {
    @AnonymousAccess
    void open() {
    }
  }

  static class NotAnnotated {
    void closed() {
    }
  }

  static class PartiallyAnnotated {
    @AnonymousAccess
    void overloaded() {
    }

    void overloaded(String argument) {
    }
  }

  @Test
  @DisplayName("An annotated caller is admitted")
  void annotatedCallerIsAdmitted() {
    assertDoesNotThrow(() -> check(callerOf(Annotated.class, "open")));
  }

  @Test
  @DisplayName("An unannotated caller is refused, and the message names it")
  void unannotatedCallerIsRefused() {
    Exception e = assertThrows(Exception.class, () -> check(callerOf(NotAnnotated.class, "closed")));
    assertInstanceOf(IllegalStateException.class, e);
    assertTrue(e.getMessage().contains("closed"), "the message should name the offending method: " + e.getMessage());
  }

  @Test
  @DisplayName("An overload cannot inherit the exemption from its annotated sibling")
  void overloadDoesNotInheritTheExemption() {
    assertThrows(IllegalStateException.class, () -> check(callerOf(PartiallyAnnotated.class, "overloaded")));
  }

  @Test
  @DisplayName("A caller whose class cannot be resolved is refused rather than admitted")
  void unresolvableCallerIsRefused() {
    StackTraceElement ghost = new StackTraceElement("org.metadatacenter.NoSuchClass", "whatever", "None.java", 1);
    assertThrows(IllegalStateException.class, () -> check(ghost));
  }

  @Test
  @DisplayName("The annotation is retained at runtime, which the guard depends on")
  void annotationIsRetainedAtRuntime() throws Exception {
    assertEquals(true, Annotated.class.getDeclaredMethod("open").isAnnotationPresent(AnonymousAccess.class));
  }
}
