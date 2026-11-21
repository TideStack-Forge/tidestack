package com.ouroboros.documentmanagement.webapp.controller;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.vavr.control.Try;

import com.ouroboros.documentmanagement.model.DirectoryNode;
import com.ouroboros.documentmanagement.model.DocumentLibrary;
import com.ouroboros.documentmanagement.model.LibraryNode;
import com.ouroboros.documentmanagement.model.acl.AclEntry;
import com.ouroboros.documentmanagement.model.acl.Permission;
import com.ouroboros.documentmanagement.model.acl.PermissionSet;
import com.ouroboros.documentmanagement.model.acl.PrincipalResolver;
import com.ouroboros.documentmanagement.model.acl.PrincipalType;
import com.ouroboros.documentmanagement.model.acl.PrincipalTypeProvider;
import com.ouroboros.documentmanagement.service.DocumentLibraryContentFacade;
import com.ouroboros.documentmanagement.service.DocumentLibraryManagementFacade;
import com.ouroboros.documentmanagement.service.DocumentLibraryService.LibraryStatistics;
import com.ouroboros.documentmanagement.webapp.dto.AclEntryDTO;
import com.ouroboros.documentmanagement.webapp.dto.LibraryOverviewDTO;
import com.ouroboros.documentmanagement.webapp.dto.LibraryTreeNodeDTO;
import com.ouroboros.documentmanagement.webapp.dto.PermissionsDTO;
import com.ouroboros.documentmanagement.webapp.dto.PrincipalTypeDTO;
import com.ouroboros.documentmanagement.webapp.dto.StatisticItemDTO;
import com.ouroboros.documentmanagement.webapp.util.UserContextHolder;
import com.ouroboros.uischema.schema.SchemaNode;
import com.ouroboros.web.ItemsWebApiResult;
import com.ouroboros.web.WebApiResult;

/**
 * 档案库管理 REST API Controller
 *
 * <p>提供档案库所有者和管理员的管理功能，包括：
 * <ul>
 *   <li>档案库创建和查询</li>
 *   <li>基本信息管理（所有者操作）</li>
 *   <li>管理员管理（所有者操作）</li>
 *   <li>所有权转移（所有者操作）</li>
 *   <li>访问权限管理（所有者和管理员都可以）</li>
 *   <li>统计信息查询</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/document-libraries")
public class DocumentLibraryManagementController {

  private static final String NODE_TYPE_LIBRARY = "LIBRARY";
  private static final String ICON_DATABASE = "fa fa-database";
  private static final String API_PATH_DOCUMENT_LIBRARIES = "/api/document-libraries/";
  private static final String API_PATH_DIRECTORIES = "/directories";
  
  private static final String STAT_LABEL_DOCUMENT_COUNT = "文件总数";
  private static final String STAT_LABEL_DIRECTORY_COUNT = "目录总数";
  private static final String STAT_LABEL_USED_SPACE = "总容量";
  
  private static final long SIZE_UNIT_KB = 1024L;
  private static final long SIZE_UNIT_MB = SIZE_UNIT_KB * 1024L;
  private static final long SIZE_UNIT_GB = SIZE_UNIT_MB * 1024L;
  
  private static final String SIZE_FORMAT_BYTES = " B";
  private static final String SIZE_FORMAT_KB = "%.2f KB";
  private static final String SIZE_FORMAT_MB = "%.2f MB";
  private static final String SIZE_FORMAT_GB = "%.2f GB";
  
  private static final String PRINCIPAL_TYPE_USER = "USER";

  private final DocumentLibraryManagementFacade facade;
  private final DocumentLibraryContentFacade contentFacade;

  public DocumentLibraryManagementController(DocumentLibraryManagementFacade facade, DocumentLibraryContentFacade contentFacade) {
    this.facade = facade;
    this.contentFacade = contentFacade;
  }

  // ==================== 档案库创建和查询 ====================

  /**
   * 创建档案库
   */
  @Operation(
      summary = "创建档案库",
      description = "以当前登录用户作为所有者创建一个新的档案库。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "档案库创建成功"),
      @ApiResponse(responseCode = "400", description = "档案库参数不合法")
  })
  @PostMapping
  public WebApiResult<DocumentLibrary> createLibrary(
      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "档案库创建请求", required = true)
      @Valid @RequestBody CreateLibraryRequest request) {
    String userId = UserContextHolder.requireCurrentUserId();
    DocumentLibrary library =
        facade.createLibrary(request.name, request.displayName, request.description, userId);
    return WebApiResult.success(library);
  }

  /**
   * 查询用户拥有的档案库列表（作为所有者）
   */
  @Operation(
      summary = "查询我拥有的档案库",
      description = "查询当前登录用户作为所有者拥有的全部档案库。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回档案库列表")
  })
  @GetMapping("/owned")
  public ItemsWebApiResult<DocumentLibrary> listOwnedLibraries() {
    String userId = UserContextHolder.requireCurrentUserId();
    List<DocumentLibrary> libraries = facade.listOwnedLibraries(userId);
    return ItemsWebApiResult.ok(libraries);
  }

  /**
   * 查询用户管理的档案库列表（作为管理员）
   */
  @Operation(
      summary = "查询我管理的档案库",
      description = "查询当前登录用户作为管理员可管理的档案库列表。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回档案库列表")
  })
  @GetMapping("/managed")
  public ItemsWebApiResult<DocumentLibrary> listManagedLibraries() {
    String userId = UserContextHolder.requireCurrentUserId();
    List<DocumentLibrary> libraries = facade.listManagedLibraries(userId);
    return ItemsWebApiResult.ok(libraries);
  }

  /**
   * 查询用户可访问的所有档案库
   */
  @Operation(
      summary = "查询可访问档案库树",
      description = "查询当前用户可访问的档案库，并可按当前库和目录回填树形结构。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回可访问档案库树")
  })
  @GetMapping("/accessible")
  public ItemsWebApiResult<LibraryTreeNodeDTO> listAccessibleLibraries(
      @Parameter(description = "当前选中的档案库 ID，用于回填目录树")
      @RequestParam(value = "currentLibrary", defaultValue = "") String currentLibrary,
      @Parameter(description = "当前选中的目录 ID")
      @RequestParam(value = "currentDirectory", defaultValue = "") String currentDirectory
  ) {
    String userId = UserContextHolder.requireCurrentUserId();
    var currentLibraryTree = StringUtils.isEmpty(currentLibrary)
        ? Optional.<DirectoryNode>empty()
        : Try.of(() -> contentFacade.getDirectoryTree(currentLibrary, userId)).getOrElse(Optional::empty);
    List<DocumentLibrary> libraries = facade.listAccessibleLibraries(userId);

    List<LibraryTreeNodeDTO> treeNodes = libraries.stream()
        .map(lib -> {
          LibraryTreeNodeDTO node = new LibraryTreeNodeDTO();
          node.setLabel(lib.getDisplayName());
          node.setValue(lib.getId());
          node.setType(NODE_TYPE_LIBRARY);
          node.setIcon(ICON_DATABASE);
          node.setLibraryId(lib.getId());
          node.setDisabled(lib.isDisabled());
          node.setLocked(lib.isLocked());
          if (StringUtils.equals(lib.getId(), currentLibrary) && currentLibraryTree.isPresent()) {
            currentLibraryTree.map(LibraryTreeNodeDTO::fromLibraryNode)
                .ifPresent(tree -> node.setChildren(tree.getChildren()));
          } else {
            node.setHasChildren(true);  // 文档库通常都有根目录
            node.setDefer(true); // 延迟加载子节点
            // 设置deferApi用于加载该文档库的目录树
            node.setDeferApi(API_PATH_DOCUMENT_LIBRARIES + lib.getId() + API_PATH_DIRECTORIES);
          }

          return node;
        })
        .collect(Collectors.toList());

    return ItemsWebApiResult.ok(treeNodes);
  }

  /**
   * 根据ID获取档案库详情
   */
  @Operation(
      summary = "获取档案库详情",
      description = "根据档案库 ID 查询当前用户可访问的档案库详情。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回档案库详情"),
      @ApiResponse(responseCode = "404", description = "档案库不存在或当前用户无权访问")
  })
  @GetMapping("/{libraryId}")
  public WebApiResult<DocumentLibrary> getLibrary(
      @Parameter(description = "档案库 ID", required = true)
      @PathVariable("libraryId") String libraryId) {
    String userId = UserContextHolder.requireCurrentUserId();
    return facade
        .getLibrary(libraryId, userId)
        .map(WebApiResult::success)
        .orElse(notFound("档案库不存在或无权限访问"));
  }

  /**
   * 文档库概览（基本信息 + 统计数据 + 权限）
   */
  @Operation(
      summary = "获取档案库概览",
      description = "查询档案库的基础信息、统计数据、根目录与当前用户权限概览。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回档案库概览"),
      @ApiResponse(responseCode = "404", description = "档案库不存在或当前用户无权访问")
  })
  @GetMapping("/{libraryId}/overview")
  public WebApiResult<LibraryOverviewDTO> getLibraryOverview(
      @Parameter(description = "档案库 ID", required = true)
      @PathVariable("libraryId") String libraryId) {
    String userId = UserContextHolder.requireCurrentUserId();

    // 获取文档库基本信息
    DocumentLibrary library = facade.getLibrary(libraryId, userId)
        .orElse(null);
    if (library == null) {
      return notFound("文档库不存在或无权限访问");
    }

    String rootDirId = contentFacade.getRootDirectory(libraryId, userId).map(LibraryNode::getId).orElse(null);

    // 获取统计信息
    LibraryStatistics stats = facade.getLibraryStatistics(libraryId, userId);

    // 获取所有者显示名称
    String ownerDisplayName = getPrincipalDisplayName(library.getOwner());

    // 构建 DTO
    LibraryOverviewDTO dto = new LibraryOverviewDTO();
    dto.setId(library.getId());
    dto.setName(library.getName());
    dto.setDisplayName(library.getDisplayName());
    dto.setDescription(library.getDescription());
    dto.setOwner(library.getOwner());
    dto.setOwnerDisplayName(ownerDisplayName);
    dto.setCreatedAt(library.getCreatedAt());
    dto.setUpdatedAt(library.getUpdatedAt());
    dto.setDeleted(library.isDeleted());
    dto.setDisabled(library.isDisabled());
    dto.setLocked(library.isLocked());
    dto.setRootDirectoryId(rootDirId);

    // 添加 isAdmin 和 isOwner 到外层
    dto.setIsAdmin(facade.isAdmin(libraryId, userId));
    dto.setIsOwner(facade.isOwner(libraryId, userId));

    // 格式化统计数据
    List<StatisticItemDTO> statistics = Arrays.asList(
        new StatisticItemDTO(STAT_LABEL_DOCUMENT_COUNT, String.valueOf(stats.getDocumentCount())),
        new StatisticItemDTO(STAT_LABEL_DIRECTORY_COUNT, String.valueOf(stats.getDirectoryCount())),
        new StatisticItemDTO(STAT_LABEL_USED_SPACE, formatSize(stats.getUsedSpace()))
    );
    dto.setStatistics(statistics);

    return WebApiResult.success(dto);
  }

  /**
   * 根据名称获取档案库
   */
  @Operation(
      summary = "按名称获取档案库",
      description = "根据档案库名称查询当前用户可访问的档案库详情。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回档案库详情"),
      @ApiResponse(responseCode = "404", description = "档案库不存在或当前用户无权访问")
  })
  @GetMapping("/by-name/{name}")
  public WebApiResult<DocumentLibrary> getLibraryByName(
      @Parameter(description = "档案库名称", required = true)
      @PathVariable("name") String name) {
    String userId = UserContextHolder.requireCurrentUserId();
    return facade
        .getLibraryByName(name, userId)
        .map(WebApiResult::success)
        .orElse(notFound("档案库不存在或无权限访问"));
  }

  // ==================== 基本信息管理（所有者操作）====================

  /**
   * 更新档案库基本信息（所有者操作）
   */
  @Operation(
      summary = "更新档案库基本信息",
      description = "由档案库所有者更新名称、显示名称和描述等基本信息。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "档案库信息更新成功"),
      @ApiResponse(responseCode = "400", description = "更新参数不合法"),
      @ApiResponse(responseCode = "404", description = "档案库不存在或当前用户无权操作")
  })
  @PutMapping("/{libraryId}/info")
  public WebApiResult<DocumentLibrary> updateLibraryInfo(
      @Parameter(description = "档案库 ID", required = true)
      @PathVariable("libraryId") String libraryId,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "档案库基本信息更新请求", required = true)
      @Valid @RequestBody UpdateLibraryInfoRequest request) {
    String userId = UserContextHolder.requireCurrentUserId();
    DocumentLibrary library =
        facade.updateLibraryInfo(
            libraryId, request.name, request.displayName, request.description, userId);
    return WebApiResult.success(library);
  }

  /**
   * 更新档案库配额限制（所有者操作）
   */
  @Operation(
      summary = "更新档案库配额",
      description = "由档案库所有者设置容量上限和文件数量上限。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "档案库配额更新成功"),
      @ApiResponse(responseCode = "400", description = "配额参数不合法"),
      @ApiResponse(responseCode = "404", description = "档案库不存在或当前用户无权操作")
  })
  @PutMapping("/{libraryId}/quota")
  public WebApiResult<DocumentLibrary> updateLibraryQuota(
      @Parameter(description = "档案库 ID", required = true)
      @PathVariable("libraryId") String libraryId,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "档案库配额更新请求", required = true)
      @Valid @RequestBody UpdateQuotaRequest request) {
    String userId = UserContextHolder.requireCurrentUserId();
    DocumentLibrary library =
        facade.updateLibraryQuota(libraryId, request.maxSizeLimit, request.maxFileCountLimit, userId);
    return WebApiResult.success(library);
  }

  /**
   * 启用/禁用回收站功能（所有者操作）
   */
  @Operation(
      summary = "切换回收站功能",
      description = "由档案库所有者启用或禁用回收站功能。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "回收站状态更新成功"),
      @ApiResponse(responseCode = "404", description = "档案库不存在或当前用户无权操作")
  })
  @PutMapping("/{libraryId}/trash-enabled")
  public WebApiResult<DocumentLibrary> setTrashEnabled(
      @Parameter(description = "档案库 ID", required = true)
      @PathVariable("libraryId") String libraryId,
      @Parameter(description = "是否启用回收站", required = true)
      @RequestParam("enabled") boolean enabled) {
    String userId = UserContextHolder.requireCurrentUserId();
    DocumentLibrary library = facade.setTrashEnabled(libraryId, enabled, userId);
    return WebApiResult.success(library);
  }

  // ==================== 管理员管理（所有者操作）====================

  /**
   * 添加档案库管理员（所有者操作）
   */
  @Operation(
      summary = "添加档案库管理员",
      description = "由档案库所有者向指定档案库新增一个管理员。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "管理员添加成功"),
      @ApiResponse(responseCode = "404", description = "档案库或管理员不存在，或当前用户无权操作")
  })
  @PostMapping("/{libraryId}/admins/{adminUserId}")
  public WebApiResult<DocumentLibrary> addAdmin(
      @Parameter(description = "档案库 ID", required = true)
      @PathVariable("libraryId") String libraryId,
      @Parameter(description = "管理员用户 ID", required = true)
      @PathVariable("adminUserId") String adminUserId) {
    String userId = UserContextHolder.requireCurrentUserId();
    DocumentLibrary library = facade.addAdmin(libraryId, adminUserId, userId);
    return WebApiResult.success(library);
  }

  /**
   * 移除档案库管理员（所有者操作）
   */
  @Operation(
      summary = "移除档案库管理员",
      description = "由档案库所有者移除指定管理员。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "管理员移除成功"),
      @ApiResponse(responseCode = "404", description = "档案库或管理员不存在，或当前用户无权操作")
  })
  @DeleteMapping("/{libraryId}/admins/{adminUserId}")
  public WebApiResult<DocumentLibrary> removeAdmin(
      @Parameter(description = "档案库 ID", required = true)
      @PathVariable("libraryId") String libraryId,
      @Parameter(description = "管理员用户 ID", required = true)
      @PathVariable("adminUserId") String adminUserId) {
    String userId = UserContextHolder.requireCurrentUserId();
    DocumentLibrary library = facade.removeAdmin(libraryId, adminUserId, userId);
    return WebApiResult.success(library);
  }

  /**
   * 批量添加管理员（所有者操作）
   */
  @Operation(
      summary = "批量添加管理员",
      description = "由档案库所有者为档案库批量添加管理员。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "管理员批量添加成功"),
      @ApiResponse(responseCode = "400", description = "管理员列表参数不合法"),
      @ApiResponse(responseCode = "404", description = "档案库不存在或当前用户无权操作")
  })
  @PostMapping("/{libraryId}/admins/batch")
  public WebApiResult<DocumentLibrary> addAdmins(
      @Parameter(description = "档案库 ID", required = true)
      @PathVariable("libraryId") String libraryId,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "管理员列表", required = true)
      @Valid @RequestBody BatchAdminRequest request) {
    String userId = UserContextHolder.requireCurrentUserId();
    DocumentLibrary library = facade.addAdmins(libraryId, request.adminUserIds, userId);
    return WebApiResult.success(library);
  }

  /**
   * 批量移除管理员（所有者操作）
   */
  @Operation(
      summary = "批量移除管理员",
      description = "由档案库所有者为档案库批量移除管理员。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "管理员批量移除成功"),
      @ApiResponse(responseCode = "400", description = "管理员列表参数不合法"),
      @ApiResponse(responseCode = "404", description = "档案库不存在或当前用户无权操作")
  })
  @DeleteMapping("/{libraryId}/admins/batch")
  public WebApiResult<DocumentLibrary> removeAdmins(
      @Parameter(description = "档案库 ID", required = true)
      @PathVariable("libraryId") String libraryId,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "待移除管理员列表", required = true)
      @Valid @RequestBody BatchAdminRequest request) {
    String userId = UserContextHolder.requireCurrentUserId();
    DocumentLibrary library = facade.removeAdmins(libraryId, request.adminUserIds, userId);
    return WebApiResult.success(library);
  }

  /**
   * 获取档案库的所有管理员列表
   */
  @Operation(
      summary = "查询档案库管理员列表",
      description = "查询指定档案库当前配置的全部管理员用户 ID。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回管理员列表"),
      @ApiResponse(responseCode = "404", description = "档案库不存在或当前用户无权访问")
  })
  @GetMapping("/{libraryId}/admins")
  public ItemsWebApiResult<String> listAdmins(
      @Parameter(description = "档案库 ID", required = true)
      @PathVariable("libraryId") String libraryId) {
    String userId = UserContextHolder.requireCurrentUserId();
    List<String> admins = facade.listAdmins(libraryId, userId);
    return ItemsWebApiResult.ok(admins);
  }

  // ==================== 所有权转移（所有者操作）====================

  /**
   * 转移档案库所有权（所有者操作）
   */
  @Operation(
      summary = "转移档案库所有权",
      description = "由当前所有者把档案库所有权转移给新的用户，并可选择保留管理员权限。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "档案库所有权转移成功"),
      @ApiResponse(responseCode = "400", description = "转移请求参数不合法"),
      @ApiResponse(responseCode = "404", description = "档案库或新所有者不存在，或当前用户无权操作")
  })
  @PostMapping("/{libraryId}/transfer-ownership")
  public WebApiResult<DocumentLibrary> transferOwnership(
      @Parameter(description = "档案库 ID", required = true)
      @PathVariable("libraryId") String libraryId,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "所有权转移请求", required = true)
      @Valid @RequestBody TransferOwnershipRequest request) {
    String userId = UserContextHolder.requireCurrentUserId();
    DocumentLibrary library =
        facade.transferOwnership(libraryId, request.newOwnerId, request.keepAsAdmin, userId);
    return WebApiResult.success(library);
  }

  // ==================== 访问权限管理（所有者和管理员都可以）====================

  /**
   * 查询档案库的所有访问权限条目
   */
  @Operation(
      summary = "查询档案库 ACL",
      description = "查询档案库当前配置的全部访问控制条目。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回访问控制列表"),
      @ApiResponse(responseCode = "404", description = "档案库不存在或当前用户无权访问")
  })
  @GetMapping("/{libraryId}/acl-entries")
  public ItemsWebApiResult<AclEntryDTO> getLibraryAclEntries(
      @Parameter(description = "档案库 ID", required = true)
      @PathVariable("libraryId") String libraryId) {
    String userId = UserContextHolder.requireCurrentUserId();
    List<AclEntry> permissions = facade.getLibraryPermissions(libraryId, userId);
    List<AclEntryDTO> dtos = permissions.stream()
        .map(AclEntryDTO::fromAclEntry)
        .collect(Collectors.toList());
    return ItemsWebApiResult.ok(dtos);
  }

  // ==================== 角色和权限检查 ====================

  /**
   * 检查用户是否是档案库所有者
   */
  @Operation(
      summary = "检查是否为档案库所有者",
      description = "检查当前登录用户是否是指定档案库的所有者。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回校验结果"),
      @ApiResponse(responseCode = "404", description = "档案库不存在或当前用户无权访问")
  })
  @GetMapping("/{libraryId}/is-owner")
  public WebApiResult<Boolean> isOwner(
      @Parameter(description = "档案库 ID", required = true)
      @PathVariable("libraryId") String libraryId) {
    String userId = UserContextHolder.requireCurrentUserId();
    boolean isOwner = facade.isOwner(libraryId, userId);
    return WebApiResult.success(isOwner);
  }

  /**
   * 检查用户是否是档案库管理员
   */
  @Operation(
      summary = "检查是否为档案库管理员",
      description = "检查当前登录用户是否是指定档案库的管理员。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回校验结果"),
      @ApiResponse(responseCode = "404", description = "档案库不存在或当前用户无权访问")
  })
  @GetMapping("/{libraryId}/is-admin")
  public WebApiResult<Boolean> isAdmin(
      @Parameter(description = "档案库 ID", required = true)
      @PathVariable("libraryId") String libraryId) {
    String userId = UserContextHolder.requireCurrentUserId();
    boolean isAdmin = facade.isAdmin(libraryId, userId);
    return WebApiResult.success(isAdmin);
  }

  /**
   * 检查用户是否可以管理档案库（所有者或管理员）
   */
  @Operation(
      summary = "检查是否可管理档案库",
      description = "检查当前登录用户是否对指定档案库拥有管理权限。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回校验结果"),
      @ApiResponse(responseCode = "404", description = "档案库不存在或当前用户无权访问")
  })
  @GetMapping("/{libraryId}/can-manage")
  public WebApiResult<Boolean> canManageLibrary(
      @Parameter(description = "档案库 ID", required = true)
      @PathVariable("libraryId") String libraryId) {
    String userId = UserContextHolder.requireCurrentUserId();
    boolean canManage = facade.canManageLibrary(libraryId, userId);
    return WebApiResult.success(canManage);
  }

  // ==================== 统计信息 ====================

  /**
   * 获取档案库统计信息
   */
  @Operation(
      summary = "获取档案库统计信息",
      description = "获取档案库中的目录数、文件数与容量等统计数据。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回统计信息"),
      @ApiResponse(responseCode = "404", description = "档案库不存在或当前用户无权访问")
  })
  @GetMapping("/{libraryId}/statistics")
  public WebApiResult<LibraryStatistics> getLibraryStatistics(
      @Parameter(description = "档案库 ID", required = true)
      @PathVariable("libraryId") String libraryId) {
    String userId = UserContextHolder.requireCurrentUserId();
    LibraryStatistics statistics = facade.getLibraryStatistics(libraryId, userId);
    return WebApiResult.success(statistics);
  }

  /**
   * 检查档案库配额是否超限
   */
  @Operation(
      summary = "检查档案库配额状态",
      description = "检查指定档案库当前是否已经超出配额限制。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回配额校验结果"),
      @ApiResponse(responseCode = "404", description = "档案库不存在或当前用户无权访问")
  })
  @GetMapping("/{libraryId}/quota-exceeded")
  public WebApiResult<Boolean> isQuotaExceeded(
      @Parameter(description = "档案库 ID", required = true)
      @PathVariable("libraryId") String libraryId) {
    String userId = UserContextHolder.requireCurrentUserId();
    boolean exceeded = facade.isQuotaExceeded(libraryId, userId);
    return WebApiResult.success(exceeded);
  }

  /**
   * 检查是否可以上传指定大小的文件
   */
  @Operation(
      summary = "检查文件上传是否允许",
      description = "根据待上传文件大小检查档案库当前是否仍允许上传。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回上传校验结果"),
      @ApiResponse(responseCode = "404", description = "档案库不存在或当前用户无权访问")
  })
  @GetMapping("/{libraryId}/can-upload")
  public WebApiResult<Boolean> canUploadFile(
      @Parameter(description = "档案库 ID", required = true)
      @PathVariable("libraryId") String libraryId,
      @Parameter(description = "待上传文件大小（字节）", required = true)
      @RequestParam("fileSize") long fileSize) {
    String userId = UserContextHolder.requireCurrentUserId();
    boolean canUpload = facade.canUploadFile(libraryId, fileSize, userId);
    return WebApiResult.success(canUpload);
  }

  // ==================== Principal 类型查询 ====================

  /**
   * 获取所有有配置 UI Schema 的 Principal 类型
   */
  @Operation(
      summary = "查询可配置主体类型",
      description = "查询已经配置 UI Schema 的主体类型列表，用于 ACL 配置界面。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回主体类型列表")
  })
  @GetMapping("/principal-types")
  public ItemsWebApiResult<PrincipalTypeDTO> getPrincipalTypes() {
    List<PrincipalTypeDTO> types = PrincipalTypeProvider.getAll().stream()
        .filter(type -> type.getConfigUISchema().isPresent())
        .map(type -> new PrincipalTypeDTO(type.getName(), type.getDisplayName()))
        .collect(Collectors.toList());
    return ItemsWebApiResult.ok(types);
  }

  /**
   * 获取指定 Principal 类型的配置 UI Schema
   */
  @Operation(
      summary = "查询主体类型配置 Schema",
      description = "根据主体类型名称查询对应的配置 UI Schema。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回主体类型配置 Schema"),
      @ApiResponse(responseCode = "404", description = "主体类型不存在或未配置 UI Schema")
  })
  @GetMapping("/principal-type-schema")
  public WebApiResult<SchemaNode> getPrincipalTypeSchema(
      @Parameter(description = "主体类型名称", required = true)
      @RequestParam("type") String type) {
    Optional<PrincipalType> principalType = PrincipalTypeProvider.get(type);
    if (principalType.isPresent()) {
      Optional<SchemaNode> schema = principalType.get().getConfigUISchema();
      if (schema.isPresent()) {
        return WebApiResult.success(schema.get());
      }
      return notFound("Principal 类型 " + type + " 没有配置 UI Schema");
    }
    return notFound("未找到 Principal 类型: " + type);
  }

  // ==================== Private Helper Methods ====================

  private PermissionsDTO buildPermissions(String libraryId, String userId) {
    PermissionsDTO dto = new PermissionsDTO();
    dto.setCanManage(facade.canManageLibrary(libraryId, userId));

    // Get root directory to check content-related permissions
    Optional<DirectoryNode> rootDirOpt = contentFacade.getRootDirectory(libraryId, userId);
    if (rootDirOpt.isPresent()) {
      PermissionSet permissions = contentFacade.getDirectoryPermissions(libraryId, rootDirOpt.get().getId(), userId);
      dto.setCanView(permissions.has(Permission.VIEW));
      dto.setCanDownload(permissions.has(Permission.DOWNLOAD));
      dto.setCanCreateFile(permissions.has(Permission.CREATE_FILE));
      dto.setCanCreateDir(permissions.has(Permission.CREATE_DIR));
      dto.setCanRenameMove(permissions.has(Permission.RENAME_MOVE));
      dto.setCanDeleteFile(permissions.has(Permission.DELETE_FILE));
      dto.setCanDeleteDir(permissions.has(Permission.DELETE_DIR));
    } else {
      // Default to false if root directory is not accessible
      dto.setCanView(false);
      dto.setCanDownload(false);
      dto.setCanCreateFile(false);
      dto.setCanCreateDir(false);
      dto.setCanRenameMove(false);
      dto.setCanDeleteFile(false);
      dto.setCanDeleteDir(false);
    }
    return dto;
  }

  private String formatSize(long bytes) {
    if (bytes < SIZE_UNIT_KB) return bytes + SIZE_FORMAT_BYTES;
    if (bytes < SIZE_UNIT_MB) return String.format(SIZE_FORMAT_KB, bytes / (double) SIZE_UNIT_KB);
    if (bytes < SIZE_UNIT_GB) return String.format(SIZE_FORMAT_MB, bytes / (double) SIZE_UNIT_MB);
    return String.format(SIZE_FORMAT_GB, bytes / (double) SIZE_UNIT_GB);
  }

  /**
   * 获取 Principal 的显示名称
   *
   * @param principal Principal 标识（通常是用户ID）
   * @return 显示名称，如果无法解析则返回原始 principal
   */
  private String getPrincipalDisplayName(String principal) {
    if (principal == null || principal.isEmpty()) {
      return principal;
    }

    // 默认使用 USER 类型的 resolver
    Optional<PrincipalType> principalType = PrincipalTypeProvider.get(PRINCIPAL_TYPE_USER);
    if (principalType.isPresent()) {
      PrincipalResolver resolver = principalType.get().getResolver();
      return resolver.getDisplayName(principal);
    }

    return principal;
  }

  private <T> WebApiResult<T> notFound(String message) {
    return WebApiResult.error(404, message);
  }

  // ==================== Request DTOs ====================

  public static class CreateLibraryRequest {
    @NotBlank
    public String name;
    @NotBlank
    public String displayName;
    public String description;
  }

  public static class UpdateLibraryInfoRequest {
    public String name;
    public String displayName;
    public String description;
  }

  public static class UpdateQuotaRequest {
    public Long maxSizeLimit;
    public Long maxFileCountLimit;
  }

  public static class BatchAdminRequest {
    @NotNull
    public List<String> adminUserIds;
  }

  public static class TransferOwnershipRequest {
    @NotBlank
    public String newOwnerId;
    public boolean keepAsAdmin = false;
  }

  public static class GrantPermissionRequest {
    @NotBlank
    public String principal;
    @NotBlank
    public String principalType;
    @NotNull
    public PermissionSet permissions;
  }
}
