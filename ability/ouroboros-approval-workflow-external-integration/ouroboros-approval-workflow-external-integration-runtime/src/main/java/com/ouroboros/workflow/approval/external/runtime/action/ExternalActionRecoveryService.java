package com.ouroboros.workflow.approval.external.runtime.action;

import java.util.List;

import org.springframework.stereotype.Service;

/** 领取可恢复动作事实并使用原 factId 重放或只补本地状态。 */
@Service
public class ExternalActionRecoveryService {

  private final ExternalActionFactService factService;
  private final ExternalActionExecutionService executionService;

  public ExternalActionRecoveryService(
      ExternalActionFactService factService, ExternalActionExecutionService executionService) {
    this.factService = factService;
    this.executionService = executionService;
  }

  public int recoverDue(int limit, int leaseSeconds) {
    List<ExternalActionFactService.RecoveryClaim> claims = factService.claimRecoverable(limit, leaseSeconds);
    int recovered = 0;
    for (ExternalActionFactService.RecoveryClaim claim : claims) {
      if (executionService.recover(claim).isSuccess()) {
        recovered++;
      }
    }
    return recovered;
  }
}
