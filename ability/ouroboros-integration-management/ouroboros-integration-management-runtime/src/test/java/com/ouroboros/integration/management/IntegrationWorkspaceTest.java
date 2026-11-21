package com.ouroboros.integration.management;

import static org.assertj.core.api.Assertions.*;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import com.ouroboros.integration.management.IntegrationManagementPage.Section;

class IntegrationWorkspaceTest {
  @Test
  void assemblesOnlyAuthorizedPagesInTheRequestedSectionWithoutFetchingDomainData() {
    var workspace = new IntegrationWorkspace(List.of(() -> List.of(
        page("ldap", Section.CONNECTIONS, 20, "login"),
        page("clients", Section.APPLICATIONS, 10, "clients"),
        page("server", Section.APPLICATIONS, 20, "settings"))));
    String connections = workspace.schema(Section.CONNECTIONS, "login"::equals).toString();
    assertThat(connections).contains("ldap", "/schema/ldap").doesNotContain("clients", "server");
    String settings = workspace.schema(Section.APPLICATIONS, "settings"::equals).toString();
    assertThat(settings).contains("server").doesNotContain("clients", "ldap");
    assertThat(workspace.schema(Section.APPLICATIONS, authority -> false)).containsEntry("type", "tpl");
  }

  @Test
  void diagnosesDuplicateContributionsAndCompetingEditorsAtStartup() {
    var page = page("ldap", Section.CONNECTIONS, 10, "login");
    assertThatThrownBy(() -> new IntegrationWorkspace(List.of(() -> List.of(page, page))))
        .hasMessageContaining("重复");
    var alias = new IntegrationManagementPage("alias", Section.APPLICATIONS, "Alias", page.schemaPath(), 30, Set.of("clients"));
    assertThatThrownBy(() -> new IntegrationWorkspace(List.of(() -> List.of(page, alias))))
        .hasMessageContaining("重复");
  }

  @Test
  void emptyHostStartsWithoutOidcOrEnterpriseProvidersAndAcceptsAnInstalledContribution() {
    new ApplicationContextRunner().withUserConfiguration(IntegrationManagementAutoConfiguration.class)
        .run(context -> {
          assertThat(context).hasSingleBean(IntegrationWorkspace.class).hasSingleBean(IntegrationManagementController.class);
          assertThat(context.getBean(IntegrationWorkspace.class).schema(Section.CONNECTIONS, authority -> true))
              .containsEntry("type", "tpl");
        });
    new ApplicationContextRunner().withUserConfiguration(IntegrationManagementAutoConfiguration.class)
        .withBean(IntegrationManagementContributor.class, () -> () -> List.of(page("ldap", Section.CONNECTIONS, 10, "login")))
        .run(context -> assertThat(context.getBean(IntegrationWorkspace.class).schema(Section.CONNECTIONS, authority -> true).toString())
            .contains("/schema/ldap"));
  }

  private IntegrationManagementPage page(String id, Section section, int order, String authority) {
    return new IntegrationManagementPage(id, section, id, "/schema/" + id, order, Set.of(authority));
  }
}
