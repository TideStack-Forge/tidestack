package com.ouroboros.workflow.approval.external.wecom;

import java.util.List;
import java.util.Map;

import com.ouroboros.workflow.approval.external.ExternalFormSchema;
import com.ouroboros.workflow.approval.external.ExternalInstanceSnapshot;
import com.ouroboros.workflow.approval.external.ExternalProcessDefinition;

import io.vavr.control.Try;

/** Enterprise WeChat approval HTTP boundary used by the adapter and contract tests. */
public interface WecomOpenApiClient {

  Try<String> accessToken();

  Try<ExternalProcessDefinition> validateTemplate(String templateId);

  Try<ExternalFormSchema> getTemplateSchema(String templateId);

  Try<String> startApproval(
      String creatorUserId,
      String templateId,
      List<Map<String, Object>> contents,
      String summary);

  Try<ExternalInstanceSnapshot> getApprovalDetail(String approvalNumber);
}
