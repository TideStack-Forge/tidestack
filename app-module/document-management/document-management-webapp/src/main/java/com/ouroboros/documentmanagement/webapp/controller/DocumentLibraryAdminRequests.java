package com.ouroboros.documentmanagement.webapp.controller;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import com.ouroboros.documentmanagement.model.acl.PermissionSet;

final class DocumentLibraryAdminRequests {

  private DocumentLibraryAdminRequests() {}

  static class TransferOwnershipRequest {
    @NotBlank public String newOwnerId;
    public boolean keepOriginalOwnerAsAdmin = false;
  }

  static class BatchAdminRequest {
    @NotNull public List<String> adminUserIds;
  }

  static class SetOwnerRequest {
    @NotBlank public String newOwnerId;
  }

  static class UpdateQuotaRequest {
    public Long maxSizeLimit;
    public Long maxFileCountLimit;
  }

  static class BatchUpdateQuotaRequest {
    @NotNull public List<String> libraryIds;
    public Long maxSizeLimit;
    public Long maxFileCountLimit;
  }

  static class BatchLibraryRequest {
    @NotNull public List<String> libraryIds;
  }

  static class GrantPermissionRequest {
    @NotBlank public String principal;
    @NotBlank public String principalType;
    @NotNull public PermissionSet permissions;
  }
}
