package com.ouroboros.workflow.approval.dev.controller;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.fieldtype.FieldTypeCatalog;
import com.ouroboros.fieldtype.FieldTypeCatalogContext;
import com.ouroboros.metadata.resource.ClasspathMetadataSource;
import com.ouroboros.metadata.resource.MetadataResource;
import com.ouroboros.vcs.VCSManagerCenter;
import com.ouroboros.web.ItemsWebApiResult;
import com.ouroboros.web.PagedWebApiResult;
import com.ouroboros.web.WebApiResult;
import com.ouroboros.workflow.approval.form.ApprovalFormMetadataCatalog;
import com.ouroboros.workflow.approval.form.CustomFlowFormProvider;
import com.ouroboros.workflow.approval.form.FlowFormSourceAssembler;

@RestController
@RequestMapping("/api/dev/{appName}/approval-workflow/base-info")
public class ApprovalWorkflowDesignTimeFormController {
  private final VCSManagerCenter vcsManagers;
  private final FieldTypeCatalog fieldTypes;
  private final com.ouroboros.businessmodel.dev.service.BusinessModelApprovalFieldSourceService businessModels;

  public ApprovalWorkflowDesignTimeFormController(VCSManagerCenter vcsManagers, FieldTypeCatalog fieldTypes,
      com.ouroboros.businessmodel.dev.service.BusinessModelApprovalFieldSourceService businessModels) {
    this.vcsManagers = vcsManagers;
    this.fieldTypes = fieldTypes;
    this.businessModels = businessModels;
  }

  @GetMapping("/form-types")
  public ItemsWebApiResult<Map<String, String>> types(@PathVariable String appName, @RequestParam(required = false) String value) {
    requireApplication(appName);
    var options = new ArrayList<>(List.of(Map.of("value", "business-model", "label", "业务模型"), Map.of("value", "custom", "label", "自定义表单")));
    if (value != null && !value.isBlank() && !List.of("business-model", "custom").contains(value)) {
      options.add(Map.of("value", value, "label", value + "（当前来源不可用）"));
    }
    return ItemsWebApiResult.ok(options);
  }

  @GetMapping("/form/{type}")
  public Object forms(@PathVariable String appName, @PathVariable String type,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String value,
      @RequestParam(defaultValue = "1") Integer page,
      @RequestParam(required = false) Integer perPage) {
    requireApplication(appName);
    if ("business-model".equals(type)) {
      var forms = businessModels.forms(keyword, value);
      if (perPage == null) {
        return ItemsWebApiResult.ok(forms);
      }
      if (page < 1 || perPage < 1) {
        throw new BusinessException("表单查询页码与每页条数必须为正数");
      }
      return PagedWebApiResult.ok((long) forms.size(), forms.stream().skip((long) (page - 1) * perPage).limit(perPage).toList());
    }
    var provider = provider(appName, type);
    if (value != null && !value.isBlank()) {
      var selected = provider.get(value).stream().toList();
      return perPage == null ? ItemsWebApiResult.ok(selected) : PagedWebApiResult.ok((long) selected.size(), selected);
    }
    if (perPage == null) {
      return ItemsWebApiResult.ok(provider.search(keyword));
    }
    var selected = provider.search(keyword, page, perPage);
    return PagedWebApiResult.ok(selected._1, selected._2);
  }

  @GetMapping("/form/{type}/{name}/workflow-field")
  public WebApiResult<Map<String, Object>> fields(@PathVariable String appName, @PathVariable String type,
      @PathVariable String name) {
    requireApplication(appName);
    if ("business-model".equals(type)) {
      return WebApiResult.ok(businessModels.source(appName, type, name, null));
    }
    return provider(appName, type).get(name)
        .map(form -> WebApiResult.ok(new FlowFormSourceAssembler().build(type, name, form)))
        .orElseGet(() -> WebApiResult.error("自定义审批表单不存在: " + name));
  }

  @GetMapping("/workflow-field")
  public WebApiResult<Map<String, Object>> boundFields(@PathVariable String appName,
      @RequestParam(required = false) String formType, @RequestParam(required = false) String formKey,
      @RequestParam(required = false) String definitionId) {
    requireApplication(appName);
    if (formType == null || formType.isBlank() || "business-model".equals(formType)) {
      return WebApiResult.ok(businessModels.source(appName, formType, formKey, definitionId));
    }
    return fields(appName, formType, formKey);
  }

  private CustomFlowFormProvider provider(String appName, String type) {
    if (!CustomFlowFormProvider.TYPE.equals(type)) {
      throw new BusinessException("此入口不支持表单来源: " + type);
    }
    var vcs = requireApplication(appName);
    return new CustomFlowFormProvider(() -> ApprovalFormMetadataCatalog.load(new ClasspathMetadataSource(), spec ->
        vcs.walkDirectory(ApprovalFormMetadataCatalog.DIRECTORY, ApprovalFormMetadataCatalog.SUFFIX)
            .map(file -> new MetadataResource(file.getPath(), file.getContent())).toList()),
        fieldTypes, FieldTypeCatalogContext.devApp(appName));
  }

  private com.ouroboros.vcs.VCSManager requireApplication(String appName) {
    var vcs = vcsManagers.getVcsManager(appName);
    if (vcs == null) {
      throw new BusinessException("应用元数据工作区不可用: " + appName);
    }
    return vcs;
  }
}
