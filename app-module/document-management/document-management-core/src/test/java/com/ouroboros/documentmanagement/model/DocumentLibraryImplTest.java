package com.ouroboros.documentmanagement.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class DocumentLibraryImplTest {

  @Test
  void builderFromDocumentLibraryShouldAllowAddingAdmin() {
    DocumentLibrary origin = DocumentLibraryImpl.builder()
        .id("lib-1")
        .name("library")
        .owner("owner")
        .addAdmin("admin-a")
        .build();

    DocumentLibrary.Builder copiedBuilder = DocumentLibraryImpl.builder(origin);

    assertDoesNotThrow(() -> copiedBuilder.addAdmin("admin-b"));
    DocumentLibrary copied = copiedBuilder.build();
    assertEquals(2, copied.getAdminIds().size());
  }
}
