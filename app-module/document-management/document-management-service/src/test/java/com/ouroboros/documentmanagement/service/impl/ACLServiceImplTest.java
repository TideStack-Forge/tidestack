package com.ouroboros.documentmanagement.service.impl;

import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.ouroboros.documentmanagement.model.DocumentLibrary;
import com.ouroboros.documentmanagement.model.DocumentLibraryImpl;
import com.ouroboros.documentmanagement.model.acl.Permission;
import com.ouroboros.documentmanagement.model.acl.ResourceType;
import com.ouroboros.documentmanagement.repository.AclEntryRepository;
import com.ouroboros.documentmanagement.repository.LibraryNodeRepository;
import com.ouroboros.documentmanagement.repository.LibraryRepository;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ACLServiceImplTest {

  @Test
  void ownerShouldAlwaysHaveAllPermissions() {
    DocumentLibrary library = DocumentLibraryImpl.builder()
        .id("lib-1")
        .name("library")
        .owner("owner")
        .addAdmin("admin")
        .build();

    ACLServiceImpl service = new ACLServiceImpl(
        libraryRepositoryReturning(library),
        emptyNodeRepository(),
        emptyAclEntryRepository());

    assertTrue(service.hasAllPermissions("lib-1", ResourceType.LIBRARY, "owner", Permission.values()));
    assertTrue(service.hasAllPermissions("lib-1", ResourceType.LIBRARY, "admin", Permission.values()));
  }

  @Test
  void nonOwnerWithoutAclShouldHaveNoPermission() {
    DocumentLibrary library = DocumentLibraryImpl.builder()
        .id("lib-1")
        .name("library")
        .owner("owner")
        .build();

    ACLServiceImpl service = new ACLServiceImpl(
        libraryRepositoryReturning(library),
        emptyNodeRepository(),
        emptyAclEntryRepository());

    assertFalse(service.hasPermission("lib-1", ResourceType.LIBRARY, "other-user", Permission.VIEW));
  }

  private LibraryRepository libraryRepositoryReturning(DocumentLibrary library) {
    return (LibraryRepository) Proxy.newProxyInstance(
        LibraryRepository.class.getClassLoader(),
        new Class<?>[]{LibraryRepository.class},
        (proxy, method, args) -> {
          if ("findById".equals(method.getName())) {
            return Optional.of(library);
          }
          throw new UnsupportedOperationException(method.getName());
        });
  }

  private LibraryNodeRepository emptyNodeRepository() {
    return (LibraryNodeRepository) Proxy.newProxyInstance(
        LibraryNodeRepository.class.getClassLoader(),
        new Class<?>[]{LibraryNodeRepository.class},
        (proxy, method, args) -> {
          if ("findRootDirectory".equals(method.getName())) {
            return Optional.empty();
          }
          throw new UnsupportedOperationException(method.getName());
        });
  }

  private AclEntryRepository emptyAclEntryRepository() {
    return (AclEntryRepository) Proxy.newProxyInstance(
        AclEntryRepository.class.getClassLoader(),
        new Class<?>[]{AclEntryRepository.class},
        (proxy, method, args) -> {
          if ("findByResource".equals(method.getName()) || "findByPrincipal".equals(method.getName())) {
            return Collections.emptyList();
          }
          if ("findById".equals(method.getName()) || "findByResourceAndPrincipal".equals(method.getName())) {
            return Optional.empty();
          }
          if (List.class.equals(method.getReturnType())) {
            return Collections.emptyList();
          }
          throw new UnsupportedOperationException(method.getName());
        });
  }
}
