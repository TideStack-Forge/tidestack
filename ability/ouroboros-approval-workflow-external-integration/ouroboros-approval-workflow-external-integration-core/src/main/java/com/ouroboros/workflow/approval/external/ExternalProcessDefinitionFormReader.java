package com.ouroboros.workflow.approval.external;

import io.vavr.control.Try;

/** 外部流程定义表单读取端口。 */
public interface ExternalProcessDefinitionFormReader {

  Try<ExternalFormSchema> getDefinitionForm(ExternalProcessDefinitionRef definition);
}
