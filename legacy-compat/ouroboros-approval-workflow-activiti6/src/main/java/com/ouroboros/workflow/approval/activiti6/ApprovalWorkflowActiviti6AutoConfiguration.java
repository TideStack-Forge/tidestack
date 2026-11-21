package com.ouroboros.workflow.approval.activiti6;

import jakarta.annotation.PostConstruct;
import javax.sql.DataSource;

import org.activiti.engine.ProcessEngineConfiguration;
import org.activiti.engine.impl.history.HistoryLevel;
import org.activiti.spring.SpringProcessEngineConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;

import com.ouroboros.workflow.approval.ApprovalWorkflowAutoConfiguration;
import com.ouroboros.workflow.approval.activiti6.config.CustomExpressionManager;

@Configuration
@ConditionalOnProperty(name = "ouroboros.workflow.approval.engine", havingValue = "activiti6")
@Import(ApprovalWorkflowAutoConfiguration.class)
@ComponentScan
public class ApprovalWorkflowActiviti6AutoConfiguration {
  private static final Logger logger = LoggerFactory.getLogger(ApprovalWorkflowActiviti6AutoConfiguration.class);

  @Autowired
  public void configEngine(SpringProcessEngineConfiguration configuration, DataSource dataSource, PlatformTransactionManager transactionManager) {
    configuration.setHistoryLevel(HistoryLevel.FULL);
    configuration.setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE);
    configuration.setDataSource(dataSource);
    configuration.setTransactionManager(transactionManager);
    configuration.setExpressionManager(new CustomExpressionManager());
  }

  @PostConstruct
  public void init() {
    logger.info("Approval Workflow Activiti6 engine Auto Configuration loaded");
  }
}
