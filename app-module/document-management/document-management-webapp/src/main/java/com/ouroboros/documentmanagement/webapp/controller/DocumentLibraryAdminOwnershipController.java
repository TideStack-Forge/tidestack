package com.ouroboros.documentmanagement.webapp.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import com.ouroboros.documentmanagement.model.DocumentLibrary;
import com.ouroboros.documentmanagement.service.DocumentLibraryAdminFacade;
import com.ouroboros.documentmanagement.webapp.util.UserContextHolder;
import com.ouroboros.web.ItemsWebApiResult;
import com.ouroboros.web.WebApiResult;

/**
 * 档案库后台管理所有权与管理员管理 REST API Controller
 */
@RestController
@RequestMapping("/api/document-management/admin/libraries")
public class DocumentLibraryAdminOwnershipController extends DocumentAdminControllerSupport {

  public DocumentLibraryAdminOwnershipController(DocumentLibraryAdminFacade facade) {
    super(facade);
  }

  @Operation(
      summary = "后台转移档案库所有权",
      description = "由后台管理员直接转移档案库所有权，并可选择保留原所有者为管理员。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "所有权转移成功"),
      @ApiResponse(responseCode = "400", description = "转移请求参数不合法"),
      @ApiResponse(responseCode = "404", description = "档案库或新所有者不存在")
  })
  @PostMapping("/{libraryId}/transfer-ownership")
  public WebApiResult<DocumentLibrary> transferOwnership(
      @Parameter(description = "档案库 ID", required = true)
      @PathVariable("libraryId") String libraryId,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "所有权转移请求", required = true)
      @Valid @RequestBody DocumentLibraryAdminRequests.TransferOwnershipRequest request) {
    String operatorId = UserContextHolder.requireCurrentUserId();
    DocumentLibrary library =
        facade.transferOwnership(
            libraryId, request.newOwnerId, request.keepOriginalOwnerAsAdmin, operatorId);
    return WebApiResult.success(library);
  }

  @Operation(
      summary = "后台添加档案库管理员",
      description = "由后台管理员为指定档案库添加管理员。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "管理员添加成功"),
      @ApiResponse(responseCode = "404", description = "档案库或管理员不存在")
  })
  @PostMapping("/{libraryId}/admins/{adminUserId}")
  public WebApiResult<DocumentLibrary> addAdmin(
      @Parameter(description = "档案库 ID", required = true)
      @PathVariable("libraryId") String libraryId,
      @Parameter(description = "管理员用户 ID", required = true)
      @PathVariable("adminUserId") String adminUserId) {
    String operatorId = UserContextHolder.requireCurrentUserId();
    DocumentLibrary library = facade.addAdmin(libraryId, adminUserId, operatorId);
    return WebApiResult.success(library);
  }

  @Operation(
      summary = "后台移除档案库管理员",
      description = "由后台管理员移除指定档案库的管理员。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "管理员移除成功"),
      @ApiResponse(responseCode = "404", description = "档案库或管理员不存在")
  })
  @DeleteMapping("/{libraryId}/admins/{adminUserId}")
  public WebApiResult<DocumentLibrary> removeAdmin(
      @Parameter(description = "档案库 ID", required = true)
      @PathVariable("libraryId") String libraryId,
      @Parameter(description = "管理员用户 ID", required = true)
      @PathVariable("adminUserId") String adminUserId) {
    String operatorId = UserContextHolder.requireCurrentUserId();
    DocumentLibrary library = facade.removeAdmin(libraryId, adminUserId, operatorId);
    return WebApiResult.success(library);
  }

  @Operation(
      summary = "后台批量添加管理员",
      description = "由后台管理员为档案库批量添加管理员。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "管理员批量添加成功"),
      @ApiResponse(responseCode = "400", description = "管理员列表参数不合法"),
      @ApiResponse(responseCode = "404", description = "档案库不存在")
  })
  @PostMapping("/{libraryId}/admins/batch")
  public WebApiResult<DocumentLibrary> addAdmins(
      @Parameter(description = "档案库 ID", required = true)
      @PathVariable("libraryId") String libraryId,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "管理员列表", required = true)
      @Valid @RequestBody DocumentLibraryAdminRequests.BatchAdminRequest request) {
    String operatorId = UserContextHolder.requireCurrentUserId();
    DocumentLibrary library = facade.addAdmins(libraryId, request.adminUserIds, operatorId);
    return WebApiResult.success(library);
  }

  @Operation(
      summary = "后台批量移除管理员",
      description = "由后台管理员为档案库批量移除管理员。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "管理员批量移除成功"),
      @ApiResponse(responseCode = "400", description = "管理员列表参数不合法"),
      @ApiResponse(responseCode = "404", description = "档案库不存在")
  })
  @DeleteMapping("/{libraryId}/admins/batch")
  public WebApiResult<DocumentLibrary> removeAdmins(
      @Parameter(description = "档案库 ID", required = true)
      @PathVariable("libraryId") String libraryId,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "待移除管理员列表", required = true)
      @Valid @RequestBody DocumentLibraryAdminRequests.BatchAdminRequest request) {
    String operatorId = UserContextHolder.requireCurrentUserId();
    DocumentLibrary library = facade.removeAdmins(libraryId, request.adminUserIds, operatorId);
    return WebApiResult.success(library);
  }

  @Operation(
      summary = "后台设置档案库所有者",
      description = "由后台管理员直接指定档案库的新所有者。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "档案库所有者更新成功"),
      @ApiResponse(responseCode = "400", description = "请求参数不合法"),
      @ApiResponse(responseCode = "404", description = "档案库或新所有者不存在")
  })
  @PutMapping("/{libraryId}/owner")
  public WebApiResult<DocumentLibrary> setOwner(
      @Parameter(description = "档案库 ID", required = true)
      @PathVariable("libraryId") String libraryId,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "新所有者设置请求", required = true)
      @Valid @RequestBody DocumentLibraryAdminRequests.SetOwnerRequest request) {
    String operatorId = UserContextHolder.requireCurrentUserId();
    DocumentLibrary library = facade.setOwner(libraryId, request.newOwnerId, operatorId);
    return WebApiResult.success(library);
  }

  @Operation(
      summary = "后台设置管理员列表",
      description = "由后台管理员直接覆盖指定档案库的管理员列表。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "管理员列表更新成功"),
      @ApiResponse(responseCode = "400", description = "管理员列表参数不合法"),
      @ApiResponse(responseCode = "404", description = "档案库不存在")
  })
  @PutMapping("/{libraryId}/admins")
  public WebApiResult<DocumentLibrary> setAdmins(
      @Parameter(description = "档案库 ID", required = true)
      @PathVariable("libraryId") String libraryId,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "管理员列表", required = true)
      @Valid @RequestBody DocumentLibraryAdminRequests.BatchAdminRequest request) {
    String operatorId = UserContextHolder.requireCurrentUserId();
    DocumentLibrary library = facade.setAdmins(libraryId, request.adminUserIds, operatorId);
    return WebApiResult.success(library);
  }

  @Operation(
      summary = "查询档案库管理员列表",
      description = "以后台管理视角查询档案库当前的管理员用户 ID 列表。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回管理员列表"),
      @ApiResponse(responseCode = "404", description = "档案库不存在")
  })
  @GetMapping("/{libraryId}/admins")
  public ItemsWebApiResult<String> listAdmins(
      @Parameter(description = "档案库 ID", required = true)
      @PathVariable("libraryId") String libraryId) {
    List<String> admins = facade.listAdmins(libraryId);
    return ItemsWebApiResult.ok(admins);
  }

  @Operation(
      summary = "后台更新档案库配额",
      description = "由后台管理员更新档案库的容量与文件数量配额。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "档案库配额更新成功"),
      @ApiResponse(responseCode = "400", description = "配额参数不合法"),
      @ApiResponse(responseCode = "404", description = "档案库不存在")
  })
  @PutMapping("/{libraryId}/quota")
  public WebApiResult<DocumentLibrary> updateQuota(
      @Parameter(description = "档案库 ID", required = true)
      @PathVariable("libraryId") String libraryId,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "档案库配额更新请求", required = true)
      @Valid @RequestBody DocumentLibraryAdminRequests.UpdateQuotaRequest request) {
    String operatorId = UserContextHolder.requireCurrentUserId();
    DocumentLibrary library =
        facade.updateQuota(libraryId, request.maxSizeLimit, request.maxFileCountLimit, operatorId);
    return WebApiResult.success(library);
  }

  @Operation(
      summary = "后台移除档案库配额",
      description = "由后台管理员清除指定档案库的配额限制。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "档案库配额移除成功"),
      @ApiResponse(responseCode = "404", description = "档案库不存在")
  })
  @DeleteMapping("/{libraryId}/quota")
  public WebApiResult<DocumentLibrary> removeQuota(
      @Parameter(description = "档案库 ID", required = true)
      @PathVariable("libraryId") String libraryId) {
    String operatorId = UserContextHolder.requireCurrentUserId();
    DocumentLibrary library = facade.removeQuota(libraryId, operatorId);
    return WebApiResult.success(library);
  }

  @Operation(
      summary = "后台批量更新配额",
      description = "由后台管理员批量更新多个档案库的配额限制。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "档案库配额批量更新成功"),
      @ApiResponse(responseCode = "400", description = "批量配额参数不合法")
  })
  @PutMapping("/quota/batch")
  public ItemsWebApiResult<DocumentLibrary> batchUpdateQuota(
      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "批量档案库配额更新请求", required = true)
      @Valid @RequestBody DocumentLibraryAdminRequests.BatchUpdateQuotaRequest request) {
    String operatorId = UserContextHolder.requireCurrentUserId();
    List<DocumentLibrary> libraries =
        facade.batchUpdateQuota(
            request.libraryIds, request.maxSizeLimit, request.maxFileCountLimit, operatorId);
    return ItemsWebApiResult.ok(libraries);
  }
}
