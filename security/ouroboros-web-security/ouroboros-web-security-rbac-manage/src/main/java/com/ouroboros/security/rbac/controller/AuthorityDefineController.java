package com.ouroboros.security.rbac.controller;

import java.beans.Transient;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import com.ouroboros.appmodule.AppModuleIntegrator;
import com.ouroboros.security.authority.Authority;
import com.ouroboros.security.authority.AuthorityCenter;
import com.ouroboros.security.rbac.util.AccessControlDefineUtils;
import com.ouroboros.uischema.schema.SchemaNode;
import com.ouroboros.util.TreeNode;
import com.ouroboros.util.Trees;
import com.ouroboros.web.ItemsWebApiResult;
import com.ouroboros.web.WebApiResult;

/**
 * @author liansz
 */
@RestController
@RequestMapping("/api/sys/authority")
public class AuthorityDefineController {

  private final AppModuleIntegrator appModuleIntegrator;

  public AuthorityDefineController(ObjectProvider<AppModuleIntegrator> appModuleIntegrator) {
    this.appModuleIntegrator = appModuleIntegrator.getIfAvailable(() -> Collections::emptyList);
  }

  @Operation(
      summary = "获取权限定义详情",
      description = "按权限全名查询单个权限定义。",
      tags = {"rbac-admin"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回权限定义")
  })
  @GetMapping("/define/{fullName}")
  public WebApiResult<Authority> get(
      @Parameter(description = "权限全名", required = true) @PathVariable("fullName") String fullName) {
    return WebApiResult.of(AuthorityCenter.getAuthority(fullName));
  }

  @Operation(
          summary = "获取权限定义树",
          description = "返回平台内所有权限定义的树形结构。",
          tags = {"rbac-admin"}
  )
  @ApiResponses({
          @ApiResponse(responseCode = "200", description = "成功返回权限定义树")
  })
  @GetMapping("/define-tree")
  public ItemsWebApiResult<AuthorityDefineTreeNode> tree() {

    var rawAuthorityMap = AuthorityCenter.getAllAuthority();

    var authorityDefineTreeNodes = appModuleIntegrator
            .rearrangeAuthorities(rawAuthorityMap)
            .stream()
            .map(AuthorityDefineTreeNode::new)
            .collect(Collectors.toList());

    var roots = authorityDefineTreeNodes
            .stream()
            .filter(node -> ObjectUtils.isEmpty(node.getParent()))
            .collect(Collectors.toList());

    Function<AuthorityDefineTreeNode, List<AuthorityDefineTreeNode>> childrenFinder = auth -> authorityDefineTreeNodes
            .stream()
            .filter(node -> Objects.equals(node.getParent(), auth.getFullName()))
            .collect(Collectors.toList());

    BiFunction<AuthorityDefineTreeNode, List<AuthorityDefineTreeNode>, AuthorityDefineTreeNode> childrenSetter = (auth, children) -> {
      auth.setChildren(children);
      return auth;
    };

    var authorityTree = Trees.buildTree(roots, childrenFinder, childrenSetter);

    return ItemsWebApiResult.ok(authorityTree);
  }

  public static class AuthorityDefineTreeNode implements TreeNode<AuthorityDefineTreeNode> {
    private List<AuthorityDefineTreeNode> children;
    private final Authority authority;

    AuthorityDefineTreeNode(Authority authority) {
      this.authority = authority;
    }

    public String getFullName() {
      return authority.getFullName();
    }

    @Transient
    public String getParent() {
      return authority.getParent();
    }

    @Transient
    public String getName() {
      return authority.getName();
    }

    public String getTitle() {
      return authority.getTitle();
    }

    public Integer getOrder() {
      return authority.getOrder();
    }

    @Override
    public List<AuthorityDefineTreeNode> getChildren() {
      return children;
    }

    public List<SchemaNode> getAccessControlSettingSchema() {
      return AccessControlDefineUtils.generateAccessControlSettingSchema(authority);
    }

    public void setChildren(List<AuthorityDefineTreeNode> children) {
      this.children = children;
    }
  }
}
