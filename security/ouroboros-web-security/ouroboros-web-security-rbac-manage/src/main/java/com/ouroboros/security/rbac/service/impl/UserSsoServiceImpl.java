package com.ouroboros.security.rbac.service.impl;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ouroboros.data.dsl.Keyword;
import com.ouroboros.data.model.TypedDataModel;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.exception.InternalException;
import com.ouroboros.exception.OuroborosException;
import com.ouroboros.exception.mapper.OuroborosExceptionMapper;
import com.ouroboros.security.externalauth.ExternalIdentityAttributes;
import com.ouroboros.security.rbac.entity.UserSsoEntity;
import com.ouroboros.security.rbac.entity.UserSsoMemberIndexEntity;
import com.ouroboros.security.rbac.service.UserSsoService;

@Service
public class UserSsoServiceImpl implements UserSsoService {

  private static final int BINDING_CONFLICT_ERROR_CODE = 40902;
  private static final String BINDING_CONFLICT_MESSAGE = "External identity already bound to another user";

  private static final Logger logger = LoggerFactory.getLogger(UserSsoServiceImpl.class);

  @Autowired
  private TypedDataModel<String, UserSsoEntity> dataModel;

  @Autowired
  private TypedDataModel<String, UserSsoMemberIndexEntity> memberIndexDataModel;

  @Override
  public Optional<UserSsoEntity> findByChannelAndExternalId(String ssoChannel, String ssoUserIdentity) {
    if (isBlank(ssoChannel) || isBlank(ssoUserIdentity)) {
      return Optional.empty();
    }
    Map<String, Object> statement = where(Map.of(
        "ssoChannel", ssoChannel,
        "ssoUserIdentity", ssoUserIdentity));

    return getDataModel().query(statement)
        .getOrElseThrow(cause -> readFailure("channel+externalId", cause))
        .stream()
        .findFirst();
  }

  @Override
  public Optional<UserSsoEntity> findByChannelAndMemberId(String ssoChannel, String memberId) {
    if (isBlank(ssoChannel) || isBlank(memberId)) {
      return Optional.empty();
    }
    Map<String, Object> statement = where(Map.of(
        "ssoChannel", ssoChannel,
        "memberId", memberId));

    return memberIndexDataModel.query(statement)
        .getOrElseThrow(cause -> readFailure("channel+memberId", cause))
        .stream()
        .map(UserSsoMemberIndexEntity::getBindingId)
        .filter(value -> !isBlank(value))
        .findFirst()
        .flatMap(this::findById);
  }

  @Override
  public List<UserSsoEntity> findByUserIdentity(String userIdentity) {
    if (isBlank(userIdentity)) {
      return Collections.emptyList();
    }
    Map<String, Object> statement = where(Map.of("userIdentity", userIdentity));
    return getDataModel().query(statement)
        .getOrElseThrow(cause -> readFailure("userIdentity", cause));
  }

  @Override
  @Transactional
  public UserSsoEntity createBinding(String userIdentity, String ssoChannel, String ssoUserIdentity,
                                      String displayName, String email,
                                      Map<String, Object> externalAttributes) {
    var entity = new UserSsoEntity();
    entity.setUserIdentity(userIdentity);
    entity.setSsoChannel(ssoChannel);
    entity.setSsoUserIdentity(ssoUserIdentity);
    entity.setExternalDisplayName(displayName);
    entity.setExternalEmail(email);
    entity.setExternalAttributes(immutableAttributes(externalAttributes));
    entity.setCreatedAt(LocalDateTime.now());
    entity.setUpdatedAt(LocalDateTime.now());

    UserSsoEntity created;
    try {
      created = getDataModel().insert(entity)
          .getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
    } catch (OuroborosException e) {
      if (isBindingConflict(e)) {
        throw new BusinessException(BINDING_CONFLICT_ERROR_CODE, BINDING_CONFLICT_MESSAGE, e);
      }
      throw e;
    }
    replaceMemberIndex(created.getId(), ssoChannel, memberIdOf(created.getExternalAttributes()));
    return created;
  }

  @Override
  @Transactional
  public boolean updateExternalAttributes(String id, Map<String, Object> externalAttributes) {
    if (isBlank(id)) {
      return false;
    }
    Optional<UserSsoEntity> existing = findById(id);
    if (existing.isEmpty()) {
      return false;
    }
    UserSsoEntity current = existing.get();
    Map<String, Object> attributes = immutableAttributes(externalAttributes);
    current.setExternalAttributes(attributes);
    current.setUpdatedAt(LocalDateTime.now());
    Long updated = getDataModel().update(id, current)
        .getOrElseThrow(cause -> writeFailure("update SSO external attributes", cause));
    if (updated == null || updated == 0) {
      return false;
    }
    replaceMemberIndex(id, current.getSsoChannel(), memberIdOf(attributes));
    return true;
  }

  @Override
  @Transactional
  public boolean deleteBinding(String id) {
    boolean deleted = getDataModel().delete(id)
        .map(r -> r > 0)
        .getOrElseThrow(cause -> writeFailure("delete SSO binding", cause));
    if (deleted) {
      removeMemberIndex(id);
    }
    return deleted;
  }

  @Override
  public boolean existsByChannelAndExternalId(String ssoChannel, String ssoUserIdentity) {
    return findByChannelAndExternalId(ssoChannel, ssoUserIdentity).isPresent();
  }

  /**
   * 保持成员索引与扩展属性一致：先删除该绑定已有索引，再按新成员标识写入。
   */
  private void replaceMemberIndex(String bindingId, String ssoChannel, String memberId) {
    removeMemberIndex(bindingId);
    if (isBlank(memberId)) {
      return;
    }
    var indexEntry = new UserSsoMemberIndexEntity();
    indexEntry.setBindingId(bindingId);
    indexEntry.setSsoChannel(ssoChannel);
    indexEntry.setMemberId(memberId);
    try {
      memberIndexDataModel.insert(indexEntry)
          .getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
    } catch (OuroborosException e) {
      if (isBindingConflict(e)) {
        throw new BusinessException(BINDING_CONFLICT_ERROR_CODE,
            "External member identity already bound under this connection", e);
      }
      throw e;
    }
  }

  private void removeMemberIndex(String bindingId) {
    memberIndexDataModel.delete(Map.of("bindingId", bindingId))
        .getOrElseThrow(cause -> writeFailure("delete SSO member index", cause));
  }

  private String memberIdOf(Map<String, Object> attributes) {
    Object value = attributes.get(ExternalIdentityAttributes.MEMBER_ID);
    if (value == null) {
      return null;
    }
    String text = String.valueOf(value).trim();
    return text.isEmpty() ? null : text;
  }

  private Map<String, Object> immutableAttributes(Map<String, Object> attributes) {
    if (attributes == null || attributes.isEmpty()) {
      return Collections.emptyMap();
    }
    Map<String, Object> copy = new HashMap<>();
    attributes.forEach((key, value) -> {
      if (key != null && !key.isBlank()) {
        copy.put(key, value);
      }
    });
    return Collections.unmodifiableMap(copy);
  }

  private Map<String, Object> where(Map<String, Object> conditions) {
    Map<String, Object> statement = new HashMap<>();
    statement.put(Keyword.WHERE.name(), conditions);
    return statement;
  }

  /**
   * 按主键读取绑定。数据层命中缺失时返回成功但值为 null（见 {@code BaseTypedDataModel.recordToModel}），
   * 因此这里先判失败再判空，避免把"没有这条绑定"当成读取异常。
   */
  private Optional<UserSsoEntity> findById(String id) {
    var result = getDataModel().get(id);
    if (result.isFailure()) {
      throw readFailure("id", result.getCause());
    }
    return Optional.ofNullable(result.get());
  }

  private InternalException readFailure(String scope, Throwable cause) {
    return new InternalException("Failed to query SSO binding by " + scope, cause);
  }

  private InternalException writeFailure(String scope, Throwable cause) {
    return new InternalException("Failed to " + scope, cause);
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private TypedDataModel<String, UserSsoEntity> getDataModel() throws OuroborosException {
    return dataModel;
  }

  private boolean isBindingConflict(Throwable throwable) {
    var current = throwable;
    while (current != null) {
      if (current instanceof DuplicateKeyException || current instanceof DataIntegrityViolationException) {
        return true;
      }
      if (current instanceof java.sql.SQLIntegrityConstraintViolationException) {
        return true;
      }
      var message = current.getMessage();
      if (message != null) {
        var normalized = message.toLowerCase(Locale.ROOT);
        if (normalized.contains("duplicate") || normalized.contains("unique")) {
          return true;
        }
      }
      current = current.getCause();
    }
    return false;
  }
}
