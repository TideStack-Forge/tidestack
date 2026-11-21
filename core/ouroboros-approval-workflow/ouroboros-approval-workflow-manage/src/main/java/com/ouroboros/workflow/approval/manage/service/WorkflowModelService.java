package com.ouroboros.workflow.approval.manage.service;

import java.util.List;

import com.ouroboros.common.service.ModelService;
import com.ouroboros.exception.OuroborosException;
import com.ouroboros.web.PagedWebApiResult;
import com.ouroboros.workflow.approval.definition.metadata.ApprovalWorkflowDefinitionMetadata;
import com.ouroboros.workflow.approval.manage.entity.WorkflowModel;

public interface WorkflowModelService extends ModelService<String, WorkflowModel> {
  PagedWebApiResult<WorkflowModel> searchByPage(String keyword, String status, int page, int pageSize) throws OuroborosException;

  PagedWebApiResult<WorkflowModel> searchAvailableByPage(String keyword, String category, int page, int pageSize) throws OuroborosException;

  void deploy(String id) throws OuroborosException;

  void offline(String id) throws OuroborosException;

  void reset(String id) throws OuroborosException;

  int refreshDefinitionLayers(List<ApprovalWorkflowDefinitionMetadata> definitions) throws OuroborosException;
}
