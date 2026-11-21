package com.ouroboros.data.initialization.runtime;

import java.nio.file.Path;
import java.util.List;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import com.ouroboros.AppContext;
import com.ouroboros.data.annotation.DataModelScan;
import com.ouroboros.data.initialization.DataInitializationReconciler;
import com.ouroboros.data.initialization.InitializationLedger;
import com.ouroboros.data.initialization.InitializationLock;
import com.ouroboros.data.initialization.InitializationPlanProvider;
import com.ouroboros.data.initialization.InitializationTransaction;
import com.ouroboros.data.initialization.TargetDatasetGateway;
import com.ouroboros.lock.DistributedLockManager;

@Configuration
@DataModelScan("com.ouroboros.data.initialization.runtime.model")
public class DataInitializationAutoConfiguration {
  private static final Logger logger = LoggerFactory.getLogger(DataInitializationAutoConfiguration.class);

  @PostConstruct
  public void init() {
    logger.info("Ouroboros Data Initialization Auto Configuration loaded");
  }

  @Bean
  @ConditionalOnMissingBean(ResourceDataInitializationPlanProvider.class)
  public ResourceDataInitializationPlanProvider resourceDataInitializationPlanProvider(
      ObjectProvider<AppContext> appContextProvider) {
    Path appHomePath = appContextProvider.stream()
        .findFirst()
        .map(AppContext::getHomePath)
        .map(Path::of)
        .orElse(null);
    return new ResourceDataInitializationPlanProvider(appHomePath);
  }

  @Bean
  @ConditionalOnMissingBean(TargetDatasetGateway.class)
  public TargetDatasetGateway dataModelTargetDatasetGateway() {
    return new DataModelTargetDatasetGateway();
  }

  @Bean
  @ConditionalOnMissingBean(InitializationLedger.class)
  public InitializationLedger dataModelInitializationLedger() {
    return new DataModelInitializationLedger();
  }

  @Bean
  @ConditionalOnMissingBean(InitializationLock.class)
  public InitializationLock distributedInitializationLock(DistributedLockManager lockManager) {
    return new DistributedInitializationLock(lockManager);
  }

  @Bean
  @ConditionalOnMissingBean(DistributedLockManager.class)
  public DistributedLockManager dataInitializationDistributedLockManager() {
    return DistributedLockManager.getLockManager();
  }

  @Bean
  @ConditionalOnMissingBean(InitializationTransaction.class)
  public InitializationTransaction dataInitializationTransaction(PlatformTransactionManager transactionManager) {
    return new SpringInitializationTransaction(transactionManager);
  }

  @Bean
  @ConditionalOnMissingBean(DataInitializationReconciler.class)
  public DataInitializationReconciler dataInitializationReconciler(List<InitializationPlanProvider> planProviders,
                                                                   TargetDatasetGateway targetDatasetGateway,
                                                                   InitializationLedger ledger,
                                                                   InitializationLock lock,
                                                                   InitializationTransaction transaction) {
    return new DataInitializationReconciler(planProviders, targetDatasetGateway, ledger, lock, transaction);
  }

  @Bean
  @ConditionalOnMissingBean(DataInitializationLifecycleAdapter.class)
  public DataInitializationLifecycleAdapter dataInitializationLifecycleAdapter(DataInitializationReconciler reconciler) {
    return new DataInitializationLifecycleAdapter(reconciler);
  }
}
