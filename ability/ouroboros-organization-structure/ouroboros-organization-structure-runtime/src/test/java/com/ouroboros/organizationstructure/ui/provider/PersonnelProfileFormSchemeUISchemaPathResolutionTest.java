package com.ouroboros.organizationstructure.ui.provider;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;

import org.junit.jupiter.api.Test;

import com.ouroboros.uischema.JarUISchemaProvider;

class PersonnelProfileFormSchemeUISchemaPathResolutionTest {

  @Test
  void shouldOnlyExposePersonnelProfileFormSchemeDevPageFromClasspath() {
    JarUISchemaProvider uiSchemaProvider = new JarUISchemaProvider();

    assertFalse(uiSchemaProvider.getUISchema("/dev/{appName}/organization-management/personnel-profile-template").isPresent());
    assertFalse(uiSchemaProvider.getUISchema("/dev/{appName}/organization-management/personnel-profile-template/edit-layout").isPresent());
    assertFalse(uiSchemaProvider.getUISchema("/dev/{appName}/organization-management/personnel-profile-template/display-layout").isPresent());

    assertTrue(uiSchemaProvider.getUISchema("/dev/{appName}/organization-management/personnel-profile-form-scheme").isPresent());

    assertMissingClasspathResource(
        "META-INF/ouroboros/metadata/dev/{appName}/organization-management/personnel-profile-template.ui-model.json"
    );
    assertPresentClasspathResource(
        "META-INF/ouroboros/metadata/dev/{appName}/organization-management/personnel-profile-form-scheme.ui-model.json"
    );
    assertMissingClasspathResource(
        "META-INF/ouroboros/metadata/Organization.PersonnelProfileTemplate.dev-menu.json"
    );
    assertPresentClasspathResource(
        "META-INF/ouroboros/metadata/Organization.PersonnelProfileFormScheme.dev-menu.json"
    );
  }

  private void assertMissingClasspathResource(String path) {
    InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(path);
    assertNull(stream);
  }

  private void assertPresentClasspathResource(String path) {
    InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(path);
    assertTrue(stream != null);
  }
}
