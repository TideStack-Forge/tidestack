package com.ouroboros.workflow.approval.external;

import io.vavr.control.Try;

/** 外部流程定义验证端口。验证不等同于定义发现。 */
public interface ExternalProcessDefinitionValidator {

  Try<ExternalProcessDefinition> validateDefinition(ExternalProcessDefinitionRef definition);
}
