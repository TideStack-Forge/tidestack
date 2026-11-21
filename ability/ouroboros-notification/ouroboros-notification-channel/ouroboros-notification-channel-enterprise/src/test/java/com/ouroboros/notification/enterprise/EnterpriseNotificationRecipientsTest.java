package com.ouroboros.notification.enterprise;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.*;
import com.ouroboros.enterprise.*;
import com.ouroboros.enterprise.runtime.*;
import com.ouroboros.organizationstructure.directory.*;

class EnterpriseNotificationRecipientsTest {
  private final EnterpriseConnectionCatalog catalog = mock(EnterpriseConnectionCatalog.class);
  private final DirectoryMemberResolver members = mock(DirectoryMemberResolver.class);
  private EnterpriseNotificationRecipients recipients;
  private final DirectoryBinding binding = new DirectoryBinding();

  @BeforeEach void setup() {
    var provider = mock(EnterpriseConnectionProvider.class);
    when(provider.provider()).thenReturn("feishu"); when(provider.connectionIdentityFields()).thenReturn(Set.of("corpId"));
    recipients = new EnterpriseNotificationRecipients("feishu", catalog, EnterpriseConnectionProviderRegistry.of(List.of(provider)), members);
    when(catalog.listSummaries()).thenReturn(List.of(new EnterpriseConnectionCatalog.ConnectionSummary("main", "feishu")));
    when(catalog.resolve("main", "feishu")).thenReturn(Optional.of(connection("corp-a", "app-a", "1")));
    binding.setId("binding-a"); binding.setLocalId("employee-a"); binding.setExternalId("alice"); binding.setActive(true);
    when(members.forEmployee("main", "employee-a")).thenReturn(Optional.of(binding));
  }

  @Test void freezesTheOrganizationAssociationAndRoutingWithoutAnySsoRequirement() {
    var prepared = recipients.resolve(List.of("employee-a"), Map.of());
    assertThat(prepared.addresses()).containsEntry("employee-a", List.of("alice"));
    assertThat(EnterpriseNotificationRecipients.context(prepared.channelOptions())).containsEntry("connectionId", "main");
    assertThat(com.ouroboros.util.Json.toJsonString(prepared.channelOptions())).contains("binding-a", "app-a", "corp-a").doesNotContain("credential", "token", "secret");
    recipients.verify("employee-a", List.of("alice"), prepared.channelOptions());
    when(catalog.resolve("main", "feishu")).thenReturn(Optional.of(connection("corp-a", "app-a", "rotated")));
    recipients.verify("employee-a", List.of("alice"), prepared.channelOptions());
  }

  @Test void sameMemberAddressCannotReviveAnUnboundRelationshipInAnOldPlan() {
    var prepared = recipients.resolve(List.of("employee-a"), Map.of());
    binding.setId("new-binding-after-admin-action");
    assertThatThrownBy(() -> recipients.verify("employee-a", List.of("alice"), prepared.channelOptions())).hasMessageContaining("关联已改变");
  }

  @Test void replacementCompanyOrApplicationAndDisabledConnectionsCannotReuseOldPlans() {
    var prepared = recipients.resolve(List.of("employee-a"), Map.of());
    when(catalog.resolve("main", "feishu")).thenReturn(Optional.of(connection("corp-b", "app-a", "2")));
    assertThatThrownBy(() -> recipients.connection(EnterpriseNotificationRecipients.context(prepared.channelOptions()))).hasMessageContaining("身份已改变");
    when(catalog.resolve("main", "feishu")).thenReturn(Optional.of(connection("corp-a", "app-b", "3")));
    assertThatThrownBy(() -> recipients.connection(EnterpriseNotificationRecipients.context(prepared.channelOptions()))).hasMessageContaining("身份已改变");
    when(catalog.resolve("main", "feishu")).thenReturn(Optional.empty());
    assertThatThrownBy(() -> recipients.connection(EnterpriseNotificationRecipients.context(prepared.channelOptions()))).hasMessageContaining("停用");
  }

  @Test void missingMembersAndAmbiguousOrOverriddenConnectionsNeverPickADefaultRecipient() {
    when(members.forEmployee("main", "employee-a")).thenReturn(Optional.empty());
    assertThatThrownBy(() -> recipients.resolve(List.of("employee-a"), Map.of())).hasMessageContaining("尚未关联");
    assertThatThrownBy(() -> recipients.resolve(List.of(), Map.of("connectionId", "another"))).hasMessageContaining("不能切换");
    when(catalog.listSummaries()).thenReturn(List.of());
    assertThatThrownBy(() -> recipients.resolve(List.of(), Map.of())).hasMessageContaining("唯一");
    assertThatThrownBy(() -> recipients.connection(Map.of())).hasMessageContaining("未固定");
  }

  @Test void mismatchedFrozenAddressesOrRemovedMembersCannotBeDelivered() {
    var prepared = recipients.resolve(List.of("employee-a"), Map.of());
    assertThatThrownBy(() -> recipients.verify("employee-a", List.of("bob"), prepared.channelOptions())).hasMessageContaining("关联已改变");
    when(members.forEmployee("main", "employee-a")).thenReturn(Optional.empty());
    assertThatThrownBy(() -> recipients.verify("employee-a", List.of("alice"), prepared.channelOptions())).hasMessageContaining("已解除");
  }

  private ResolvedEnterpriseConnection connection(String corporation, String application, String revision) {
    return new ResolvedEnterpriseConnection("main", Map.of("provider", "feishu", "corpId", corporation, "revision", revision), () -> Map.of("appId", application));
  }
}
