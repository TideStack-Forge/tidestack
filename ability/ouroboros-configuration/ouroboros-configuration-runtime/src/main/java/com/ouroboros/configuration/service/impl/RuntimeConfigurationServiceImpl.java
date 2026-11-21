package com.ouroboros.configuration.service.impl;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ouroboros.configuration.center.ConfigurationMetadataCenter;
import com.ouroboros.configuration.ConfigurationRevision;
import com.ouroboros.configuration.ConfigurationSnapshot;
import com.ouroboros.configuration.management.ConfigurationManagementAccessPolicy;
import com.ouroboros.configuration.management.ConfigurationManagementContext;
import com.ouroboros.configuration.management.ConfigurationMutation;
import com.ouroboros.configuration.event.ConfigurationUpdateEvent;
import com.ouroboros.configuration.metadata.ConfigScope;
import com.ouroboros.configuration.metadata.ConfigValueType;
import com.ouroboros.configuration.metadata.ConfigurationMetadata;
import com.ouroboros.configuration.model.ConfigurationModel;
import com.ouroboros.configuration.model.ConfigurationValueModel;
import com.ouroboros.configuration.model.RuntimeConfigurationValueModel;
import com.ouroboros.configuration.service.ConfigurationValueService;
import com.ouroboros.configuration.service.RuntimeConfigurationService;
import com.ouroboros.configuration.validation.ConfigurationValueValidator;
import com.ouroboros.exception.InternalException;
import com.ouroboros.exception.OuroborosException;
import com.ouroboros.exception.PlatformException;
import com.ouroboros.expression.EL;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.util.Json;

/**
 * @author liansz
 */
@Service
@CacheConfig(cacheNames = "ouroboros:configuration")
public class RuntimeConfigurationServiceImpl implements RuntimeConfigurationService {

  /**
   * 引用变量正则
   */
  private static final Pattern REFERENCE_VARIABLE_PATTERN = Pattern.compile("\\#\\{(.+?)\\}");

  @Autowired
  private RuntimeConfigurationServiceImpl self;

  @Autowired
  private ApplicationContext applicationContext;

  @Autowired
  private ConfigurableEnvironment environment;

  @Autowired
  private ConfigurationValueService valueService;

  /**
   * 由业务模块注册的保存前校验器。配置模块只依赖公共扩展点，未注册时保持空列表。
   */
  @Autowired(required = false)
  private List<ConfigurationValueValidator> valueValidators = List.of();

  @Autowired(required = false)
  private List<ConfigurationManagementAccessPolicy> managementPolicies = List.of();

  @Override
  public <T> Optional<T> get(String key) {
    return Optional.ofNullable((T) self.getAllConfigurationValue().get(key));
  }

  @Override
  public <T> Optional<T> getForExecution(String key) {
    if (ConfigurationMetadataCenter.getConfigurationMetadata(key)
        .map(ConfigurationMetadata::isManagementProtected).orElse(false)) {
      throw new com.ouroboros.exception.BusinessException(403, "[CONFIGURATION_PROTECTED_READ_FORBIDDEN] 此配置不能由脚本或逻辑流读取");
    }
    return get(key);
  }

  @Override
  public Optional<ConfigurationSnapshot> getSnapshot(String key) {
    return ConfigurationMetadataCenter.getConfigurationMetadata(key)
        .map(metadata -> snapshot(metadata, valueService.get(key)));
  }

  @Override
  public Map<String, ConfigurationModel> getManagedConfigurationModels() {
    Map<String, ConfigurationModel> result = new TreeMap<>(String::compareToIgnoreCase);
    var context = managementContext();
    self.getAllConfigurationModel().forEach((key, model) ->
        project(model, context).ifPresent(projected -> result.put(key, projected)));
    return result;
  }

  @Override
  public Optional<ConfigurationModel> getManagedConfigurationModel(String key) {
    return Optional.ofNullable(self.getAllConfigurationModel().get(key))
        .flatMap(model -> project(model, managementContext()));
  }

  @Override
  public Map<String, Object> getValues(ConfigScope scope) {
    return self.getAllConfigurationModel().values().stream()
        .filter(model -> Objects.equals(model.getMetadata().getScope(), scope))
        .filter(model -> scope != ConfigScope.FRONTEND || !model.getMetadata().isManagementProtected())
        .collect(() -> new TreeMap<>(String::compareToIgnoreCase), (map, model) -> map.put(model.getKey(), model.getValue()), Map::putAll);
  }

  @Override
  public java.util.Optional<String> getRevision(String key) {
    if (key == null || key.isBlank()) {
      return java.util.Optional.empty();
    }
    return java.util.Optional.ofNullable(self.getAllConfigurationModel().get(key))
        .map(ConfigurationModel::getRevision);
  }

  @Override
  public java.util.Optional<String> getStoredRawValue(String key) {
    if (key == null || key.isBlank()) {
      return java.util.Optional.empty();
    }
    return java.util.Optional.ofNullable(self.getAllConfigurationModel().get(key))
        .map(ConfigurationModel::getConfigValue)
        .map(ConfigurationValueModel::getValue);
  }

  @Override
  public Map<String, Object> getAllConfigurationValue() {
    return self.getAllConfigurationModel().values().stream()
        .collect(() -> new TreeMap<>(String::compareToIgnoreCase), (map, model) -> map.put(model.getKey(), model.getValue()), Map::putAll);
  }

  @Override
  @Cacheable(sync = true)
  public Map<String, ConfigurationModel> getAllConfigurationModel() {
    /*
     * 此处通过加权排序算法推导配置填充优先级
     *
     * 1.取出所有元数据，并将所有key写入权重map
     * 2.对元数据值进行遍历
     *  2.1 当前值权重 += 1
     *  2.2 遍历引用变量
     *    2.2.1 如果引用变量存在，则引用变量权重 += 当前值权重
     *    2.2.2 如果引用变量还存在引用变量，则递归执行2.2.1
     * 3.遍历完成后会得到权重map，根据权重map对元数据进行排序
     */

    // 获取元数据、值数据
    Collection<ConfigurationMetadata> metadataModels = ConfigurationMetadataCenter.getConfigurationMetadataMap().values();
    List<ConfigurationValueModel> valueModels = Optional.ofNullable(valueService.getAllConfigurationValue())
        .orElseGet(Collections::emptyList)
        .stream()
        .filter(Objects::nonNull)
        .collect(Collectors.toList());

    // 初始化权重map
    Map<String, AtomicInteger> weightMap = new HashMap<>(metadataModels.size());
    metadataModels.forEach(m -> weightMap.put(m.getKey(), new AtomicInteger(0)));

    // 递归计算权重
    Consumer<String> calculateWeight = (key) -> {
      // 当前key权重 + 1
      int currentWight = weightMap.get(key).incrementAndGet();

      BiConsumer<BiConsumer, String> calculate = (consumer, k) -> {
        // 获取值数据
        String value = getValueByKey(metadataModels, valueModels, k);
        if (value == null) {
          return;
        }

        // 匹配引用变量
        Matcher matcher = REFERENCE_VARIABLE_PATTERN.matcher(value);
        while (matcher.find()) {
          String referenceKey = matcher.group(1);

          // 当前变量属于外部变量无需处理权重
          if (!weightMap.containsKey(referenceKey)) {
            continue;
          }

          // 如果引用变量存在，则引用变量权重 += 当前值权重
          weightMap.get(referenceKey).addAndGet(currentWight);

          // 递归计算引用变量权重
          consumer.accept(consumer, referenceKey);
        }
      };

      calculate.accept(calculate, key);
    };
    metadataModels.forEach(m -> calculateWeight.accept(m.getKey()));

    // 对元数据按权重进行排序（倒序）、处理值引用、类型化
    Map<String, Object> elContext = getEnvironmentProperties();
    Map<String, ConfigurationModel> map = metadataModels.stream()
        .sorted(Comparator.comparingInt(m -> weightMap.get(((ConfigurationMetadata) m).getKey()).get()).reversed())
        .collect(Collectors.toList())
        .stream()
//        .filter(metadataModel -> ObjectUtils.isNotEmpty(getValueByKey(metadataModels, valueModels, metadataModel.getKey())))
        .collect(Collectors.toMap(
            ConfigurationMetadata::getKey,
            metadataModel -> {
              String key = metadataModel.getKey();
              String valueExpression = getValueByKey(metadataModels, valueModels, key);

              // 处理引用（此处如果解析失败，取原值即可）
              rejectSensitiveReferences(valueExpression, metadataModels);
              String valueString = metadataModel.isManagementProtected() ? valueExpression
                  : (String) EL.eval(valueExpression, elContext).getOrElse(valueExpression);
              if (!metadataModel.isManagementProtected()) {
                elContext.put(key, valueString);
              }

              // 获取值类型
              var valueType = metadataModel.getValueType();

              // 类型化
              Object valueObject = convertValue(metadataModel, valueString);

              // 获取值模型
              ConfigurationValueModel valueModel = valueModels.stream()
                  .filter(v -> v.getKey().equals(key))
                  .findFirst()
                  .orElse(null);

              String revision;
              if (valueModel == null) {
                // A metadata default has no persisted row yet, so its token is derived from the default.
                revision = ConfigurationRevision.defaultRevision(key, metadataModel.getDefaultValue());
              } else if (valueModel instanceof RuntimeConfigurationValueModel runtimeValue
                  && runtimeValue.getRevision() != null) {
                revision = ConfigurationRevision.storedRevision(key, runtimeValue.getValue(),
                    runtimeValue.getRevision(), runtimeValue.getGeneration());
              } else {
                // Legacy rows may predate the revision column; their effective token is derived from the stored value.
                revision = ConfigurationRevision.legacyStoredRevision(key, valueModel.getValue());
              }
              return new ConfigurationModel(key, valueObject, metadataModel, valueModel, revision);
            },
            (v1, v2) -> v2));

    // 检查是否存在不允许为空，但是数据为空的情况
    List<String> nullValueKeyList = metadataModels.stream()
        .filter(m -> !m.isNullable() && !map.containsKey(m.getKey()))
        .map(ConfigurationMetadata::getKey)
        .collect(Collectors.toList());
    if (ObjectUtils.isNotEmpty(nullValueKeyList)) {
      String keys = nullValueKeyList.stream().collect(Collectors.joining(","));
      throw new PlatformException(String.format("配置: %1s 不允许为空！", keys));
    }

    return map;
  }

  @Override
  public void update(Map<String, Object> map, String updateBy, Map<String, String> expectedRevisions) {
    Map<String, ConfigurationMutation> mutations = new LinkedHashMap<>();
    if (map != null) {
      map.forEach((key, value) -> mutations.put(key, new ConfigurationMutation.Replace(value)));
    }
    updateManaged(mutations, updateBy, expectedRevisions);
  }

  @Override
  public Map<String, ConfigurationModel> updateManaged(Map<String, ConfigurationMutation> map, String updateBy,
                                                      Map<String, String> expectedRevisions) {
    if (ObjectUtils.isEmpty(map)) {
      throw new PlatformException("配置键不能为空！");
    }

    Map<String, String> revisions = expectedRevisions == null ? Collections.emptyMap() : expectedRevisions;
    if (!revisions.isEmpty()
        && (revisions.size() < map.size()
            || !revisions.keySet().containsAll(map.keySet())
            || revisions.values().stream().anyMatch(StringUtils::isBlank))) {
      throw new com.ouroboros.exception.BusinessException(400, "[CONFIGURATION_REVISION_REQUIRED] 批量 CAS 更新必须为每个配置提供 expectedRevision");
    }

    // 执行更新
    Map<String, ConfigurationModel> committed = self.doUpdate(map, updateBy, revisions);

    try {
      self.clearCache();
      committed.forEach((key, model) -> publishUpdateEvent(key, model.getValue()));
    } catch (RuntimeException error) {
      Map<String, String> revisionsCommitted = new LinkedHashMap<>();
      committed.forEach((key, model) -> revisionsCommitted.put(key, model.getRevision()));
      throw new com.ouroboros.exception.BusinessException(503,
          "[CONFIGURATION_COMMITTED_VISIBILITY_UNCONFIRMED] 配置已提交，请重新核对版本: " + revisionsCommitted, error);
    }
    Map<String, ConfigurationModel> projected = new LinkedHashMap<>();
    var context = managementContext();
    committed.forEach((key, model) -> project(model, context).ifPresent(value -> projected.put(key, value)));
    return projected;
  }

  /**
   * 通过key获取值（未处理引用）先从值数据中获取，如果值数据中没有则从元数据中获取
   *
   * @param metadataModels
   * @param valueModels
   * @param key
   * @return
   */
  protected String getValueByKey(Collection<ConfigurationMetadata> metadataModels,
                                 Collection<? extends ConfigurationValueModel> valueModels, String key) {
    String value = valueModels.stream()
        .filter(m -> m.getKey().equals(key))
        .map(ConfigurationValueModel::getValue)
        .findFirst()
        .orElseGet(() ->
            metadataModels.stream()
                .filter(m -> m.getKey().equals(key))
                .findFirst()
                .get()
                .getDefaultValue()
        );

    return value;
  }

  /**
   * 获取环境属性
   *
   * @return
   */
  protected Map<String, Object> getEnvironmentProperties() {
    Map<String, Object> props = new HashMap<>(32);

    // 获取spring所有配置
    {
      Map<String, String> springPropertyMap = StreamSupport.stream(environment.getPropertySources().spliterator(), false)
          .filter(ps -> ps instanceof EnumerablePropertySource)
          .map(ps -> ((EnumerablePropertySource<?>) ps).getPropertyNames())
          .flatMap(Arrays::stream)
          .distinct()
          .collect(Collectors.toMap(
              Function.identity(),
              key -> Objects.requireNonNull(environment.getProperty(key)),
              (v1, v2) -> v2
          ));

      props.putAll(springPropertyMap);
    }

    // 获取java所有属性
    {
      Properties properties = System.getProperties();
      Enumeration<?> enumeration = properties.propertyNames();
      while (enumeration.hasMoreElements()) {
        String key = (String) enumeration.nextElement();
        String value = properties.getProperty(key);
        props.put(key, value);
      }
    }

    // 获取java环境变量
    {
      props.putAll(System.getenv());
    }

    ConfigurationMetadataCenter.getConfigurationMetadataMap().values().stream()
        .filter(ConfigurationMetadata::isManagementProtected)
        .flatMap(metadata -> sensitiveSources(metadata).stream())
        .forEach(props::remove);
    return props;
  }

  @Transactional(rollbackFor = Exception.class)
  public Map<String, ConfigurationModel> doUpdate(Map<String, ConfigurationMutation> map, String updateBy,
                                                 Map<String, String> expectedRevisions) {
    Map<String, ConfigurationModel> committed = new LinkedHashMap<>();
    var context = managementContext();
    if (!context.authenticated() && (StringUtils.isBlank(updateBy) || map.keySet().stream().anyMatch(key ->
        ConfigurationMetadataCenter.getConfigurationMetadata(key).map(ConfigurationMetadata::isManagementProtected).orElse(false)))) {
      if (map.size() != 1 || map.values().stream().anyMatch(mutation -> mutation == null
          || mutation instanceof ConfigurationMutation.Replace || mutation instanceof ConfigurationMutation.RestoreDefaults)
          || map.keySet().stream().anyMatch(key -> !ConfigurationMetadataCenter.getConfigurationMetadata(key)
              .map(ConfigurationMetadata::isManagementProtected).orElse(false))) {
        throw new com.ouroboros.exception.BusinessException(403, "[CONFIGURATION_ACCESS_DENIED] 此操作要求平台管理身份");
      }
    }
    Map<String, Set<String>> dependencies = new LinkedHashMap<>();
    map.keySet().stream().sorted().forEach(key -> {
      var metadata = ConfigurationMetadataCenter.getConfigurationMetadata(key)
          .orElseThrow(() -> new PlatformException("配置元数据不存在: " + key));
      dependencies.put(key, metadata.isManagementProtected()
          ? Set.copyOf(policyFor(metadata).orElseThrow().writeDependencies(key, map.get(key))) : Set.of());
    });
    Set<String> keys = new TreeSet<>(map.keySet());
    dependencies.values().forEach(keys::addAll);
    if (!keys.containsAll(expectedRevisions.keySet())) {
      throw new com.ouroboros.exception.BusinessException(400,
          "[CONFIGURATION_REVISION_REQUIRED] 配置版本只能包含变更键及其声明的依赖");
    }
    Map<String, ConfigurationSnapshot> locked = new LinkedHashMap<>();
    if (dependencies.values().stream().anyMatch(dependencyKeys -> !dependencyKeys.isEmpty())) {
      for (String key : keys) {
        var metadata = ConfigurationMetadataCenter.getConfigurationMetadata(key)
            .orElseThrow(() -> new PlatformException("配置依赖元数据不存在: " + key));
        locked.put(key, snapshot(metadata, valueService.getForUpdate(key)));
      }
    }
    for (String key : expectedRevisions.keySet()) {
      if (!map.containsKey(key) && !Objects.equals(locked.get(key).revision(), expectedRevisions.get(key))) {
        throw new com.ouroboros.exception.BusinessException(409,
            "[CONFIGURATION_REVISION_CONFLICT] 配置依赖已被其他操作修改，请刷新后重试: " + key);
      }
    }
    for (String key : new TreeSet<>(map.keySet())) {
      var mutation = Objects.requireNonNull(map.get(key), "配置变更不能为空");
      if (ObjectUtils.isEmpty(key)) {
        throw new PlatformException("配置键不能为空！");
      }

      var metadataModelOptional = ConfigurationMetadataCenter.getConfigurationMetadata(key);
      if (!metadataModelOptional.isPresent()) {
        throw new PlatformException(String.format("配置:【%s】元数据不存在！", key));
      }

      ConfigurationMetadata metadata = metadataModelOptional.get();
      var policy = policyFor(metadata);
      Object value;
      String auditActor = updateBy;
      if (metadata.isManagementProtected()) {
        String expected = expectedRevisions.get(key);
        if (StringUtils.isBlank(expected)) {
          throw new com.ouroboros.exception.BusinessException(400, "[CONFIGURATION_REVISION_REQUIRED] 受保护配置必须提供 expectedRevision");
        }
        var old = locked.containsKey(key) ? copySnapshot(locked.get(key)) : snapshot(metadata, valueService.get(key));
        if (!Objects.equals(old.revision(), expected)) {
          throw new com.ouroboros.exception.BusinessException(409, "[CONFIGURATION_REVISION_CONFLICT] 配置已被其他操作修改，请刷新后重试: " + key);
        }
        Map<String, ConfigurationSnapshot> required = new LinkedHashMap<>();
        dependencies.get(key).forEach(dependency -> required.put(dependency, copySnapshot(locked.get(dependency))));
        var prepared = policy.orElseThrow().prepareWrite(context, old, mutation, Collections.unmodifiableMap(required));
        value = prepared.value();
        auditActor = prepared.auditActor();
      } else if (mutation instanceof ConfigurationMutation.Replace replace) {
        value = replace.value();
      } else if (mutation == ConfigurationMutation.RestoreDefaults.INSTANCE) {
        value = convertValue(metadata, metadata.getDefaultValue());
      } else {
        throw new PlatformException("配置不支持此变更命令");
      }

      if (StringUtils.isBlank(auditActor) || auditActor.length() > 255
          || auditActor.chars().anyMatch(Character::isISOControl)) {
        throw new PlatformException("配置审计主体无效");
      }
      if (metadata.isManagementProtected()) {
        value = convertValue(metadata, metadata.getValueType().toString(value));
      }

      if (!metadata.isModifiable()) {
        throw new PlatformException("配置不允许修改: " + key);
      }
      if (mutation == ConfigurationMutation.RestoreDefaults.INSTANCE && metadata.isRestoreDefaultRemovesOverride()) {
        String expected = expectedRevisions.get(key);
        if (StringUtils.isBlank(expected)) {
          throw new com.ouroboros.exception.BusinessException(400, "[CONFIGURATION_REVISION_REQUIRED] 撤销覆盖必须提供 expectedRevision");
        }
        String defaultRevision = ConfigurationRevision.defaultRevision(key, metadata.getDefaultValue());
        if (!valueService.removeOverride(key, expected, defaultRevision)) {
          throw new com.ouroboros.exception.BusinessException(409, "[CONFIGURATION_REVISION_CONFLICT] 配置已被其他操作修改，请刷新后重试: " + key);
        }
        committed.put(key, new ConfigurationModel(key, convertValue(metadata, metadata.getDefaultValue()),
            copyMetadata(metadata), null, defaultRevision));
        continue;
      }
      rejectSensitiveReferences(metadata.getValueType().toString(value),
          ConfigurationMetadataCenter.getConfigurationMetadataMap().values());

      if (isMissingValue(value, metadataModelOptional.get())) {
        throw new PlatformException(String.format("配置:【%s】值不能为空！", key));
      }

      var valueTypeEnum = metadataModelOptional.get().getValueType();

      // 验证默认值类型是否匹配
      if (!valueTypeEnum.isValid(value)) {
        throw new PlatformException(String.format("配置:【%s】值类型不匹配", key));
      }

      validateWithExtensions(key, value);

      try {
        RuntimeConfigurationValueModel valueModel = new RuntimeConfigurationValueModel();
        valueModel.setKey(key);
        valueModel.setValue(valueTypeEnum.toString(value));
        valueModel.setUpdateBy(auditActor);
        String expectedRevision = expectedRevisions.get(key);
        String defaultRevision = ConfigurationRevision.defaultRevision(key, metadataModelOptional.get().getDefaultValue());
        if (!valueService.update(valueModel, expectedRevision, defaultRevision)) {
          if (expectedRevision != null) {
            throw new com.ouroboros.exception.BusinessException(409, "[CONFIGURATION_REVISION_CONFLICT] 配置已被其他操作修改，请刷新后重试: " + key);
          }
          throw new InternalException("更新数据失败！");
        }
        String revision = ConfigurationRevision.storedRevision(key, valueModel.getValue(),
            valueModel.getRevision(), valueModel.getGeneration());
        committed.put(key, new ConfigurationModel(key, value, copyMetadata(metadata), valueModel, revision));
      } catch (InternalException e) {
        throw e;
      } catch (OuroborosException e) {
        throw e;
      } catch (Exception e) {
        throw new InternalException(String.format("配置:【%s】更新失败", key), e);
      }
    }
    return committed;
  }

  private ConfigurationSnapshot copySnapshot(ConfigurationSnapshot snapshot) {
    return new ConfigurationSnapshot(snapshot.key(), copyValue(snapshot.value()), snapshot.storedRawValue(),
        snapshot.revision(), copyMetadata(snapshot.metadata()));
  }

  private ConfigurationManagementContext managementContext() {
    return new ConfigurationManagementContext(CurrentSecurityContextHolder.findIdentity()
        .filter(identity -> identity.getCredentialContext() != null
            && identity.getCredentialContext().kind() == com.ouroboros.security.identity.CredentialContext.Kind.PLATFORM)
        .map(identity -> identity.getUserIdentity()).orElse(null), ConfigurationManagementContext.Purpose.MANAGEMENT);
  }

  private Optional<ConfigurationManagementAccessPolicy> policyFor(ConfigurationMetadata metadata) {
    var matches = managementPolicies.stream().filter(policy -> policy.supports(metadata.getKey())).toList();
    if (matches.size() > 1 || (metadata.isManagementProtected() && matches.isEmpty())) {
      throw new com.ouroboros.exception.BusinessException(503, "[CONFIGURATION_POLICY_REQUIRED] 受保护配置必须且只能配置一个管理策略: " + metadata.getKey());
    }
    return matches.stream().findFirst();
  }

  private Optional<ConfigurationModel> project(ConfigurationModel model, ConfigurationManagementContext context) {
    ConfigurationMetadata metadata = copyMetadata(model.getMetadata());
    var policy = policyFor(metadata);
    var copied = new ConfigurationSnapshot(model.getKey(), copyValue(model.getValue()),
        model.getConfigValue() == null ? null : model.getConfigValue().getValue(), model.getRevision(), metadata);
    Optional<ConfigurationSnapshot> projected = metadata.isManagementProtected()
        ? (context.authenticated() ? policy.orElseThrow().projectRead(context, copied) : Optional.empty())
        : Optional.of(copied);
    return projected.map(snapshot -> {
      ConfigurationValueModel stored = null;
      if (model.getConfigValue() != null) {
        RuntimeConfigurationValueModel copy = Json.toBean(RuntimeConfigurationValueModel.class,
            Json.toJsonString(model.getConfigValue()));
        copy.setValue(snapshot.storedRawValue());
        stored = copy;
      }
      return new ConfigurationModel(snapshot.key(), snapshot.value(), snapshot.metadata(), stored, snapshot.revision());
    });
  }

  private ConfigurationSnapshot snapshot(ConfigurationMetadata metadata, ConfigurationValueModel stored) {
    String raw = stored == null ? metadata.getDefaultValue() : stored.getValue();
    String revision = stored == null ? ConfigurationRevision.defaultRevision(metadata.getKey(), raw)
        : stored instanceof RuntimeConfigurationValueModel runtime
            ? ConfigurationRevision.storedRevision(metadata.getKey(), raw, runtime.getRevision(), runtime.getGeneration())
            : ConfigurationRevision.legacyStoredRevision(metadata.getKey(), raw);
    return new ConfigurationSnapshot(metadata.getKey(), convertValue(metadata, raw),
        stored == null ? null : raw, revision, copyMetadata(metadata));
  }

  private Object convertValue(ConfigurationMetadata metadata, String raw) {
    if (raw == null && metadata.isNullable()) { return null; }
    if (metadata.isManagementProtected()) {
      try {
        return switch (metadata.getValueType()) {
          case ARRAY, LIST -> Json.tryToList(raw).getOrElseThrow(cause -> new PlatformException("配置数组格式不合法", cause));
          case OBJECT, MAP -> Json.tryToMap(raw).getOrElseThrow(cause -> new PlatformException("配置对象格式不合法", cause));
          default -> metadata.getValueType().convert(raw);
        };
      } catch (RuntimeException ex) {
        throw new com.ouroboros.exception.BusinessException(503, "[CONFIGURATION_INVALID_VALUE] 受保护配置内容损坏: " + metadata.getKey(), ex);
      }
    }
    return metadata.getValueType().convert(raw);
  }

  private ConfigurationMetadata copyMetadata(ConfigurationMetadata metadata) {
    return Json.toBean(ConfigurationMetadata.class, Json.toJsonString(metadata));
  }

  private Object copyValue(Object value) {
    return value == null ? null : Json.toBean(Object.class, Json.toJsonString(value));
  }

  private List<String> sensitiveSources(ConfigurationMetadata metadata) {
    Object sources = metadata.getExtraProp("sensitiveSourceNames").orElse(List.of());
    return sources instanceof Collection<?> values ? values.stream().filter(String.class::isInstance)
        .map(String.class::cast).toList() : List.of();
  }

  private void rejectSensitiveReferences(String expression, Collection<ConfigurationMetadata> metadata) {
    if (expression == null) { return; }
    Set<String> protectedNames = new HashSet<>();
    metadata.stream().filter(ConfigurationMetadata::isManagementProtected).forEach(item -> {
      protectedNames.add(item.getKey());
      protectedNames.addAll(sensitiveSources(item));
    });
    Matcher matcher = REFERENCE_VARIABLE_PATTERN.matcher(expression);
    while (matcher.find()) {
      String reference = matcher.group(1).trim();
      if (protectedNames.stream().anyMatch(reference::contains)) {
        throw new com.ouroboros.exception.BusinessException(400, "[CONFIGURATION_SENSITIVE_REFERENCE_FORBIDDEN] 配置不能引用受保护值");
      }
    }
  }

  private void validateWithExtensions(String key, Object value) {
    List<ConfigurationValueValidator> validators = valueValidators == null ? List.of() : valueValidators;
    validators.stream()
        .filter(validator -> validator != null && validator.supports(key))
        .forEach(validator -> validator.validate(value));
  }

  private boolean isMissingValue(Object value, ConfigurationMetadata metadata) {
    if (value == null) {
      return true;
    }
    if (metadata == null) {
      return false;
    }
    return metadata.getValueType() == ConfigValueType.STRING
        && value instanceof String text
        && text.isBlank();
  }

  @Override
  @CacheEvict
  public void clearCache() {
  }

  /**
   * 发布更新事件
   *
   * @param key
   * @param value
   */
  private void publishUpdateEvent(String key, Object value) {
    // 发送事件通知
    applicationContext.publishEvent(new ConfigurationUpdateEvent<>(key, value));
  }
}
