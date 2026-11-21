package com.ouroboros.resource.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.resource.model.ResourceScope;

class ResourcePathUtilsTest {

  @Test
  void stripsScopePrefixAndBuildsMetaFilePath() {
    assertEquals("templates/invoice.docx",
        ResourcePathUtils.toResourceKey("frontend/templates/invoice.docx"));
    assertEquals("frontend/templates/invoice.docx.resource.json",
        ResourcePathUtils.toSidecarPath(ResourceScope.FRONTEND, "templates/invoice.docx"));
  }

  @Test
  void buildsScopeRelativeResourcePath() {
    assertEquals("backend/sql/init.sql",
        ResourcePathUtils.toScopedResourcePath(ResourceScope.BACKEND, "sql/init.sql"));
  }

  @Test
  void rejectsTraversalSegments() {
    assertThrows(BusinessException.class, () -> ResourcePathUtils.normalize("../secrets.txt"));
    assertThrows(BusinessException.class,
        () -> ResourcePathUtils.toScopedResourcePath(ResourceScope.FRONTEND, "images/../../secrets.txt"));
  }
}
