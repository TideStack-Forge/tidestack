package com.ouroboros.documentmanagement.webapp.controller;

import java.util.List;
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

import com.ouroboros.documentmanagement.service.DocumentLibraryAdminFacade;
import com.ouroboros.documentmanagement.webapp.dto.DocumentLibraryAdminDTO;
import com.ouroboros.web.ItemsWebApiResult;
import com.ouroboros.web.WebApiResult;

/**
 * 档案库后台管理查询 REST API Controller
 */
@RestController
@RequestMapping("/api/document-management/admin/libraries")
public class DocumentLibraryAdminQueryController extends DocumentAdminControllerSupport {

  public DocumentLibraryAdminQueryController(DocumentLibraryAdminFacade facade) {
    super(facade);
  }

  @Operation(
      summary = "查询全部档案库",
      description = "以后台管理视角查询系统中的全部档案库。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回档案库列表")
  })
  @GetMapping
  public ItemsWebApiResult<DocumentLibraryAdminDTO> listAllLibraries() {
    List<DocumentLibraryAdminDTO> libraries = facade.listAllLibraries().stream()
        .map(this::toAdminDTO)
        .collect(Collectors.toList());
    return ItemsWebApiResult.ok(libraries);
  }

  @Operation(
      summary = "按条件查询档案库",
      description = "根据删除、禁用和锁定状态过滤档案库列表。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回档案库列表")
  })
  @GetMapping("/filtered")
  public ItemsWebApiResult<DocumentLibraryAdminDTO> listLibraries(
      @Parameter(description = "是否包含已删除档案库")
      @RequestParam(value = "includeDeleted", defaultValue = "false") boolean includeDeleted,
      @Parameter(description = "是否包含已禁用档案库")
      @RequestParam(value = "includeDisabled", defaultValue = "true") boolean includeDisabled,
      @Parameter(description = "是否包含已锁定档案库")
      @RequestParam(value = "includeLocked", defaultValue = "true") boolean includeLocked) {
    List<DocumentLibraryAdminDTO> libraries =
        facade.listLibraries(includeDeleted, includeDisabled, includeLocked).stream()
            .map(this::toAdminDTO)
            .collect(Collectors.toList());
    return ItemsWebApiResult.ok(libraries);
  }

  @Operation(
      summary = "按所有者查询档案库",
      description = "根据所有者用户 ID 查询其名下的档案库列表。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回档案库列表")
  })
  @GetMapping("/by-owner/{ownerId}")
  public ItemsWebApiResult<DocumentLibraryAdminDTO> listLibrariesByOwner(
      @Parameter(description = "所有者用户 ID", required = true)
      @PathVariable("ownerId") String ownerId) {
    List<DocumentLibraryAdminDTO> libraries = facade.listLibrariesByOwner(ownerId).stream()
        .map(this::toAdminDTO)
        .collect(Collectors.toList());
    return ItemsWebApiResult.ok(libraries);
  }

  @Operation(
      summary = "按管理员查询档案库",
      description = "根据管理员用户 ID 查询其管理的档案库列表。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回档案库列表")
  })
  @GetMapping("/by-admin/{adminId}")
  public ItemsWebApiResult<DocumentLibraryAdminDTO> listLibrariesByAdmin(
      @Parameter(description = "管理员用户 ID", required = true)
      @PathVariable("adminId") String adminId) {
    List<DocumentLibraryAdminDTO> libraries = facade.listLibrariesByAdmin(adminId).stream()
        .map(this::toAdminDTO)
        .collect(Collectors.toList());
    return ItemsWebApiResult.ok(libraries);
  }

  @Operation(
      summary = "查询档案库详情",
      description = "以后台管理视角根据档案库 ID 查询详情。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回档案库详情"),
      @ApiResponse(responseCode = "404", description = "档案库不存在")
  })
  @GetMapping("/{libraryId}")
  public WebApiResult<DocumentLibraryAdminDTO> getLibrary(
      @Parameter(description = "档案库 ID", required = true)
      @PathVariable("libraryId") String libraryId) {
    return facade
        .getLibrary(libraryId)
        .map(this::toAdminDTO)
        .map(WebApiResult::success)
        .orElse(notFound("档案库不存在"));
  }

  @Operation(
      summary = "按名称查询档案库",
      description = "以后台管理视角根据档案库名称查询详情。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回档案库详情"),
      @ApiResponse(responseCode = "404", description = "档案库不存在")
  })
  @GetMapping("/by-name/{name}")
  public WebApiResult<DocumentLibraryAdminDTO> getLibraryByName(
      @Parameter(description = "档案库名称", required = true)
      @PathVariable("name") String name) {
    return facade
        .getLibraryByName(name)
        .map(this::toAdminDTO)
        .map(WebApiResult::success)
        .orElse(notFound("档案库不存在"));
  }
}
