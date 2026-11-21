package com.ouroboros.documentmanagement.webapp.controller;

import java.util.List;
import java.util.stream.Collectors;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import com.ouroboros.documentmanagement.model.acl.AclEntry;
import com.ouroboros.documentmanagement.model.acl.Permission;
import com.ouroboros.documentmanagement.model.acl.PermissionSet;
import com.ouroboros.documentmanagement.service.DocumentLibraryContentFacade;
import com.ouroboros.documentmanagement.webapp.dto.AclEntryDTO;
import com.ouroboros.documentmanagement.webapp.util.UserContextHolder;
import com.ouroboros.web.ItemsWebApiResult;
import com.ouroboros.web.WebApiResult;

@RestController
@RequestMapping("/api/document-libraries/{libraryId}")
public class DocumentPermissionController extends DocumentControllerSupport {

  public DocumentPermissionController(DocumentLibraryContentFacade facade) {
    super(facade);
  }

  @Operation(
      summary = "查询文件有效权限",
      description = "查询当前用户对指定文件的有效权限集合。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回权限集合"),
      @ApiResponse(responseCode = "404", description = "文件不存在或当前用户无权访问")
  })
  @GetMapping("/documents/{documentId}/permissions")
  public WebApiResult<PermissionSet> getDocumentPermissions(
      @Parameter(description = "档案库 ID", required = true)
      @PathVariable("libraryId") String libraryId,
      @Parameter(description = "文件 ID", required = true)
      @PathVariable("documentId") String documentId) {
    String userId = UserContextHolder.requireCurrentUserId();
    PermissionSet permissions = facade.getDocumentPermissions(libraryId, documentId, userId);
    return WebApiResult.success(permissions);
  }

  @Operation(
      summary = "查询目录有效权限",
      description = "查询当前用户对指定目录的有效权限集合。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回权限集合"),
      @ApiResponse(responseCode = "404", description = "目录不存在或当前用户无权访问")
  })
  @GetMapping("/directories/{directoryId}/permissions")
  public WebApiResult<PermissionSet> getDirectoryPermissions(
      @Parameter(description = "档案库 ID", required = true)
      @PathVariable("libraryId") String libraryId,
      @Parameter(description = "目录 ID", required = true)
      @PathVariable("directoryId") String directoryId) {
    String userId = UserContextHolder.requireCurrentUserId();
    PermissionSet permissions = facade.getDirectoryPermissions(libraryId, directoryId, userId);
    return WebApiResult.success(permissions);
  }

  @Operation(
      summary = "检查文件权限",
      description = "检查当前用户是否对指定文件拥有某项权限。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回权限检查结果"),
      @ApiResponse(responseCode = "404", description = "文件不存在或当前用户无权访问")
  })
  @GetMapping("/documents/{documentId}/has-permission")
  public WebApiResult<Boolean> hasDocumentPermission(
      @Parameter(description = "档案库 ID", required = true)
      @PathVariable("libraryId") String libraryId,
      @Parameter(description = "文件 ID", required = true)
      @PathVariable("documentId") String documentId,
      @Parameter(description = "待检查的权限项", required = true)
      @RequestParam("permission") Permission permission) {
    String userId = UserContextHolder.requireCurrentUserId();
    boolean has = facade.hasDocumentPermission(libraryId, documentId, userId, permission);
    return WebApiResult.success(has);
  }

  @Operation(
      summary = "检查目录权限",
      description = "检查当前用户是否对指定目录拥有某项权限。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回权限检查结果"),
      @ApiResponse(responseCode = "404", description = "目录不存在或当前用户无权访问")
  })
  @GetMapping("/directories/{directoryId}/has-permission")
  public WebApiResult<Boolean> hasDirectoryPermission(
      @Parameter(description = "档案库 ID", required = true)
      @PathVariable("libraryId") String libraryId,
      @Parameter(description = "目录 ID", required = true)
      @PathVariable("directoryId") String directoryId,
      @Parameter(description = "待检查的权限项", required = true)
      @RequestParam("permission") Permission permission) {
    String userId = UserContextHolder.requireCurrentUserId();
    boolean has = facade.hasDirectoryPermission(libraryId, directoryId, userId, permission);
    return WebApiResult.success(has);
  }

  @Operation(
      summary = "查询文件 ACL",
      description = "查询指定文件的全部访问控制条目。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回访问控制列表"),
      @ApiResponse(responseCode = "404", description = "文件不存在或当前用户无权访问")
  })
  @GetMapping("/documents/{documentId}/acl-entries")
  public ItemsWebApiResult<AclEntryDTO> getDocumentAclEntries(
      @Parameter(description = "档案库 ID", required = true)
      @PathVariable("libraryId") String libraryId,
      @Parameter(description = "文件 ID", required = true)
      @PathVariable("documentId") String documentId) {
    String userId = UserContextHolder.requireCurrentUserId();
    List<AclEntry> entries = facade.getDocumentAclEntries(libraryId, documentId, userId);
    List<AclEntryDTO> dtos = entries.stream().map(AclEntryDTO::fromAclEntry).collect(Collectors.toList());
    return ItemsWebApiResult.ok(dtos);
  }

  @Operation(
      summary = "查询目录 ACL",
      description = "查询指定目录的全部访问控制条目。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回访问控制列表"),
      @ApiResponse(responseCode = "404", description = "目录不存在或当前用户无权访问")
  })
  @GetMapping("/directories/{directoryId}/acl-entries")
  public ItemsWebApiResult<AclEntryDTO> getDirectoryAclEntries(
      @Parameter(description = "档案库 ID", required = true)
      @PathVariable("libraryId") String libraryId,
      @Parameter(description = "目录 ID", required = true)
      @PathVariable("directoryId") String directoryId) {
    String userId = UserContextHolder.requireCurrentUserId();
    List<AclEntry> entries = facade.getDirectoryAclEntries(libraryId, directoryId, userId);
    List<AclEntryDTO> dtos = entries.stream().map(AclEntryDTO::fromAclEntry).collect(Collectors.toList());
    return ItemsWebApiResult.ok(dtos);
  }

  @Operation(
      summary = "授予文件权限",
      description = "为指定文件新增一个访问控制条目。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "文件权限授予成功"),
      @ApiResponse(responseCode = "400", description = "权限请求参数不合法"),
      @ApiResponse(responseCode = "404", description = "文件不存在或当前用户无权访问")
  })
  @PostMapping("/documents/{documentId}/acl-entries")
  public WebApiResult<AclEntryDTO> grantDocumentPermission(
      @Parameter(description = "档案库 ID", required = true)
      @PathVariable("libraryId") String libraryId,
      @Parameter(description = "文件 ID", required = true)
      @PathVariable("documentId") String documentId,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "文件权限授予请求", required = true)
      @Valid @RequestBody DocumentLibraryContentRequests.GrantPermissionRequest request) {
    String userId = UserContextHolder.requireCurrentUserId();
    AclEntry entry =
        facade.grantDocumentPermission(
            libraryId,
            documentId,
            request.principal,
            request.principalType,
            request.permissions,
            userId);
    return WebApiResult.success(AclEntryDTO.fromAclEntry(entry));
  }

  @Operation(
      summary = "授予目录权限",
      description = "为指定目录新增一个访问控制条目。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "目录权限授予成功"),
      @ApiResponse(responseCode = "400", description = "权限请求参数不合法"),
      @ApiResponse(responseCode = "404", description = "目录不存在或当前用户无权访问")
  })
  @PostMapping("/directories/{directoryId}/acl-entries")
  public WebApiResult<AclEntryDTO> grantDirectoryPermission(
      @Parameter(description = "档案库 ID", required = true)
      @PathVariable("libraryId") String libraryId,
      @Parameter(description = "目录 ID", required = true)
      @PathVariable("directoryId") String directoryId,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "目录权限授予请求", required = true)
      @Valid @RequestBody DocumentLibraryContentRequests.GrantPermissionRequest request) {
    String userId = UserContextHolder.requireCurrentUserId();
    AclEntry entry =
        facade.grantDirectoryPermission(
            libraryId,
            directoryId,
            request.principal,
            request.principalType,
            request.permissions,
            userId);
    return WebApiResult.success(AclEntryDTO.fromAclEntry(entry));
  }

  @Operation(
      summary = "更新目录权限",
      description = "按主体标识更新目录上的访问控制条目。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "目录权限更新成功"),
      @ApiResponse(responseCode = "400", description = "权限请求参数不合法"),
      @ApiResponse(responseCode = "404", description = "目录不存在或当前用户无权访问")
  })
  @PutMapping("/directories/{directoryId}/acl-entries")
  public WebApiResult<AclEntryDTO> updateDirectoryPermission(
      @Parameter(description = "档案库 ID", required = true)
      @PathVariable("libraryId") String libraryId,
      @Parameter(description = "目录 ID", required = true)
      @PathVariable("directoryId") String directoryId,
      @Parameter(description = "主体标识", required = true)
      @RequestParam("principal") String principal,
      @Parameter(description = "主体类型", required = true)
      @RequestParam("principalType") String principalType,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "目录权限更新请求", required = true)
      @Valid @RequestBody DocumentLibraryContentRequests.UpdatePermissionRequest request) {
    String userId = UserContextHolder.requireCurrentUserId();
    AclEntry entry =
        facade.grantDirectoryPermission(
            libraryId,
            directoryId,
            principal,
            principalType,
            request.permissions,
            userId);
    return WebApiResult.success(AclEntryDTO.fromAclEntry(entry));
  }

  @Operation(
      summary = "撤销文件权限",
      description = "按主体标识撤销指定文件的访问控制条目。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "文件权限撤销成功"),
      @ApiResponse(responseCode = "404", description = "文件不存在或当前用户无权访问")
  })
  @DeleteMapping("/documents/{documentId}/acl-entries")
  public WebApiResult<Void> revokeDocumentPermission(
      @Parameter(description = "档案库 ID", required = true)
      @PathVariable("libraryId") String libraryId,
      @Parameter(description = "文件 ID", required = true)
      @PathVariable("documentId") String documentId,
      @Parameter(description = "主体标识", required = true)
      @RequestParam("principal") String principal,
      @Parameter(description = "主体类型", required = true)
      @RequestParam("principalType") String principalType) {
    String userId = UserContextHolder.requireCurrentUserId();
    facade.revokeDocumentPermission(libraryId, documentId, principal, principalType, userId);
    return WebApiResult.success();
  }

  @Operation(
      summary = "撤销目录权限",
      description = "按主体标识撤销指定目录的访问控制条目。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "目录权限撤销成功"),
      @ApiResponse(responseCode = "404", description = "目录不存在或当前用户无权访问")
  })
  @DeleteMapping("/directories/{directoryId}/acl-entries")
  public WebApiResult<Void> revokeDirectoryPermission(
      @Parameter(description = "档案库 ID", required = true)
      @PathVariable("libraryId") String libraryId,
      @Parameter(description = "目录 ID", required = true)
      @PathVariable("directoryId") String directoryId,
      @Parameter(description = "主体标识", required = true)
      @RequestParam("principal") String principal,
      @Parameter(description = "主体类型", required = true)
      @RequestParam("principalType") String principalType) {
    String userId = UserContextHolder.requireCurrentUserId();
    facade.revokeDirectoryPermission(libraryId, directoryId, principal, principalType, userId);
    return WebApiResult.success();
  }
}
