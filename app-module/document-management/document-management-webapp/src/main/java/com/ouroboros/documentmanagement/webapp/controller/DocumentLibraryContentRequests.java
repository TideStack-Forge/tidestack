package com.ouroboros.documentmanagement.webapp.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import com.ouroboros.documentmanagement.model.acl.PermissionSet;

final class DocumentLibraryContentRequests {

  private DocumentLibraryContentRequests() {
  }

  static class CreateDirectoryRequest {
    public String parentDirectoryId;

    @NotBlank
    public String directoryName;
  }

  static class MoveNodeRequest {
    @NotBlank
    public String targetDirectoryId;
  }

  static class MoveDirectoryRequest {
    @NotBlank
    public String targetDirectoryId;
  }

  static class RenameRequest {
    @NotBlank
    public String newName;
  }

  static class RenameDirectoryRequest {
    @NotBlank
    public String newName;
  }

  static class GrantPermissionRequest {
    @NotBlank
    public String principal;

    @NotBlank
    public String principalType;

    @NotNull
    public PermissionSet permissions;
  }

  static class UpdatePermissionRequest {
    @NotNull
    public PermissionSet permissions;
  }
}
