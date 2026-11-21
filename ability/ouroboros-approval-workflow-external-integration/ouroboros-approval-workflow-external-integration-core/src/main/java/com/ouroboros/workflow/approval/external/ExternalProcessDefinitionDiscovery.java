package com.ouroboros.workflow.approval.external;

import io.vavr.control.Try;

/** 外部流程定义发现端口。实现该端口才可声明 {@code DEFINITION_DISCOVERY}。 */
public interface ExternalProcessDefinitionDiscovery {

  Try<ExternalProcessDefinitionPage> discoverDefinitions(DefinitionDiscoveryRequest request);
}
