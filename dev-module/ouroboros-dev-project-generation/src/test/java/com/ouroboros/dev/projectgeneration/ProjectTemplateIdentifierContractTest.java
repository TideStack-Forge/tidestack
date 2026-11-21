package com.ouroboros.dev.projectgeneration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.ouroboros.dev.projectgeneration.catalog.ProjectTemplateIdentifierContracts;

class ProjectTemplateIdentifierContractTest {

  @Test
  void shouldAcceptOfficialTemplateAndSafeProjectRootIdentifiers() {
    assertDoesNotThrow(() -> ProjectTemplateIdentifierContracts.requireTemplateId(
        "frontend.micro-app.react-webpack",
        "templateId"
    ));
    assertDoesNotThrow(() -> ProjectTemplateIdentifierContracts.requirePathSegment(
        "customer_portal-1",
        "projectName"
    ));
  }

  @Test
  void shouldRejectPathSeparatorsControlCharactersAndReservedNames() {
    assertThrows(IllegalArgumentException.class, () -> ProjectTemplateIdentifierContracts.requireTemplateId(
        "frontend/evil",
        "templateId"
    ));
    assertThrows(IllegalArgumentException.class, () -> ProjectTemplateIdentifierContracts.requirePathSegment(
        "CON",
        "projectName"
    ));
    assertThrows(IllegalArgumentException.class, () -> ProjectTemplateIdentifierContracts.requireDisplayText(
        "bad\nname",
        "displayName",
        120
    ));
  }
}
