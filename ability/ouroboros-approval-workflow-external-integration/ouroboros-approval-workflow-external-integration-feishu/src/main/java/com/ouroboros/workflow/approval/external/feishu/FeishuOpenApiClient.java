package com.ouroboros.workflow.approval.external.feishu;

import com.ouroboros.workflow.approval.external.ExternalFormSchema;
import com.ouroboros.workflow.approval.external.ExternalInstanceSnapshot;
import com.ouroboros.workflow.approval.external.ExternalProcessDefinition;
import com.ouroboros.workflow.approval.external.ExternalStartRecoveryResult;

import io.vavr.control.Try;

/** Feishu native approval HTTP boundary used by the adapter and contract tests. */
public interface FeishuOpenApiClient {

  Try<String> accessToken();

  Try<ExternalProcessDefinition> validateApproval(String approvalCode);

  Try<ExternalFormSchema> getApprovalForm(String approvalCode);

  Try<String> createInstance(
      String approvalCode,
      String externalUserId,
      String externalDepartmentId,
      String formJson,
      String uuid);

  Try<ExternalInstanceSnapshot> getInstance(String instanceIdentity);

  Try<ExternalStartRecoveryResult> recoverStart(String uuid);
}
