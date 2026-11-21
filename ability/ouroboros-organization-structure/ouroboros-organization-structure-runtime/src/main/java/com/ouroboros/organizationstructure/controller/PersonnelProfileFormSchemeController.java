package com.ouroboros.organizationstructure.controller;

import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ouroboros.exception.OuroborosException;
import com.ouroboros.organizationstructure.profile.entity.PersonnelProfileFormSchemeEntity;
import com.ouroboros.organizationstructure.profile.service.PersonnelProfileFormSchemeService;
import com.ouroboros.web.ItemsWebApiResult;
import com.ouroboros.web.WebApiResult;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

@RestController
@RequestMapping("/api/dev/{appName}/organization-management/personnel-profile-form-scheme")
public class PersonnelProfileFormSchemeController {

  private final PersonnelProfileFormSchemeService personnelProfileFormSchemeService;

  public PersonnelProfileFormSchemeController(PersonnelProfileFormSchemeService personnelProfileFormSchemeService) {
    this.personnelProfileFormSchemeService = personnelProfileFormSchemeService;
  }

  @Operation(summary = "列出人员档案表单方案", description = "按应用查询人员档案表单方案列表。", tags = {"organization"})
  @ApiResponses({@ApiResponse(responseCode = "200", description = "成功返回人员档案表单方案列表")})
  @GetMapping
  public ItemsWebApiResult<PersonnelProfileFormSchemeEntity> list(@PathVariable("appName") String appName) throws OuroborosException {
    return ItemsWebApiResult.success(personnelProfileFormSchemeService.searchAll(appName));
  }

  @Operation(summary = "获取人员档案表单方案详情", description = "根据 ID 获取人员档案表单方案详情。", tags = {"organization"})
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回人员档案表单方案详情"),
      @ApiResponse(responseCode = "404", description = "人员档案表单方案不存在")
  })
  @GetMapping("/{id}")
  public WebApiResult<PersonnelProfileFormSchemeEntity> detail(
      @PathVariable("appName") String appName,
      @PathVariable("id") String id) throws OuroborosException {
    return personnelProfileFormSchemeService.getById(id, appName)
        .map(WebApiResult::success)
        .orElse(WebApiResult.error(404, "人员档案表单方案不存在"));
  }

  @Operation(summary = "创建人员档案表单方案", description = "新增一个人员档案表单方案。", tags = {"organization"})
  @ApiResponses({@ApiResponse(responseCode = "200", description = "人员档案表单方案创建成功")})
  @PostMapping
  public WebApiResult<PersonnelProfileFormSchemeEntity> create(
      @PathVariable("appName") String appName,
      @RequestBody PersonnelProfileFormSchemeEntity entity) throws OuroborosException {
    entity.setAppName(StringUtils.trimToNull(appName));
    return WebApiResult.success(personnelProfileFormSchemeService.create(entity, currentUser()));
  }

  @Operation(summary = "更新人员档案表单方案", description = "根据 ID 更新人员档案表单方案。", tags = {"organization"})
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "人员档案表单方案更新成功"),
      @ApiResponse(responseCode = "404", description = "人员档案表单方案不存在")
  })
  @PutMapping("/{id}")
  public WebApiResult<Boolean> update(
      @PathVariable("appName") String appName,
      @PathVariable("id") String id,
      @RequestBody PersonnelProfileFormSchemeEntity entity) throws OuroborosException {
    if (!personnelProfileFormSchemeService.getById(id, appName).isPresent()) {
      return WebApiResult.error(404, "人员档案表单方案不存在");
    }
    entity.setAppName(StringUtils.trimToNull(appName));
    return WebApiResult.success(personnelProfileFormSchemeService.update(id, entity, currentUser()));
  }

  @Operation(summary = "删除人员档案表单方案", description = "根据 ID 删除人员档案表单方案。", tags = {"organization"})
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "人员档案表单方案删除成功"),
      @ApiResponse(responseCode = "404", description = "人员档案表单方案不存在")
  })
  @DeleteMapping("/{id}")
  public WebApiResult<Boolean> delete(
      @PathVariable("appName") String appName,
      @PathVariable("id") String id) throws OuroborosException {
    if (!personnelProfileFormSchemeService.getById(id, appName).isPresent()) {
      return WebApiResult.error(404, "人员档案表单方案不存在");
    }
    return WebApiResult.success(personnelProfileFormSchemeService.delete(id, currentUser()));
  }

  private String currentUser() {
    return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
        .map(Authentication::getPrincipal)
        .map(Object::toString)
        .filter(StringUtils::isNotBlank)
        .orElse("anonymous");
  }
}
