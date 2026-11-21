package com.ouroboros.documentmanagement.webapp.controller;

import java.util.List;
import java.util.stream.Collectors;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import com.ouroboros.documentmanagement.model.DocumentLibrary;
import com.ouroboros.documentmanagement.service.DocumentLibraryAdminFacade;
import com.ouroboros.documentmanagement.webapp.dto.DocumentLibraryAdminDTO;
import com.ouroboros.documentmanagement.webapp.util.UserContextHolder;
import com.ouroboros.web.ItemsWebApiResult;
import com.ouroboros.web.WebApiResult;

/**
 * 档案库后台管理生命周期 REST API Controller
 */
@RestController
@RequestMapping("/api/document-management/admin/libraries")
public class DocumentLibraryAdminLifecycleController extends DocumentAdminControllerSupport {

  public DocumentLibraryAdminLifecycleController(DocumentLibraryAdminFacade facade) {
    super(facade);
  }

  @Operation(
      summary = "后台启用档案库",
      description = "由后台管理员启用指定档案库。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "档案库启用成功"),
      @ApiResponse(responseCode = "404", description = "档案库不存在")
  })
  @PostMapping("/{libraryId}/enable")
  public WebApiResult<DocumentLibrary> enableLibrary(
      @Parameter(description = "档案库 ID", required = true)
      @PathVariable("libraryId") String libraryId) {
    String operatorId = UserContextHolder.requireCurrentUserId();
    DocumentLibrary library = facade.enableLibrary(libraryId, operatorId);
    return WebApiResult.success(library);
  }

  @Operation(
      summary = "后台禁用档案库",
      description = "由后台管理员禁用指定档案库。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "档案库禁用成功"),
      @ApiResponse(responseCode = "404", description = "档案库不存在")
  })
  @PostMapping("/{libraryId}/disable")
  public WebApiResult<DocumentLibrary> disableLibrary(
      @Parameter(description = "档案库 ID", required = true)
      @PathVariable("libraryId") String libraryId) {
    String operatorId = UserContextHolder.requireCurrentUserId();
    DocumentLibrary library = facade.disableLibrary(libraryId, operatorId);
    return WebApiResult.success(library);
  }

  @Operation(
      summary = "后台锁定档案库",
      description = "由后台管理员锁定指定档案库。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "档案库锁定成功"),
      @ApiResponse(responseCode = "404", description = "档案库不存在")
  })
  @PostMapping("/{libraryId}/lock")
  public WebApiResult<DocumentLibrary> lockLibrary(
      @Parameter(description = "档案库 ID", required = true)
      @PathVariable("libraryId") String libraryId) {
    String operatorId = UserContextHolder.requireCurrentUserId();
    DocumentLibrary library = facade.lockLibrary(libraryId, operatorId);
    return WebApiResult.success(library);
  }

  @Operation(
      summary = "后台解锁档案库",
      description = "由后台管理员解除指定档案库的锁定状态。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "档案库解锁成功"),
      @ApiResponse(responseCode = "404", description = "档案库不存在")
  })
  @PostMapping("/{libraryId}/unlock")
  public WebApiResult<DocumentLibrary> unlockLibrary(
      @Parameter(description = "档案库 ID", required = true)
      @PathVariable("libraryId") String libraryId) {
    String operatorId = UserContextHolder.requireCurrentUserId();
    DocumentLibrary library = facade.unlockLibrary(libraryId, operatorId);
    return WebApiResult.success(library);
  }

  @Operation(
      summary = "后台启用回收站",
      description = "由后台管理员启用指定档案库的回收站功能。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "回收站启用成功"),
      @ApiResponse(responseCode = "404", description = "档案库不存在")
  })
  @PostMapping("/{libraryId}/trash/enable")
  public WebApiResult<DocumentLibrary> enableTrash(
      @Parameter(description = "档案库 ID", required = true)
      @PathVariable("libraryId") String libraryId) {
    String operatorId = UserContextHolder.requireCurrentUserId();
    DocumentLibrary library = facade.enableTrash(libraryId, operatorId);
    return WebApiResult.success(library);
  }

  @Operation(
      summary = "后台禁用回收站",
      description = "由后台管理员禁用指定档案库的回收站功能。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "回收站禁用成功"),
      @ApiResponse(responseCode = "404", description = "档案库不存在")
  })
  @PostMapping("/{libraryId}/trash/disable")
  public WebApiResult<DocumentLibrary> disableTrash(
      @Parameter(description = "档案库 ID", required = true)
      @PathVariable("libraryId") String libraryId) {
    String operatorId = UserContextHolder.requireCurrentUserId();
    DocumentLibrary library = facade.disableTrash(libraryId, operatorId);
    return WebApiResult.success(library);
  }

  @Operation(
      summary = "后台批量启用档案库",
      description = "由后台管理员批量启用多个档案库。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "档案库批量启用成功"),
      @ApiResponse(responseCode = "400", description = "档案库列表参数不合法")
  })
  @PostMapping("/enable/batch")
  public ItemsWebApiResult<DocumentLibrary> batchEnableLibraries(
      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "待启用档案库列表", required = true)
      @Valid @RequestBody DocumentLibraryAdminRequests.BatchLibraryRequest request) {
    String operatorId = UserContextHolder.requireCurrentUserId();
    List<DocumentLibrary> libraries = facade.batchEnableLibraries(request.libraryIds, operatorId);
    return ItemsWebApiResult.ok(libraries);
  }

  @Operation(
      summary = "后台批量禁用档案库",
      description = "由后台管理员批量禁用多个档案库。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "档案库批量禁用成功"),
      @ApiResponse(responseCode = "400", description = "档案库列表参数不合法")
  })
  @PostMapping("/disable/batch")
  public ItemsWebApiResult<DocumentLibrary> batchDisableLibraries(
      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "待禁用档案库列表", required = true)
      @Valid @RequestBody DocumentLibraryAdminRequests.BatchLibraryRequest request) {
    String operatorId = UserContextHolder.requireCurrentUserId();
    List<DocumentLibrary> libraries = facade.batchDisableLibraries(request.libraryIds, operatorId);
    return ItemsWebApiResult.ok(libraries);
  }

  @Operation(
      summary = "后台软删除档案库",
      description = "由后台管理员将指定档案库标记为软删除。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "档案库软删除成功"),
      @ApiResponse(responseCode = "404", description = "档案库不存在")
  })
  @PostMapping("/{libraryId}/soft-delete")
  public WebApiResult<DocumentLibrary> softDeleteLibrary(
      @Parameter(description = "档案库 ID", required = true)
      @PathVariable("libraryId") String libraryId) {
    String operatorId = UserContextHolder.requireCurrentUserId();
    DocumentLibrary library = facade.softDeleteLibrary(libraryId, operatorId);
    return WebApiResult.success(library);
  }

  @Operation(
      summary = "后台恢复档案库",
      description = "由后台管理员恢复已软删除的档案库。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "档案库恢复成功"),
      @ApiResponse(responseCode = "404", description = "档案库不存在")
  })
  @PostMapping("/{libraryId}/restore")
  public WebApiResult<DocumentLibrary> restoreLibrary(
      @Parameter(description = "档案库 ID", required = true)
      @PathVariable("libraryId") String libraryId) {
    String operatorId = UserContextHolder.requireCurrentUserId();
    DocumentLibrary library = facade.restoreLibrary(libraryId, operatorId);
    return WebApiResult.success(library);
  }

  @Operation(
      summary = "后台硬删除档案库",
      description = "由后台管理员彻底删除指定档案库。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "档案库硬删除成功"),
      @ApiResponse(responseCode = "404", description = "档案库不存在")
  })
  @DeleteMapping("/{libraryId}")
  public WebApiResult<Void> hardDeleteLibrary(
      @Parameter(description = "档案库 ID", required = true)
      @PathVariable("libraryId") String libraryId) {
    String operatorId = UserContextHolder.requireCurrentUserId();
    facade.hardDeleteLibrary(libraryId, operatorId);
    return WebApiResult.success();
  }

  @Operation(
      summary = "后台批量软删除档案库",
      description = "由后台管理员批量软删除多个档案库。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "档案库批量软删除成功"),
      @ApiResponse(responseCode = "400", description = "档案库列表参数不合法")
  })
  @PostMapping("/soft-delete/batch")
  public ItemsWebApiResult<DocumentLibrary> batchSoftDeleteLibraries(
      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "待软删除档案库列表", required = true)
      @Valid @RequestBody DocumentLibraryAdminRequests.BatchLibraryRequest request) {
    String operatorId = UserContextHolder.requireCurrentUserId();
    List<DocumentLibrary> libraries =
        facade.batchSoftDeleteLibraries(request.libraryIds, operatorId);
    return ItemsWebApiResult.ok(libraries);
  }

  @Operation(
      summary = "后台批量恢复档案库",
      description = "由后台管理员批量恢复多个已软删除档案库。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "档案库批量恢复成功"),
      @ApiResponse(responseCode = "400", description = "档案库列表参数不合法")
  })
  @PostMapping("/restore/batch")
  public ItemsWebApiResult<DocumentLibrary> batchRestoreLibraries(
      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "待恢复档案库列表", required = true)
      @Valid @RequestBody DocumentLibraryAdminRequests.BatchLibraryRequest request) {
    String operatorId = UserContextHolder.requireCurrentUserId();
    List<DocumentLibrary> libraries = facade.batchRestoreLibraries(request.libraryIds, operatorId);
    return ItemsWebApiResult.ok(libraries);
  }

  @Operation(
      summary = "后台批量硬删除档案库",
      description = "由后台管理员批量彻底删除多个档案库。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "档案库批量硬删除成功"),
      @ApiResponse(responseCode = "400", description = "档案库列表参数不合法")
  })
  @DeleteMapping("/batch")
  public WebApiResult<Void> batchHardDeleteLibraries(
      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "待硬删除档案库列表", required = true)
      @Valid @RequestBody DocumentLibraryAdminRequests.BatchLibraryRequest request) {
    String operatorId = UserContextHolder.requireCurrentUserId();
    facade.batchHardDeleteLibraries(request.libraryIds, operatorId);
    return WebApiResult.success();
  }

  @Operation(
      summary = "后台清理已删除档案库",
      description = "按照保留天数清理超过期限的已软删除档案库。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回清理数量")
  })
  @PostMapping("/cleanup")
  public WebApiResult<Integer> cleanupDeletedLibraries(
      @Parameter(description = "已删除档案库保留天数")
      @RequestParam(value = "retentionDays", defaultValue = "30") int retentionDays) {
    String operatorId = UserContextHolder.requireCurrentUserId();
    int count = facade.cleanupDeletedLibraries(retentionDays, operatorId);
    return WebApiResult.success(count);
  }

  @Operation(
      summary = "查询已禁用档案库",
      description = "查询当前处于禁用状态的档案库列表。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回档案库列表")
  })
  @GetMapping("/disabled")
  public ItemsWebApiResult<DocumentLibraryAdminDTO> listDisabledLibraries() {
    List<DocumentLibraryAdminDTO> libraries = facade.listDisabledLibraries().stream()
        .map(this::toAdminDTO)
        .collect(Collectors.toList());
    return ItemsWebApiResult.ok(libraries);
  }

  @Operation(
      summary = "查询已锁定档案库",
      description = "查询当前处于锁定状态的档案库列表。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回档案库列表")
  })
  @GetMapping("/locked")
  public ItemsWebApiResult<DocumentLibraryAdminDTO> listLockedLibraries() {
    List<DocumentLibraryAdminDTO> libraries = facade.listLockedLibraries().stream()
        .map(this::toAdminDTO)
        .collect(Collectors.toList());
    return ItemsWebApiResult.ok(libraries);
  }

  @Operation(
      summary = "查询已删除档案库",
      description = "查询当前已软删除的档案库列表。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回档案库列表")
  })
  @GetMapping("/deleted")
  public ItemsWebApiResult<DocumentLibraryAdminDTO> listDeletedLibraries() {
    List<DocumentLibraryAdminDTO> libraries = facade.listDeletedLibraries().stream()
        .map(this::toAdminDTO)
        .collect(Collectors.toList());
    return ItemsWebApiResult.ok(libraries);
  }
}
