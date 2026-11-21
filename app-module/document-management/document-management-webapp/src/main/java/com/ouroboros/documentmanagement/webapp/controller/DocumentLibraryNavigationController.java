package com.ouroboros.documentmanagement.webapp.controller;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import com.ouroboros.documentmanagement.model.DirectoryNode;
import com.ouroboros.documentmanagement.model.DocumentNode;
import com.ouroboros.documentmanagement.model.LibraryNode;
import com.ouroboros.documentmanagement.service.DocumentLibraryContentFacade;
import com.ouroboros.documentmanagement.webapp.dto.DirectoryBrowseResultDTO;
import com.ouroboros.documentmanagement.webapp.dto.LibraryTreeNodeDTO;
import com.ouroboros.documentmanagement.webapp.dto.NodeItemDTO;
import com.ouroboros.documentmanagement.webapp.dto.PermissionsDTO;
import com.ouroboros.documentmanagement.webapp.util.UserContextHolder;
import com.ouroboros.web.ItemsWebApiResult;
import com.ouroboros.web.WebApiResult;

@RestController
@RequestMapping("/api/document-libraries/{libraryId}")
public class DocumentLibraryNavigationController extends DocumentControllerSupport {

  public DocumentLibraryNavigationController(DocumentLibraryContentFacade facade) {
    super(facade);
  }

  @Operation(
      summary = "查询档案库目录树",
      description = "查询档案库根目录或指定目录的目录树数据。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回目录树"),
      @ApiResponse(responseCode = "404", description = "目录树不存在或当前用户无权访问")
  })
  @GetMapping("/directories")
  public ItemsWebApiResult<LibraryTreeNodeDTO> getLibraryDirectories(
      @Parameter(description = "档案库 ID", required = true)
      @PathVariable("libraryId") String libraryId,
      @Parameter(description = "目录 ID，为空时查询根目录")
      @RequestParam(value = "directoryId", defaultValue = "") String directoryId,
      @Parameter(description = "是否构建完整树结构")
      @RequestParam(value = "buildTree", defaultValue = "true") Boolean buildTree) {
    if (directoryId.isEmpty()) {
      String userId = UserContextHolder.requireCurrentUserId();
      if (buildTree) {
        return facade
            .getDirectoryTree(libraryId, userId)
            .map(DirectoryNode::getChildren)
            .map(this::mapToTreeNodes)
            .map(ItemsWebApiResult::ok)
            .orElse(itemsNotFound("目录树不存在或无权限访问"));
      }
      return facade
          .getRootDirectory(libraryId, userId)
          .map(dir -> listSubdirectories(libraryId, dir.getId(), false))
          .orElse(itemsNotFound("根目录不存在或无访问权限"));
    }
    return listSubdirectories(libraryId, directoryId, buildTree);
  }

  @Operation(
      summary = "浏览目录内容",
      description = "浏览指定目录下的子目录、文件、路径和当前用户权限信息。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回目录浏览结果"),
      @ApiResponse(responseCode = "404", description = "目录不存在或当前用户无权访问")
  })
  @GetMapping("/directories/{directoryId}/browse")
  public WebApiResult<DirectoryBrowseResultDTO> browseDirectory(
      @Parameter(description = "档案库 ID", required = true)
      @PathVariable("libraryId") String libraryId,
      @Parameter(description = "目录 ID", required = true)
      @PathVariable("directoryId") String directoryId,
      @Parameter(description = "是否包含子目录")
      @RequestParam(value = "includeDir", defaultValue = "false") Boolean includeDir,
      @Parameter(description = "排序字段")
      @RequestParam(value = "orderBy", required = false) String orderBy,
      @Parameter(description = "排序方向，支持 asc 或 desc")
      @RequestParam(value = "orderDir", required = false) String orderDir) {

    String userId = UserContextHolder.requireCurrentUserId();

    List<? extends LibraryNode> children = includeDir
        ? facade.browseDirectory(libraryId, directoryId, userId)
        : facade.listDocuments(libraryId, directoryId, userId);

    List<LibraryNode> path = facade.getNodePath(libraryId, directoryId, userId);

    PermissionsDTO permissions = buildPermissions(libraryId, directoryId, userId);

    List<NodeItemDTO> items = children.stream()
        .map(node -> {
          PermissionsDTO nodePermissions = buildNodePermissions(libraryId, node.getId(), userId, node);
          String createdByDisplayName = getPrincipalDisplayName(node.getCreatedBy());
          String updatedByDisplayName = node.getUpdatedBy() != null
              ? getPrincipalDisplayName(node.getUpdatedBy())
              : null;

          return new NodeItemDTO()
              .setId(node.getId())
              .setParentId(node.getParentId())
              .setSize(node instanceof DocumentNode ? ((DocumentNode) node).getSize() : null)
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
              .setPermissions(nodePermissions);
        })
        .collect(Collectors.toList());

    if (orderBy != null && !orderBy.isEmpty()) {
      sortItems(items, orderBy, orderDir);
    }

    DirectoryBrowseResultDTO result = new DirectoryBrowseResultDTO();
    result.setLibraryId(libraryId);
    result.setDirectoryId(directoryId);
    result.setDirectoryName(path.isEmpty() ? "异常数据" : path.get(path.size() - 1).getName());
    result.setPermissions(permissions);
    result.setPath(path);
    result.setItems(items);

    return WebApiResult.success(result);
  }

  @Operation(
      summary = "查询子目录列表",
      description = "查询指定目录下的子目录列表，可选返回完整树结构。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回子目录列表"),
      @ApiResponse(responseCode = "404", description = "目录不存在或当前用户无权访问")
  })
  @GetMapping({"/directories/{directoryId}/subdirectories", "/directories/{directoryId}/children"})
  public ItemsWebApiResult<LibraryTreeNodeDTO> listSubdirectories(
      @Parameter(description = "档案库 ID", required = true)
      @PathVariable("libraryId") String libraryId,
      @Parameter(description = "目录 ID", required = true)
      @PathVariable("directoryId") String directoryId,
      @Parameter(description = "是否构建完整树结构")
      @RequestParam(value = "buildTree", defaultValue = "true") Boolean buildTree) {
    String userId = UserContextHolder.requireCurrentUserId();
    if (buildTree) {
      return facade.getDirectoryTree(libraryId, userId)
          .map(tree -> findDirectoryChildren(tree, directoryId))
          .map(this::mapToTreeNodes)
          .map(ItemsWebApiResult::ok)
          .orElseGet(() -> ItemsWebApiResult.ok(Collections.emptyList()));
    }
    List<LibraryTreeNodeDTO> subdirs = facade.listSubdirectories(libraryId, directoryId, userId)
        .stream()
        .map(LibraryTreeNodeDTO::fromLibraryNode)
        .collect(Collectors.toList());
    return ItemsWebApiResult.ok(subdirs);
  }

  @Operation(
      summary = "查询目录文件列表",
      description = "查询指定目录下的直接文件列表。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回文件列表"),
      @ApiResponse(responseCode = "404", description = "目录不存在或当前用户无权访问")
  })
  @GetMapping("/directories/{directoryId}/documents")
  public ItemsWebApiResult<DocumentNode> listDocuments(
      @Parameter(description = "档案库 ID", required = true)
      @PathVariable("libraryId") String libraryId,
      @Parameter(description = "目录 ID", required = true)
      @PathVariable("directoryId") String directoryId) {
    String userId = UserContextHolder.requireCurrentUserId();
    List<DocumentNode> documents = facade.listDocuments(libraryId, directoryId, userId);
    return ItemsWebApiResult.ok(documents);
  }

  @Operation(
      summary = "查询节点路径",
      description = "查询从根目录到指定节点的完整路径，用于面包屑展示。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回节点路径"),
      @ApiResponse(responseCode = "404", description = "节点不存在或当前用户无权访问")
  })
  @GetMapping("/nodes/{nodeId}/path")
  public ItemsWebApiResult<LibraryNode> getNodePath(
      @Parameter(description = "档案库 ID", required = true)
      @PathVariable("libraryId") String libraryId,
      @Parameter(description = "节点 ID", required = true)
      @PathVariable("nodeId") String nodeId) {
    String userId = UserContextHolder.requireCurrentUserId();
    List<LibraryNode> path = facade.getNodePath(libraryId, nodeId, userId);
    return ItemsWebApiResult.ok(path);
  }

  private List<DirectoryNode> findDirectoryChildren(DirectoryNode root, String directoryId) {
    return findDirectoryNode(root, directoryId)
        .map(node -> castDirectoryChildren(node.getChildren()))
        .orElse(Collections.emptyList());
  }

  private Optional<DirectoryNode> findDirectoryNode(DirectoryNode current, String directoryId) {
    if (directoryId.equals(current.getId())) {
      return Optional.of(current);
    }
    for (DirectoryNode child : castDirectoryChildren(current.getChildren())) {
      Optional<DirectoryNode> matched = findDirectoryNode(child, directoryId);
      if (matched.isPresent()) {
        return matched;
      }
    }
    return Optional.empty();
  }

  private List<DirectoryNode> castDirectoryChildren(List<? extends LibraryNode> children) {
    return children.stream()
        .filter(DirectoryNode.class::isInstance)
        .map(DirectoryNode.class::cast)
        .collect(Collectors.toList());
  }
}
