package com.ouroboros.documentmanagement.webapp.controller;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.ouroboros.documentmanagement.model.DocumentNode;
import com.ouroboros.documentmanagement.model.LibraryNode;
import com.ouroboros.documentmanagement.model.acl.Permission;
import com.ouroboros.documentmanagement.model.acl.PermissionSet;
import com.ouroboros.documentmanagement.model.acl.PrincipalResolver;
import com.ouroboros.documentmanagement.model.acl.PrincipalType;
import com.ouroboros.documentmanagement.model.acl.PrincipalTypeProvider;
import com.ouroboros.documentmanagement.service.DocumentLibraryContentFacade;
import com.ouroboros.documentmanagement.webapp.dto.LibraryTreeNodeDTO;
import com.ouroboros.documentmanagement.webapp.dto.NodeItemDTO;
import com.ouroboros.documentmanagement.webapp.dto.PermissionsDTO;
import com.ouroboros.web.ItemsWebApiResult;
import com.ouroboros.web.WebApiResult;

abstract class DocumentControllerSupport {

  protected final DocumentLibraryContentFacade facade;

  protected DocumentControllerSupport(DocumentLibraryContentFacade facade) {
    this.facade = facade;
  }

  protected List<LibraryTreeNodeDTO> mapToTreeNodes(List<? extends LibraryNode> nodes) {
    return nodes.stream()
        .map(LibraryTreeNodeDTO::fromLibraryNode)
        .collect(Collectors.toList());
  }

  protected PermissionsDTO buildPermissions(String libraryId, String directoryId, String userId) {
    PermissionsDTO dto = new PermissionsDTO();

    PermissionSet permissions = facade.getDirectoryPermissions(libraryId, directoryId, userId);

    dto.setCanManage(permissions.has(Permission.MANAGE));
    dto.setCanView(permissions.has(Permission.VIEW));
    dto.setCanDownload(permissions.has(Permission.DOWNLOAD));
    dto.setCanCreateFile(permissions.has(Permission.CREATE_FILE));
    dto.setCanCreateDir(permissions.has(Permission.CREATE_DIR));
    dto.setCanRenameMove(permissions.has(Permission.RENAME_MOVE));
    dto.setCanDeleteFile(permissions.has(Permission.DELETE_FILE));
    dto.setCanDeleteDir(permissions.has(Permission.DELETE_DIR));

    return dto;
  }

  protected PermissionsDTO buildNodePermissions(
      String libraryId, String nodeId, String userId, LibraryNode node) {
    PermissionsDTO dto = new PermissionsDTO();

    PermissionSet permissions;
    if (node instanceof DocumentNode) {
      permissions = facade.getDocumentPermissions(libraryId, nodeId, userId);
    } else {
      permissions = facade.getDirectoryPermissions(libraryId, nodeId, userId);
    }

    dto.setCanManage(permissions.has(Permission.MANAGE));
    dto.setCanView(permissions.has(Permission.VIEW));
    dto.setCanDownload(permissions.has(Permission.DOWNLOAD));
    dto.setCanCreateFile(permissions.has(Permission.CREATE_FILE));
    dto.setCanCreateDir(permissions.has(Permission.CREATE_DIR));
    dto.setCanRenameMove(permissions.has(Permission.RENAME_MOVE));
    dto.setCanDeleteFile(permissions.has(Permission.DELETE_FILE));
    dto.setCanDeleteDir(permissions.has(Permission.DELETE_DIR));

    return dto;
  }

  protected NodeItemDTO convertToNodeItemDTO(LibraryNode node, String libraryId, String userId) {
    PermissionsDTO permissions = buildNodePermissions(libraryId, node.getId(), userId, node);

    String createdByDisplayName = getPrincipalDisplayName(node.getCreatedBy());
    String updatedByDisplayName = node.getUpdatedBy() != null
        ? getPrincipalDisplayName(node.getUpdatedBy())
        : null;

    NodeItemDTO dto = new NodeItemDTO()
        .setId(node.getId())
        .setParentId(node.getParentId())
        .setType(node.getType())
        .setName(node.getName())
        .setDescription(node.getDescription())
        .setLibraryId(node.getLibraryId())
        .setCreatedBy(node.getCreatedBy())
        .setCreatedAt(node.getCreatedAt())
        .setUpdatedBy(node.getUpdatedBy())
        .setUpdatedAt(node.getUpdatedAt())
        .setCreatedByDisplayName(createdByDisplayName)
        .setUpdatedByDisplayName(updatedByDisplayName)
        .setPermissions(permissions);

    if (node instanceof DocumentNode) {
      dto.setSize(((DocumentNode) node).getSize());
    }

    return dto;
  }

  protected String getPrincipalDisplayName(String principal) {
    if (principal == null || principal.isEmpty()) {
      return principal;
    }

    Optional<PrincipalType> principalType = PrincipalTypeProvider.get("USER");
    if (principalType.isPresent()) {
      PrincipalResolver resolver = principalType.get().getResolver();
      return resolver.getDisplayName(principal);
    }

    return principal;
  }

  protected void sortItems(List<NodeItemDTO> items, String orderBy, String orderDir) {
    boolean ascending = !"desc".equalsIgnoreCase(orderDir);

    java.util.Comparator<NodeItemDTO> comparator = null;

    switch (orderBy) {
      case "name":
        comparator = java.util.Comparator.comparing(
            NodeItemDTO::getName,
            java.util.Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
        break;
      case "size":
        comparator = java.util.Comparator.comparing(
            NodeItemDTO::getSize,
            java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()));
        break;
      case "createdByDisplayName":
        comparator = java.util.Comparator.comparing(
            NodeItemDTO::getCreatedByDisplayName,
            java.util.Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
        break;
      case "createdAt":
        comparator = java.util.Comparator.comparing(
            NodeItemDTO::getCreatedAt,
            java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()));
        break;
      default:
        return;
    }

    if (!ascending) {
      comparator = comparator.reversed();
    }

    items.sort(comparator);
  }

  protected <T> WebApiResult<T> notFound(String message) {
    return WebApiResult.error(404, message);
  }

  protected <T> ItemsWebApiResult<T> itemsNotFound(String message) {
    return ItemsWebApiResult.fail(404, message);
  }
}
