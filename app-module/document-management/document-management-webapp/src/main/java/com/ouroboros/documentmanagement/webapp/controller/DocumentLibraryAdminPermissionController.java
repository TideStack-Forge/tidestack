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
import com.ouroboros.documentmanagement.service.DocumentLibraryAdminFacade;
import com.ouroboros.documentmanagement.service.DocumentLibraryAdminFacade.GlobalStatistics;
import com.ouroboros.documentmanagement.service.DocumentLibraryService.LibraryStatistics;
import com.ouroboros.documentmanagement.webapp.dto.DocumentLibraryAdminDTO;
import com.ouroboros.documentmanagement.webapp.util.UserContextHolder;
import com.ouroboros.web.ItemsWebApiResult;
import com.ouroboros.web.WebApiResult;

/**
 * 档案库后台管理权限与统计 REST API Controller
 */
@RestController
@RequestMapping("/api/document-management/admin/libraries")
public class DocumentLibraryAdminPermissionController extends DocumentAdminControllerSupport {

  public DocumentLibraryAdminPermissionController(DocumentLibraryAdminFacade facade) {
    super(facade);
  }

  @Operation(
      summary = "后台查询档案库 ACL",
      description = "查询指定档案库当前的全部访问控制条目。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回访问控制列表"),
      @ApiResponse(responseCode = "404", description = "档案库不存在")
  })
  @GetMapping("/{libraryId}/permissions")
  public ItemsWebApiResult<AclEntry> getLibraryPermissions(
      @Parameter(description = "档案库 ID", required = true)
      @PathVariable("libraryId") String libraryId) {
    List<AclEntry> permissions = facade.getLibraryPermissions(libraryId);
    return ItemsWebApiResult.ok(permissions);
  }

  @Operation(
      summary = "后台授予档案库权限",
      description = "为指定档案库新增一个访问控制条目。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "访问权限授予成功"),
      @ApiResponse(responseCode = "400", description = "权限请求参数不合法"),
      @ApiResponse(responseCode = "404", description = "档案库不存在")
  })
  @PostMapping("/{libraryId}/permissions")
  public WebApiResult<AclEntry> grantLibraryPermission(
      @Parameter(description = "档案库 ID", required = true)
      @PathVariable("libraryId") String libraryId,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "档案库权限授予请求", required = true)
      @Valid @RequestBody DocumentLibraryAdminRequests.GrantPermissionRequest request) {
    String operatorId = UserContextHolder.requireCurrentUserId();
    AclEntry entry =
        facade.grantLibraryPermission(
            libraryId, request.principal, request.principalType, request.permissions, operatorId);
    return WebApiResult.success(entry);
  }

  @Operation(
      summary = "后台更新档案库权限",
      description = "更新指定档案库中已有主体的访问权限。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "访问权限更新成功"),
      @ApiResponse(responseCode = "400", description = "权限请求参数不合法"),
      @ApiResponse(responseCode = "404", description = "档案库不存在")
  })
  @PutMapping("/{libraryId}/permissions")
  public WebApiResult<AclEntry> updateLibraryPermission(
      @Parameter(description = "档案库 ID", required = true)
      @PathVariable("libraryId") String libraryId,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "档案库权限更新请求", required = true)
      @Valid @RequestBody DocumentLibraryAdminRequests.GrantPermissionRequest request) {
    String operatorId = UserContextHolder.requireCurrentUserId();
    AclEntry entry =
        facade.updateLibraryPermission(
            libraryId, request.principal, request.principalType, request.permissions, operatorId);
    return WebApiResult.success(entry);
  }

  @Operation(
      summary = "后台撤销档案库权限",
      description = "按主体标识和主体类型撤销档案库访问权限。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "访问权限撤销成功"),
      @ApiResponse(responseCode = "404", description = "档案库不存在")
  })
  @DeleteMapping("/{libraryId}/permissions")
  public WebApiResult<Void> revokeLibraryPermission(
      @Parameter(description = "档案库 ID", required = true)
      @PathVariable("libraryId") String libraryId,
      @Parameter(description = "主体标识", required = true)
      @RequestParam("principal") String principal,
      @Parameter(description = "主体类型", required = true)
      @RequestParam("principalType") String principalType) {
    String operatorId = UserContextHolder.requireCurrentUserId();
    facade.revokeLibraryPermission(libraryId, principal, principalType, operatorId);
    return WebApiResult.success();
  }

  @Operation(
      summary = "后台批量授予档案库权限",
      description = "批量为档案库新增多个访问控制条目。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "访问权限批量授予成功"),
      @ApiResponse(responseCode = "400", description = "访问控制条目列表参数不合法")
  })
  @PostMapping("/{libraryId}/permissions/batch")
  public ItemsWebApiResult<AclEntry> batchGrantLibraryPermissions(
      @Parameter(description = "档案库 ID", required = true)
      @PathVariable("libraryId") String libraryId,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "访问控制条目列表", required = true)
      @Valid @RequestBody List<AclEntry> aclEntries) {
    String operatorId = UserContextHolder.requireCurrentUserId();
    List<AclEntry> entries = facade.batchGrantLibraryPermissions(libraryId, aclEntries, operatorId);
    return ItemsWebApiResult.ok(entries);
  }

  @Operation(
      summary = "后台批量撤销档案库权限",
      description = "按主体标识批量撤销档案库访问权限。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "访问权限批量撤销成功"),
      @ApiResponse(responseCode = "400", description = "主体标识列表参数不合法")
  })
  @DeleteMapping("/{libraryId}/permissions/batch")
  public WebApiResult<Void> batchRevokeLibraryPermissions(
      @Parameter(description = "档案库 ID", required = true)
      @PathVariable("libraryId") String libraryId,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "待撤销权限的主体标识列表", required = true)
      @Valid @RequestBody List<String> principals) {
    String operatorId = UserContextHolder.requireCurrentUserId();
    facade.batchRevokeLibraryPermissions(libraryId, principals, operatorId);
    return WebApiResult.success();
  }

  @Operation(
      summary = "后台清空档案库权限",
      description = "清空指定档案库的所有访问控制条目。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "档案库权限清空成功"),
      @ApiResponse(responseCode = "404", description = "档案库不存在")
  })
  @DeleteMapping("/{libraryId}/permissions/clear")
  public WebApiResult<Void> clearLibraryPermissions(
      @Parameter(description = "档案库 ID", required = true)
      @PathVariable("libraryId") String libraryId) {
    String operatorId = UserContextHolder.requireCurrentUserId();
    facade.clearLibraryPermissions(libraryId, operatorId);
    return WebApiResult.success();
  }

  @Operation(
      summary = "查询用户全部档案库权限",
      description = "查询指定用户在所有档案库中的访问控制条目。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回用户权限列表")
  })
  @GetMapping("/users/{userId}/permissions")
  public ItemsWebApiResult<AclEntry> getUserPermissions(
      @Parameter(description = "用户 ID", required = true)
      @PathVariable("userId") String userId) {
    List<AclEntry> permissions = facade.getUserPermissions(userId);
    return ItemsWebApiResult.ok(permissions);
  }

  @Operation(
      summary = "撤销用户全部档案库权限",
      description = "撤销指定用户在所有档案库中的全部访问权限。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "用户权限全部撤销成功")
  })
  @DeleteMapping("/users/{userId}/permissions")
  public WebApiResult<Void> revokeAllUserPermissions(
      @Parameter(description = "用户 ID", required = true)
      @PathVariable("userId") String userId) {
    String operatorId = UserContextHolder.requireCurrentUserId();
    facade.revokeAllUserPermissions(userId, operatorId);
    return WebApiResult.success();
  }

  @Operation(
      summary = "后台获取档案库统计信息",
      description = "以后台管理视角获取指定档案库的统计信息。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回档案库统计信息"),
      @ApiResponse(responseCode = "404", description = "档案库不存在")
  })
  @GetMapping("/{libraryId}/statistics")
  public WebApiResult<LibraryStatistics> getLibraryStatistics(
      @Parameter(description = "档案库 ID", required = true)
      @PathVariable("libraryId") String libraryId) {
    LibraryStatistics statistics = facade.getLibraryStatistics(libraryId);
    return WebApiResult.success(statistics);
  }

  @Operation(
      summary = "查询全局档案库统计",
      description = "查询后台管理维度下所有档案库的全局汇总统计信息。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回全局统计信息")
  })
  @GetMapping("/statistics/global")
  public WebApiResult<GlobalStatistics> getGlobalStatistics() {
    GlobalStatistics statistics = facade.getGlobalStatistics();
    return WebApiResult.success(statistics);
  }

  @Operation(
      summary = "查询配额超限档案库",
      description = "查询当前已超出配额限制的档案库列表。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回档案库列表")
  })
  @GetMapping("/quota-exceeded")
  public ItemsWebApiResult<DocumentLibraryAdminDTO> listQuotaExceededLibraries() {
    List<DocumentLibraryAdminDTO> libraries = facade.listQuotaExceededLibraries().stream()
        .map(this::toAdminDTO)
        .collect(Collectors.toList());
    return ItemsWebApiResult.ok(libraries);
  }
}
