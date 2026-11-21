package com.ouroboros.workflow.approval.runtime;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import com.ouroboros.exception.InternalException;
import com.ouroboros.fieldtype.FieldTypeCatalog;
import com.ouroboros.fieldtype.FieldTypeCatalogContext;
import com.ouroboros.metadata.resource.MetadataSource;
import com.ouroboros.metadata.resource.ClasspathMetadataSource;
import com.ouroboros.metadata.resource.FileSystemMetadataSource;
import com.ouroboros.workflow.approval.form.ApprovalFormMetadataCatalog;
import com.ouroboros.workflow.approval.form.CustomFlowFormProvider;
import com.ouroboros.lifecycle.event.OuroborosMetadataRefreshingEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.PayloadApplicationEvent;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import com.ouroboros.AppContext;
import com.ouroboros.data.annotation.DataModelScan;
import com.ouroboros.logicflow.LogicFlowAutoConfiguration;
import com.ouroboros.logicflow.LogicFlowExecutionFacade;
import com.ouroboros.logicflow.model.FlowModelProvider;
import com.ouroboros.webapi.WebAPIAutoConfiguration;
import com.ouroboros.workflow.approval.BuiltInFlowDefinitionService;
import com.ouroboros.workflow.approval.callback.CallbackBindingProvider;
import com.ouroboros.workflow.approval.definition.startrequirements.BuiltInStartRequirementsResolver;
import com.ouroboros.workflow.approval.flowable.ApprovalWorkflowFlowableAutoConfiguration;
import com.ouroboros.workflow.approval.manage.service.WorkflowModelService;
import com.ouroboros.workflow.approval.provider.StarterCandidateProvider;
import com.ouroboros.workflow.approval.resolver.EmployeeUsernameResolver;
import com.ouroboros.workflow.approval.runtime.callback.ApprovalProcessCallbackBindingCatalog;
import com.ouroboros.workflow.approval.runtime.callback.ApprovalProcessCallbackListener;
import com.ouroboros.workflow.approval.runtime.lifecycle.ApprovalWorkflowDefinitionLayerRefresher;
import com.ouroboros.workflow.approval.runtime.lifecycle.ApprovalWorkflowMetadataDefinitionProvider;
import com.ouroboros.workflow.approval.runtime.notification.ApprovalNotificationListener;
import com.ouroboros.workflow.approval.runtime.startrequirements.DefaultStartRequirementsResolver;
import com.ouroboros.workflow.approval.lifecycle.ApprovalCaseService;

@Configuration
@AutoConfigureAfter({
    com.ouroboros.fieldtype.runtime.FieldTypeRuntimeAutoConfiguration.class,
    LogicFlowAutoConfiguration.class,
    WebAPIAutoConfiguration.class,
    TransactionAutoConfiguration.class,
    ApprovalWorkflowFlowableAutoConfiguration.class})
@DataModelScan({
    "com.ouroboros.workflow.approval.runtime",
    "com.ouroboros.workflow.approval.event",
    "com.ouroboros.workflow.approval.lifecycle"})
@ComponentScan(basePackageClasses = {
    StarterCandidateProvider.class,
    EmployeeUsernameResolver.class,
    ApprovalCaseService.class,
    ApprovalNotificationListener.class})
public class ApprovalWorkflowAdapterAutoConfiguration {

  @Bean
  public com.ouroboros.workflow.approval.service.FlowInstanceFactStateService flowInstanceFactStateService() {
    return new com.ouroboros.workflow.approval.service.FlowInstanceFactStateService();
  }

  private static final Logger logger = LoggerFactory.getLogger(ApprovalWorkflowAdapterAutoConfiguration.class);

  @PostConstruct
  public void init() {
    logger.info("Approval Workflow Runtime Auto Configuration loaded");
  }

  @Bean
  @ConditionalOnBean(BuiltInFlowDefinitionService.class)
  public BuiltInStartRequirementsResolver defaultStartRequirementsResolver(
      BuiltInFlowDefinitionService flowDefinitionService) {
    return new DefaultStartRequirementsResolver(flowDefinitionService);
  }

  @Bean
  @ConditionalOnBean(FlowModelProvider.class)
  public ApprovalProcessCallbackBindingCatalog approvalProcessCallbackBindingCatalog(
      FlowModelProvider flowModelProvider,
      ObjectProvider<CallbackBindingProvider> callbackBindingProviders) {
    return new ApprovalProcessCallbackBindingCatalog(
        flowModelProvider, callbackBindingProviders);
  }

  @Bean
  @ConditionalOnBean({
      LogicFlowExecutionFacade.class,
      PlatformTransactionManager.class,
      ApprovalProcessCallbackBindingCatalog.class})
  public ApprovalProcessCallbackListener approvalProcessCallbackListener(
      LogicFlowExecutionFacade executionFacade,
      PlatformTransactionManager transactionManager,
      ApprovalProcessCallbackBindingCatalog bindingCatalog,
      ObjectProvider<com.ouroboros.workflow.approval.event.ApprovalBusinessFactProjection> projections) {
    return new ApprovalProcessCallbackListener(
        executionFacade, transactionManager, bindingCatalog, projections.orderedStream().toList());
  }

  @Bean
  @ConditionalOnBean({AppContext.class, FieldTypeCatalog.class})
  public CustomFlowFormProvider customFlowFormProvider(
      AppContext appContext, FieldTypeCatalog fieldTypes) {
    var provider = new CustomFlowFormProvider(() -> {
      String home = appContext.getHomePath();
      if (home == null || home.isBlank()) {
        throw new InternalException("AppContext.homePath 不能为空，无法读取自定义审批表单");
      }
      var directory = Path.of(home, ApprovalFormMetadataCatalog.DIRECTORY);
      MetadataSource application = spec -> List.of();
      if (Files.exists(directory)) {
        if (!Files.isDirectory(directory)) {
          throw new InternalException("自定义审批表单路径不是目录: " + directory);
        }
        application = new FileSystemMetadataSource(directory);
      }
      return ApprovalFormMetadataCatalog.load(
          new ClasspathMetadataSource(), application);
    }, fieldTypes, FieldTypeCatalogContext.runtime());
    provider.search(null);
    return provider;
  }

  @Bean
  @ConditionalOnBean(CustomFlowFormProvider.class)
  public ApplicationListener<PayloadApplicationEvent<OuroborosMetadataRefreshingEvent>> approvalFormMetadataValidator(
      CustomFlowFormProvider provider) {
    return ApplicationListener.forPayload(event -> provider.search(null));
  }

  @Bean
  @ConditionalOnBean(AppContext.class)
  public ApprovalWorkflowMetadataDefinitionProvider approvalWorkflowMetadataDefinitionProvider(AppContext appContext) {
    return new ApprovalWorkflowMetadataDefinitionProvider(appContext);
  }

  @Bean
  @ConditionalOnBean({ApprovalWorkflowMetadataDefinitionProvider.class, WorkflowModelService.class})
  public ApprovalWorkflowDefinitionLayerRefresher approvalWorkflowDefinitionLayerRefresher(
      ApprovalWorkflowMetadataDefinitionProvider definitionProvider,
      WorkflowModelService workflowModelService) {
    return new ApprovalWorkflowDefinitionLayerRefresher(definitionProvider, workflowModelService);
  }
}
