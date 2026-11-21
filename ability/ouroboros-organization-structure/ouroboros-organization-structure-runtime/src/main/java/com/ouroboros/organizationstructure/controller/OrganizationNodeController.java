package com.ouroboros.organizationstructure.controller;

import com.ouroboros.organizationstructure.OrganizationNode;
import com.ouroboros.organizationstructure.dto.response.OrganizationNodeDTO;
import com.ouroboros.organizationstructure.service.OrganizationQueryFacade;
import com.ouroboros.web.ItemsWebApiResult;
import com.ouroboros.web.WebApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 组织节点查询控制器
 * 
 * <p>仅提供节点查询功能，CRUD操作请使用 OrganizationManagementController
 */
@RestController
@RequestMapping("/api/organization/nodes")
public class OrganizationNodeController {

  private final OrganizationQueryFacade queryFacade;

  public OrganizationNodeController(OrganizationQueryFacade queryFacade) {
    this.queryFacade = queryFacade;
  }

  /**
   * 获取节点详情（通用节点查询，用于不确定节点类型的场景）
   */
  @Operation(summary = "获取组织节点详情", description = "根据节点 ID 获取组织节点详情。", tags = {"organization"})
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回组织节点详情"),
      @ApiResponse(responseCode = "404", description = "组织节点不存在")
  })
  @GetMapping("/{id}")
  public WebApiResult<OrganizationNode> getById(
      @Parameter(description = "组织节点 ID", required = true)
      @PathVariable("id") String id) {
    return queryFacade.getNodeById(id)
        .map(WebApiResult::success)
        .orElse(WebApiResult.error(404, "组织节点不存在"));
  }

  /**
   * 获取子节点列表（树形导航）
   */
  @Operation(summary = "获取组织节点子节点", description = "根据节点 ID 获取其直接子节点列表。", tags = {"organization"})
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回组织节点子节点"),
      @ApiResponse(responseCode = "404", description = "组织节点不存在")
  })
  @GetMapping("/{id}/children")
  public ItemsWebApiResult<OrganizationNodeDTO> getChildren(
      @Parameter(description = "组织节点 ID", required = true)
      @PathVariable("id") String id) {
    return ItemsWebApiResult.success(queryFacade.getNodeChildrenDTO(id));
  }

  /**
   * 获取节点路径（面包屑导航）
   */
  @Operation(summary = "获取组织节点路径", description = "根据节点 ID 获取从根节点到当前节点的路径。", tags = {"organization"})
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回组织节点路径"),
      @ApiResponse(responseCode = "404", description = "组织节点不存在")
  })
  @GetMapping("/{id}/path")
  public ItemsWebApiResult<OrganizationNode> getPath(
      @Parameter(description = "组织节点 ID", required = true)
      @PathVariable("id") String id) {
    List<OrganizationNode> path = queryFacade.getNodePath(id);
    
    return ItemsWebApiResult.success(path);
  }
}
