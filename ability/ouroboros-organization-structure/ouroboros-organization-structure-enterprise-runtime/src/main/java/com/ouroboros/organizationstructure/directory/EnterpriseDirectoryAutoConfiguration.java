package com.ouroboros.organizationstructure.directory;

import com.ouroboros.organizationstructure.directory.service.EnterpriseDirectoryService;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import com.ouroboros.data.annotation.DataModelScan;
import com.ouroboros.data.model.TypedDataModel;
import com.ouroboros.enterprise.directory.EnterpriseDirectorySource;
import com.ouroboros.enterprise.runtime.EnterpriseConnectionCatalog;
import com.ouroboros.organizationstructure.service.*;

@Configuration(proxyBeanMethods = false)
@DataModelScan
@Import(EnterpriseDirectoryController.class)
public class EnterpriseDirectoryAutoConfiguration {
  @Bean DirectoryRetentionJob directoryRetentionJob(DirectoryStore store, com.ouroboros.configuration.ConfigurationService configuration) {
    return new DirectoryRetentionJob(store, configuration);
  }
  @Bean DirectoryIntegrationContributor directoryIntegrationContributor() { return new DirectoryIntegrationContributor(); }
  @Bean DirectoryMemberResolver directoryMemberResolver(DirectoryStore store, com.ouroboros.security.rbac.UserSubjectCenter subjects) {
    return new DirectoryMemberResolver(store, subjects);
  }
  @Bean DirectoryMutationPolicy directoryMutationPolicy(DirectoryStore store) { return new DirectoryMutationPolicy(store); }
  @Bean DirectorySources directorySources(List<EnterpriseDirectorySource> sources) { return new DirectorySources(sources); }
  @Bean DirectoryStore directoryStore(TypedDataModel<String, DirectoryBinding> bindings,
      TypedDataModel<String, DirectorySyncScope> scopes, TypedDataModel<String, DirectorySyncRun> runs) {
    return new DirectoryStore(bindings, scopes, runs);
  }
  @Bean DirectoryOrganization directoryOrganization(OrganizationFacadeService organization, StructureManageService nodes,
      EmployeeManageService employees, PositionManageService positions, AppointmentManageService appointments) {
    return new DirectoryOrganization(organization, nodes, employees, positions, appointments);
  }
  @Bean DirectoryBindingCommands directoryBindingCommands(DirectoryStore store, DirectoryOrganization organization,
      EnterpriseConnectionCatalog connections) { return new DirectoryBindingCommands(store, organization, connections); }
  @Bean DirectorySyncCommands directorySyncCommands(DirectoryStore store, DirectoryOrganization organization,
      EnterpriseConnectionCatalog connections) { return new DirectorySyncCommands(store, organization, connections); }
  @Bean EnterpriseDirectoryService enterpriseDirectoryService(EnterpriseConnectionCatalog connections, DirectorySources sources,
      DirectoryStore store, DirectoryBindingCommands bindings, DirectorySyncCommands synchronization) {
    return new EnterpriseDirectoryService(connections, sources, store, bindings, synchronization);
  }
}
