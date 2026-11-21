package com.ouroboros.organizationstructure.controller;

import com.ouroboros.organizationstructure.dto.response.OrganizationNodeDTO;
import com.ouroboros.organizationstructure.service.OrganizationQueryFacade;
import com.ouroboros.web.ItemsWebApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.annotation.security.PermitAll;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 组织架构树查询 Controller
 */
@RestController
@RequestMapping("/api/organization")
public class OrganizationTreeController {

  private final OrganizationQueryFacade queryFacade;

  public OrganizationTreeController(OrganizationQueryFacade queryFacade) {
    this.queryFacade = queryFacade;
  }

  /**
   * 获取组织架构树
   *
   * @param rootId           根节点ID（null表示所有根节点）
   * @param includePositions 是否包含岗位节点
   * @param types            节点类型过滤
   * @param type             选择场景类型（position/department/company）
   * @return 树形结构的节点列表
   */
  @Operation(summary = "获取组织架构树", description = "按根节点、节点类型和是否包含岗位等条件获取组织架构树。", tags = {"organization"})
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回组织架构树"),
      @ApiResponse(responseCode = "404", description = "组织节点不存在")
  })
  @GetMapping("/tree")
  @PermitAll
  public ItemsWebApiResult<OrganizationNodeDTO> getTree(
      @Parameter(description = "根节点 ID")
      @RequestParam(name = "rootId", required = false) String rootId,
      @Parameter(description = "是否包含岗位节点")
      @RequestParam(name = "includePositions", defaultValue = "false") boolean includePositions,
      @Parameter(description = "节点类型过滤列表")
      @RequestParam(name = "types", required = false) List<String> types,
      @Parameter(description = "树选择场景类型")
      @RequestParam(name = "type", required = false) String type) {
    
    List<OrganizationNodeDTO> dtos = queryFacade.buildTree(rootId, includePositions, types, type);
    return ItemsWebApiResult.success(dtos);
  }
}
