package com.ouroboros.security.rbac.service.impl;

import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ouroboros.data.dsl.Keyword;
import com.ouroboros.data.model.TypedDataModel;
import com.ouroboros.exception.OuroborosException;
import com.ouroboros.exception.mapper.OuroborosExceptionMapper;
import com.ouroboros.security.rbac.entity.UserEntity;
import com.ouroboros.security.rbac.service.DefaultPasswordGenerator;
import com.ouroboros.security.rbac.service.UserService;
import com.ouroboros.util.Maps;

/**
 * @author liansz
 */
@Service
@CacheConfig(cacheNames = "ouroboros:users")
public class UserServiceImpl implements UserService {


  @Autowired
  private UserServiceImpl self;

  @Autowired
  private PasswordEncoder passwordEncoder;

  @Autowired
  private DefaultPasswordGenerator defaultPasswordGenerator;

  @Autowired
  private TypedDataModel<String, UserEntity> dataModel;

  @Override
  @CacheEvict(allEntries = true)
  @Transactional
  public UserEntity add(UserEntity user) throws OuroborosException {
    UserEntity model = new UserEntity(user);

    if (ObjectUtils.isEmpty(model.getPassword())) {
      model.setPassword(defaultPasswordGenerator.generatorDefaultPassword());
    }

    model.setPassword(encryptPassword(model.getPassword()));

    return getDataModel().insert(model)
        .getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
  }

  @Override
  @CacheEvict(allEntries = true)
  @Transactional
  public List<UserEntity> add(Collection<UserEntity> users) throws OuroborosException {
    List<UserEntity> models = users.stream().map(user -> {
      UserEntity model = new UserEntity(user);
      if (ObjectUtils.isEmpty(model.getPassword())) {
        model.setPassword(defaultPasswordGenerator.generatorDefaultPassword());
      }

      model.setPassword(encryptPassword(model.getPassword()));

      return model;
    }).collect(Collectors.toList());

    return getDataModel().batchInsert(models)
        .getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
  }

  @Override
  @CacheEvict(allEntries = true)
  @Transactional
  public Boolean delete(String id) throws OuroborosException {
    return getDataModel().delete(id)
        .map(r -> r > 0)
        .getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
  }

  @Override
  @CacheEvict(allEntries = true)
  @Transactional
  public Boolean update(String id, UserEntity model) throws OuroborosException {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("avatar", model.getAvatar());
    data.put("isEnabled", model.isEnabled());

    return getDataModel().update(id, data)
        .map(r -> r > 0)
        .getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
  }

  @Override
  @CacheEvict(allEntries = true)
  @Transactional
  public Boolean resetPassword(String userIdentity) throws OuroborosException {
    return modifyPassword(userIdentity, defaultPasswordGenerator.generatorDefaultPassword());
  }

  @Override
  @CacheEvict(allEntries = true)
  @Transactional
  public Boolean modifyPassword(String userIdentity, String password) throws OuroborosException {
    Optional<UserEntity> optional = self.getByUserIdentity(userIdentity);
    if (!optional.isPresent()) {
      return false;
    }

    return getDataModel().update(userIdentity, Map.of("password", encryptPassword(password)))
        .map(i -> i > 0)
        .getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
  }

  @Override
  public Optional<UserEntity> getByUserIdentity(String userIdentity) {
    var model = getDataModel().get(userIdentity)
        .getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
    return Optional.ofNullable(model)
        .map(UserEntity::new)
        .map(copy -> {
          copy.setPassword(null);
          return copy;
        });
  }

  @Override
  public Optional<String> getPasswordByUserIdentity(String userIdentity) {
    var model = getDataModel().get(userIdentity)
        .getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
    return Optional.ofNullable(model).map(UserEntity::getPassword);
  }

  @Override
  public List<UserEntity> searchAll() {
    return self.doSearchAll().stream()
        .map(UserEntity::new)
        .peek(model -> model.setPassword(null))
        .collect(Collectors.toList());
  }

  @Cacheable(sync = true)
  public List<UserEntity> doSearchAll() {
    // TODO: 此处为了兼容老版本升级，如果不进行处理，在启动时会检查admin不存在创建，有可能会出现错误，所以此兼容是必要的
    Map<String, Object> statement = new HashMap<>();
    List<Map<String, Object>> list = Arrays.asList(Collections.singletonMap("isDeleted", false), Collections.singletonMap("isDeleted", null));
    statement.put(Keyword.WHERE.name(), Collections.singletonMap("OR", list));

    return getDataModel().query(statement)
        .getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
  }

  /**
   * 密码加密
   *
   * @param password
   * @return
   */
  private String encryptPassword(String password) {
    return passwordEncoder.encode(password);
  }

  /**
   * 获取数据模型
   *
   * @return
   */
  private TypedDataModel<String, UserEntity> getDataModel() throws OuroborosException {
    return dataModel;
  }
  @Override
  @Transactional
  @CacheEvict(allEntries = true)
  public void saveProfile(String identity, String subject, long expected, Map<String, Object> patch) {
    if (com.ouroboros.security.context.CurrentSecurityContextHolder.findIdentity().orElse(null)
        != com.ouroboros.security.identity.SystemSecurityIdentities.system()) {
      throw new com.ouroboros.exception.BusinessException(403, "账号资料持久化只允许受信系统操作");
    }
    if (expected < 0 || expected == Long.MAX_VALUE || patch == null || patch.isEmpty()
        || !Set.of("nickname", "email", "emailVerified", "phoneNumber", "phoneNumberVerified", "profileValues")
            .containsAll(patch.keySet())) {
      throw new com.ouroboros.exception.ValidationException("[PROFILE_INVALID] 无效的资料补丁");
    }
    Map<String, Object> values = new LinkedHashMap<>(patch);
    values.put("profileRevision", expected + 1);
    var where = new LinkedHashMap<String, Object>();
    where.put("userIdentity", identity);
    where.put("publicSubjectId", subject);
    where.put("profileRevision", expected);
    where.put("isDeleted", false);
    where.put("isEnabled", true);
    long updated = com.ouroboros.security.context.CurrentSecurityContextHolder.withIdentity(
        com.ouroboros.security.identity.SystemSecurityIdentities.system(), () ->
            dataModel.update(where, values).getOrElseThrow(OuroborosExceptionMapper::toOuroborosException));
    if (updated != 1) { throw new com.ouroboros.exception.BusinessException(409, "[PROFILE_REVISION_CONFLICT] 资料已变更或账号不可用，请重新加载"); }
  }

}
