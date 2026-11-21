package com.ouroboros.logicflow.dev.scriptintelligence;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.Test;

public class ClassGraphJavaTypeMetadataIndexTest {

  @Test
  public void load_allowlistedTypes_onlyIndexesConfiguredClasses() throws Exception {
    var index = createIndex(Arrays.asList("java.util.UUID", "com.ouroboros.util.Dates"));

    var uuid = findBySimpleName(index, "UUID");
    assertTrue(uuid.isPresent());
    assertEquals("java.util.UUID", invokeGetter(uuid.get(), "getQualifiedName"));
    assertTrue(containsMember((List<?>) invokeGetter(uuid.get(), "getStaticMethods"), "randomUUID"));
    assertFalse(findByQualifiedName(index, "java.io.File").isPresent());
  }

  @Test
  public void load_allowlistedPackages_indexesMatchingTypesOnly() throws Exception {
    var index = createIndex(Arrays.asList("java.util"));

    assertTrue(findByQualifiedName(index, "java.util.UUID").isPresent());
    assertFalse(findByQualifiedName(index, "java.io.File").isPresent());
  }

  private Object createIndex(List<String> allowlistEntries) throws Exception {
    var indexClass = Class.forName("com.ouroboros.script.intelligence.impl.ClassGraphJavaTypeMetadataIndex");
    return indexClass.getConstructor(List.class).newInstance(allowlistEntries);
  }

  @SuppressWarnings("unchecked")
  private Optional<Object> findBySimpleName(Object index, String simpleName) throws Exception {
    return (Optional<Object>) index.getClass().getMethod("findBySimpleName", String.class).invoke(index, simpleName);
  }

  @SuppressWarnings("unchecked")
  private Optional<Object> findByQualifiedName(Object index, String qualifiedName) throws Exception {
    return (Optional<Object>) index.getClass().getMethod("findByQualifiedName", String.class).invoke(index, qualifiedName);
  }

  private boolean containsMember(List<?> members, String memberName) throws Exception {
    for (Object member : members) {
      if (memberName.equals(String.valueOf(invokeGetter(member, "getName")))) {
        return true;
      }
    }
    return false;
  }

  private Object invokeGetter(Object target, String methodName) throws Exception {
    return target.getClass().getMethod(methodName).invoke(target);
  }
}
