package com.ouroboros.security.rbac.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.ouroboros.data.model.TypedDataModel;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.exception.InternalException;
import com.ouroboros.security.externalauth.ExternalIdentityAttributes;
import com.ouroboros.security.rbac.entity.UserSsoEntity;
import com.ouroboros.security.rbac.entity.UserSsoMemberIndexEntity;

import io.vavr.control.Try;

/**
 * SSO 绑定扩展属性与成员索引的行为测试。
 *
 * <p>这里用数据模型替身固定服务层契约，真实持久化由 ServiceImplIntegrationCoverageTest 验证。
 * 扩展属性是渠道自定义属性的唯一事实源，成员索引只做检索投影且必须与绑定同事务
 * 保持一致——写入、刷新、删除三条路径都要覆盖。
 */
class UserSsoServiceImplBindingAttributesTest {

  private TypedDataModel<String, UserSsoEntity> bindingModel;
  private TypedDataModel<String, UserSsoMemberIndexEntity> indexModel;
  private UserSsoServiceImpl service;

  @SuppressWarnings("unchecked")
  @BeforeEach
  void setUp() {
    bindingModel = mock(TypedDataModel.class);
    indexModel = mock(TypedDataModel.class);
    service = new UserSsoServiceImpl();
    ReflectionTestUtils.setField(service, "dataModel", bindingModel);
    ReflectionTestUtils.setField(service, "memberIndexDataModel", indexModel);
  }

  @Test
  void createBindingStoresAttributesAndProjectsMemberIdIntoTheIndex() {
    when(bindingModel.insert(any(UserSsoEntity.class))).thenAnswer(invocation -> {
      UserSsoEntity entity = invocation.getArgument(0);
      entity.setId("binding-1");
      return Try.success(entity);
    });
    when(indexModel.delete(anyMap())).thenReturn(Try.success(0L));
    when(indexModel.insert(any(UserSsoMemberIndexEntity.class)))
        .thenAnswer(invocation -> Try.success(invocation.getArgument(0)));

    UserSsoEntity created = service.createBinding("user-1", "conn-1", "union-1", "显示名", "a@example.com",
        Map.of(ExternalIdentityAttributes.MEMBER_ID, "staff-1", "dingtalk.deptId", "dept-9"));

    assertThat(created.getExternalAttributes())
        .containsEntry(ExternalIdentityAttributes.MEMBER_ID, "staff-1")
        .containsEntry("dingtalk.deptId", "dept-9");

    var indexCaptor = org.mockito.ArgumentCaptor.forClass(UserSsoMemberIndexEntity.class);
    verify(indexModel).insert(indexCaptor.capture());
    assertThat(indexCaptor.getValue().getBindingId()).isEqualTo("binding-1");
    assertThat(indexCaptor.getValue().getSsoChannel()).isEqualTo("conn-1");
    assertThat(indexCaptor.getValue().getMemberId()).isEqualTo("staff-1");
  }

  @Test
  void createBindingWithoutMemberIdKeepsAttributesButSkipsTheIndex() {
    when(bindingModel.insert(any(UserSsoEntity.class))).thenAnswer(invocation -> {
      UserSsoEntity entity = invocation.getArgument(0);
      entity.setId("binding-2");
      return Try.success(entity);
    });
    when(indexModel.delete(anyMap())).thenReturn(Try.success(0L));

    UserSsoEntity created = service.createBinding("user-1", "conn-1", "open-1", null, null,
        Map.of("wecom.userTicket", "ticket-1"));

    assertThat(created.getExternalAttributes()).containsEntry("wecom.userTicket", "ticket-1");
    // 没有成员标识就没有可检索投影；索引不得写入空值。
    verify(indexModel, never()).insert(any(UserSsoMemberIndexEntity.class));
  }

  @Test
  void updateExternalAttributesRefreshesTheIndex() {
    UserSsoEntity existing = binding("binding-3", "conn-1", "staff-old");
    when(bindingModel.get("binding-3")).thenReturn(Try.success(existing));
    when(bindingModel.update(eq("binding-3"), any(UserSsoEntity.class)))
        .thenAnswer(invocation -> Try.success(1L));
    when(indexModel.delete(anyMap())).thenReturn(Try.success(1L));
    when(indexModel.insert(any(UserSsoMemberIndexEntity.class)))
        .thenAnswer(invocation -> Try.success(invocation.getArgument(0)));

    boolean updated = service.updateExternalAttributes("binding-3",
        Map.of(ExternalIdentityAttributes.MEMBER_ID, "staff-new"));

    assertThat(updated).isTrue();
    verify(indexModel).delete(anyMap());
    var captor = org.mockito.ArgumentCaptor.forClass(UserSsoMemberIndexEntity.class);
    verify(indexModel).insert(captor.capture());
    assertThat(captor.getValue().getMemberId()).isEqualTo("staff-new");
    assertThat(captor.getValue().getBindingId()).isEqualTo("binding-3");
  }

  @Test
  void updateExternalAttributesOnMissingBindingIsANoOp() {
    when(bindingModel.get("missing")).thenReturn(Try.success(null));

    assertThat(service.updateExternalAttributes("missing",
        Map.of(ExternalIdentityAttributes.MEMBER_ID, "staff-1"))).isFalse();
    verify(bindingModel, never()).update(anyString(), any(UserSsoEntity.class));
    verify(indexModel, never()).insert(any(UserSsoMemberIndexEntity.class));
  }

  @Test
  void updateExternalAttributesRejectsMemberIdAlreadyUsedByAnotherBinding() {
    UserSsoEntity existing = binding("binding-4", "conn-1", "staff-old");
    when(bindingModel.get("binding-4")).thenReturn(Try.success(existing));
    when(bindingModel.update(eq("binding-4"), any(UserSsoEntity.class)))
        .thenAnswer(invocation -> Try.success(1L));
    when(indexModel.delete(anyMap())).thenReturn(Try.success(1L));
    when(indexModel.insert(any(UserSsoMemberIndexEntity.class)))
        .thenReturn(Try.failure(new java.sql.SQLIntegrityConstraintViolationException(
            "Duplicate entry for key 'uk_sso_member_channel_member'")));

    assertThatThrownBy(() -> service.updateExternalAttributes("binding-4",
        Map.of(ExternalIdentityAttributes.MEMBER_ID, "staff-taken")))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("already bound under this connection");
  }

  @Test
  void deleteBindingRemovesTheIndexProjection() {
    when(bindingModel.delete("binding-5")).thenReturn(Try.success(1L));
    when(indexModel.delete(anyMap())).thenReturn(Try.success(1L));

    assertThat(service.deleteBinding("binding-5")).isTrue();
    verify(indexModel).delete(Map.of("bindingId", "binding-5"));
  }

  @Test
  void deleteBindingDoesNotTouchTheIndexWhenNothingWasDeleted() {
    when(bindingModel.delete("missing")).thenReturn(Try.success(0L));

    assertThat(service.deleteBinding("missing")).isFalse();
    verify(indexModel, never()).delete(anyMap());
  }

  @Test
  void findByChannelAndMemberIdResolvesThroughTheIndex() {
    when(indexModel.query(anyMap())).thenReturn(Try.success(List.of(
        index("binding-6", "conn-1", "staff-6"))));
    when(bindingModel.get("binding-6")).thenReturn(Try.success(binding("binding-6", "conn-1", "staff-6")));

    assertThat(service.findByChannelAndMemberId("conn-1", "staff-6"))
        .map(UserSsoEntity::getId)
        .contains("binding-6");
  }

  @Test
  void findByChannelAndMemberIdReturnsEmptyWhenTheIndexHasNoMatch() {
    when(indexModel.query(anyMap())).thenReturn(Try.success(List.of()));

    assertThat(service.findByChannelAndMemberId("conn-1", "staff-none")).isEmpty();
    verify(bindingModel, never()).get(anyString());
  }

  @Test
  void findByChannelAndMemberIdRejectsBlankScopesWithoutQuerying() {
    assertThat(service.findByChannelAndMemberId(null, "staff-1")).isEmpty();
    assertThat(service.findByChannelAndMemberId("conn-1", " ")).isEmpty();
    verify(indexModel, never()).query(anyMap());
  }

  @Test
  void readFailuresSurfaceInsteadOfLookingLikeMissingBindings() {
    // 静默返回空会把"数据库故障"表现成"用户没有绑定"，必须抛出。
    when(indexModel.query(anyMap())).thenReturn(Try.failure(new IllegalStateException("db down")));

    assertThatThrownBy(() -> service.findByChannelAndMemberId("conn-1", "staff-1"))
        .isInstanceOf(InternalException.class)
        .hasMessageContaining("channel+memberId");
  }

  private UserSsoEntity binding(String id, String channel, String memberId) {
    UserSsoEntity entity = new UserSsoEntity();
    entity.setId(id);
    entity.setSsoChannel(channel);
    entity.setSsoUserIdentity("external-" + id);
    entity.setUserIdentity("user-" + id);
    Map<String, Object> attributes = new HashMap<>();
    attributes.put(ExternalIdentityAttributes.MEMBER_ID, memberId);
    entity.setExternalAttributes(attributes);
    return entity;
  }

  private UserSsoMemberIndexEntity index(String bindingId, String channel, String memberId) {
    UserSsoMemberIndexEntity entity = new UserSsoMemberIndexEntity();
    entity.setBindingId(bindingId);
    entity.setSsoChannel(channel);
    entity.setMemberId(memberId);
    return entity;
  }
}
