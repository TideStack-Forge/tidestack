package com.ouroboros.workflow.approval.manage.service;

import java.util.Optional;

import com.ouroboros.exception.OuroborosException;
import com.ouroboros.workflow.approval.definition.metadata.ApprovalWorkflowDefinitionMetadata;

public interface ApprovalWorkflowDesignTimeDefinitionProvider {
  Optional<ApprovalWorkflowDefinitionMetadata> findDefinition(String id) throws OuroborosException;
}
