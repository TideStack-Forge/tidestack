package com.ouroboros.app.datamodel.plugin;

import java.util.*;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ObjectUtils;

import io.vavr.control.Try;

import com.ouroboros.data.dsl.Operators;
import com.ouroboros.data.dsl.SExpression;
import com.ouroboros.data.dsl.statement.ModelQueryStatement;
import com.ouroboros.data.dsl.statement.QueryStatement;
import com.ouroboros.data.model.DataModel;
import com.ouroboros.data.model.DataModelCenter;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.data.model.DataModelPlugin;
import com.ouroboros.data.model.DataModelPluginBuilder;
import com.ouroboros.data.model.DataModelPluginContext;
import com.ouroboros.data.normalize.QueryNormalizeContext;
import com.ouroboros.data.record.Record;
import com.ouroboros.data.record.RecordList;
import com.ouroboros.security.authority.accesscontrol.AccessControlParams;
import com.ouroboros.security.authority.accesscontrol.FieldConstraint;
import com.ouroboros.security.authority.accesscontrol.RowConstraint;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.util.Maps;

/**
 * @author liansz
 **/
public class AccessControlDataModelPlugin implements DataModelPlugin {

  private static final QueryNormalizeContext NORMALIZE_CONTEXT = QueryNormalizeContext.builder()
      .withDefaultNormalizers()
      .build();

  private final Config config;

  public AccessControlDataModelPlugin(Config config) {
    this.config = config;
  }

  @Override
  public Try<Record> insert(Map<String, Object> data, DataModelPluginContext context) {
    return Try.of(() -> {
      if (StringUtils.isNotEmpty(config.getCreateAuthority())) {
        var params = getAccessControlParams(config.getCreateAuthority());
        validateWriteFields(data, params);
        validateNewRows(data, params);
      }
      return data;
    }).flatMap(valid -> DataModelPlugin.super.insert(valid, context));
  }

  @Override
  public Try<RecordList> batchInsert(List<Map<String, Object>> dataList, DataModelPluginContext context) {
    return Try.of(() -> {
      if (StringUtils.isNotEmpty(config.getCreateAuthority())) {
        var params = getAccessControlParams(config.getCreateAuthority());
        for (var data : dataList) {
          validateWriteFields(data, params);
          validateNewRows(data, params);
        }
      }
      return dataList;
    }).flatMap(valid -> DataModelPlugin.super.batchInsert(valid, context));
  }

  @Override
  public Try<Long> update(SExpression<Boolean> where, Map<String, Object> data, DataModelPluginContext context) {
    if (StringUtils.isEmpty(config.getUpdateAuthority())) { return DataModelPlugin.super.update(where, data, context); }
    return Try.of(() -> {
      var params = getAccessControlParams(config.getUpdateAuthority());
      validateWriteFields(data, params);
      validateExpression(where, params.getFieldConstraint());
      var rows = params.getRowConstraint();
      if (rows.mode() == RowConstraint.Mode.NONE) { throw denied("没有可修改的数据行"); }
      if (rows.mode() == RowConstraint.Mode.FILTER) {
        var condition = normalizedRows(rows);
        Set<String> dimensions = new HashSet<>();
        collectFields(condition, dimensions);
        if (dimensions.stream().anyMatch(data::containsKey) && !matchesNewRow(condition, data)) {
          throw denied("修改后的记录不满足行权限；受控维度必须保持在授权范围内");
        }
      }
      return restrictedWhere(params, where);
    }).flatMap(filtered -> DataModelPlugin.super.update(filtered, data, context));
  }

  @Override
  public Try<Long> delete(SExpression<Boolean> where, DataModelPluginContext context) {
    if (StringUtils.isEmpty(config.getDeleteAuthority())) { return DataModelPlugin.super.delete(where, context); }
    return Try.of(() -> {
      var params = getAccessControlParams(config.getDeleteAuthority());
      validateExpression(where, params.getFieldConstraint());
      if (params.getRowConstraint().mode() == RowConstraint.Mode.NONE) { throw denied("没有可删除的数据行"); }
      return restrictedWhere(params, where);
    }).flatMap(filtered -> DataModelPlugin.super.delete(filtered, context));
  }

  @Override
  public Try<Long> count(QueryStatement statement, DataModelPluginContext context) {
    return protectedRead(statement).flatMap(authorized -> {
      if (StringUtils.isEmpty(config.getReadAuthority())) { return DataModelPlugin.super.count(authorized, context); }
      return Try.of(() -> getAccessControlParams(config.getReadAuthority())).flatMap(params -> {
        if (params.getRowConstraint().mode() == RowConstraint.Mode.NONE) { return Try.success(0L); }
        return Try.of(() -> {
          validateQuery(statement, readFields(params));
          return authorized.getBuilder().where(readWhere(params, authorized.getWhere())).build();
        }).flatMap(filtered -> DataModelPlugin.super.count(filtered, context));
      });
    });
  }

  @Override
  public Try<RecordList> query(QueryStatement statement, DataModelPluginContext context) {
    return read(statement, context, filtered -> DataModelPlugin.super.query(filtered, context));
  }

  @Override
  public Try<RecordList> queryForUpdate(QueryStatement statement, DataModelPluginContext context) {
    return read(statement, context, filtered -> DataModelPlugin.super.queryForUpdate(filtered, context));
  }

  private Try<RecordList> read(QueryStatement statement, DataModelPluginContext context,
      java.util.function.Function<QueryStatement, Try<RecordList>> execute) {
    return protectedRead(statement).flatMap(authorized -> {
      if (StringUtils.isEmpty(config.getReadAuthority())) { return execute.apply(authorized); }
      return Try.of(() -> getAccessControlParams(config.getReadAuthority())).flatMap(params -> {
        if (params.getRowConstraint().mode() == RowConstraint.Mode.NONE) { return Try.success(RecordList.empty()); }
        return Try.of(() -> {
          var fields = readFields(params);
          validateQuery(statement, fields);
          var builder = authorized.getBuilder();
          if (fields.mode() == FieldConstraint.Mode.NONE) { throw denied("没有可读取的业务字段"); }
          if (statement.getSelect().isEmpty() || statement.getSelect().stream().anyMatch(this::hasWildcard)) {
            if (fields.mode() != FieldConstraint.Mode.ALL || !fields.excluded().isEmpty()) {
              var model = context.getDataModel();
              if (model == null) { throw denied("字段约束缺少目标模型"); }
              List<SExpression<?>> selected = model.getFields().stream().map(field -> field.getName())
                  .filter(fields::allows).filter(name -> !getOmitFields(statement).contains(name))
                  .<SExpression<?>>map(name -> SExpression.field(name)).toList();
              if (selected.isEmpty()) { throw denied("没有可读取的业务字段"); }
              builder.select(selected);
            }
          } else {
            statement.getSelect().forEach(select -> validateExpression(select, fields));
          }
          return builder.where(readWhere(params, authorized.getWhere())).build();
        }).flatMap(execute);
      });
    });
  }

  private Try<QueryStatement> protectedRead(QueryStatement statement) {
    return Try.of(() -> {
      if (StringUtils.isNotBlank(config.getRequiredReadAuthority())) {
        if (!hasAuthority(config.getRequiredReadAuthority()) || !hasAuthority(config.getReadAuthority())) {
          throw new BusinessException("缺少历史快照读取权限：" + config.getRequiredReadAuthority());
        }
        if (getAccessControlParams(config.getReadAuthority()).getRowConstraint().mode() == RowConstraint.Mode.FILTER) {
          verifyCurrentSource(statement);
        }
      }
      if (StringUtils.isNotBlank(config.getDeletedReadAuthority()) && !hasAuthority(config.getDeletedReadAuthority())) {
        SExpression<Boolean> active = SExpression.create(Operators.EQ, SExpression.field(config.getDeletedField()), SExpression.constant(false));
        return statement.getBuilder().where(and(statement.getWhere(), active)).build();
      }
      if (StringUtils.isNotBlank(config.getDeletedReadAuthority()) && !hasAuthority(config.getReadAuthority())) {
        throw new BusinessException("缺少源模型读取权限：" + config.getReadAuthority());
      }
      if (!config.getFixedReadConditions().isEmpty()) {
        var fixed = NORMALIZE_CONTEXT.forClause("WHERE").normalizeCondition(config.getFixedReadConditions(), "root")
            .getOrElseThrow(error -> new BusinessException("特性查询固定范围不合法", error));
        return statement.getBuilder().where(and(statement.getWhere(), fixed)).build();
      }
      return statement;
    });
  }

  private SExpression<Boolean> readWhere(AccessControlParams params, SExpression<Boolean> where) {
    // Snapshot disclosure follows the current source row, not historical values of its filter fields.
    return StringUtils.isNotBlank(config.getRequiredReadAuthority()) ? where : restrictedWhere(params, where);
  }

  private void verifyCurrentSource(QueryStatement statement) {
    Object key = requiredEquality(statement.getWhere(), config.getSnapshotKeyField());
    if (key == null || StringUtils.isBlank(config.getSourceModel()) || StringUtils.isBlank(config.getSourceKeyField())) {
      throw new BusinessException("当前读取范围不能证明历史快照的披露权限");
    }
    var source = DataModelCenter.getDataModel(config.getSourceModel())
        .orElseThrow(() -> new BusinessException("历史快照源模型不可用"));
    SExpression<Boolean> reference = SExpression.create(Operators.EQ,
        SExpression.field(config.getSourceKeyField()), SExpression.constant(key));
    var scope = restrictedWhere(getAccessControlParams(config.getReadAuthority()), reference);
    var proof = QueryStatement.builder().from(source.getFullName(), source.getRawName()).where(scope).build();
    long readable = source.count(proof)
        .getOrElseThrow(error -> new BusinessException("无法验证当前源对象的读取权限", error));
    if (readable != 1) { throw new BusinessException("当前源对象不可读，不能披露历史快照"); }
  }

  /** Only an equality required by every matching row can prove a current object; OR never does. */
  private Object requiredEquality(SExpression<?> condition, String field) {
    if (condition == null || StringUtils.isBlank(field)) { return null; }
    if (condition.getOperator() == Operators.AND) {
      for (Object child : condition.getParams()) {
        if (child instanceof SExpression<?> expression) {
          Object value = requiredEquality(expression, field);
          if (value != null) { return value; }
        }
      }
    }
    if (condition.getOperator() == Operators.EQ && condition.getParams().size() == 2) {
      for (int index = 0; index < 2; index++) {
        if (condition.getParam(index) instanceof SExpression<?> path && path.getOperator() == Operators.FIELD
            && (path.getParams().equals(List.of(field)) || path.getParams().equals(List.of("root", field)))
            && condition.getParam(1 - index) instanceof SExpression<?> value
            && value.getOperator() == Operators.CONSTANT && value.getParams().size() == 1) {
          return value.getParam(0);
        }
      }
    }
    return null;
  }

  private FieldConstraint readFields(AccessControlParams params) {
    var fields = params.getFieldConstraint();
    var mappings = config.getSourceFieldMappings();
    return new FieldConstraint(fields.mode(), fields.fields().stream()
        .map(name -> mappings.getOrDefault(name, name)).collect(java.util.stream.Collectors.toSet()),
        fields.excluded().stream().map(name -> mappings.getOrDefault(name, name)).collect(java.util.stream.Collectors.toSet()));
  }

  private void validateQuery(QueryStatement statement, FieldConstraint fields) {
    boolean constrained = fields.mode() != FieldConstraint.Mode.ALL || !fields.excluded().isEmpty();
    if ((constrained || isDelegated()) && (!statement.getJoins().isEmpty() || !statement.getWith().isEmpty()
        || !statement.getUnions().isEmpty() || !statement.getPopulate().isEmpty())) {
      throw denied("当前权限不支持关联或跨模型查询");
    }
    validateExpression(statement.getWhere(), fields);
    validateExpression(statement.getGroup(), fields);
    validateExpression(statement.getHaving(), fields);
    statement.getOrders().forEach(order -> requireField(order.getColumn(), fields));
  }

  private boolean hasWildcard(SExpression<?> select) {
    if (select.getOperator() != Operators.FIELD && select.getOperator() != Operators.COLUMNS) { return false; }
    return select.getParams().stream().anyMatch(param -> "*".equals(param)
        || param instanceof SExpression<?> nested && hasWildcard(nested));
  }

  private void validateExpression(SExpression<?> expression, FieldConstraint fields) {
    if (expression == null || expression.isEmpty()) { return; }
    if (expression.getOperator() == Operators.FIELD) {
      String field = localField(expression);
      requireField(field, fields);
      return;
    }
    if (expression.getOperator() == Operators.CONSTANT) { return; }
    for (Object item : expression.getParams()) {
      if (item instanceof SExpression<?> nested) { validateExpression(nested, fields); }
      else if (item instanceof QueryStatement || item instanceof Map<?, ?>) {
        if (fields.mode() != FieldConstraint.Mode.ALL || !fields.excluded().isEmpty() || isDelegated()) {
          throw denied("当前权限不支持子查询或未解析的表达式");
        }
      }
    }
  }

  private String localField(SExpression<?> expression) {
    List<Object> path = expression.getParams();
    if (path.size() == 1 && path.getFirst() instanceof String name) { return name; }
    if (path.size() == 2 && "root".equals(path.getFirst()) && path.getLast() instanceof String name) { return name; }
    throw denied("当前权限不支持未解析的关联字段");
  }

  private void requireField(String name, FieldConstraint fields) {
    if (!fields.allows(name)) { throw denied("字段未授权: " + name); }
  }

  private void validateWriteFields(Map<String, Object> data, AccessControlParams params) {
    data.keySet().forEach(name -> requireField(name, params.getFieldConstraint()));
  }

  private void validateNewRows(Map<String, Object> data, AccessControlParams params) {
    var rows = params.getRowConstraint();
    if (rows.mode() == RowConstraint.Mode.NONE
        || (rows.mode() == RowConstraint.Mode.FILTER && !matchesNewRow(normalizedRows(rows), data))) {
      throw denied("新记录不满足行权限");
    }
  }

  /** Only the normalized EQ/IN subset can prove a new record satisfies its row constraint. */
  private boolean matchesNewRow(SExpression<?> expression, Map<String, Object> data) {
    var operator = expression.getOperator();
    if (operator == Operators.AND || operator == Operators.OR) {
      var children = expression.getParams().stream().map(item -> {
        if (!(item instanceof SExpression<?> child)) { throw denied("行条件无法执行"); }
        return child;
      }).toList();
      return operator == Operators.AND ? children.stream().allMatch(child -> matchesNewRow(child, data))
          : children.stream().anyMatch(child -> matchesNewRow(child, data));
    }
    if ((operator != Operators.EQ && operator != Operators.IN) || expression.getParams().size() != 2
        || !(expression.getParam(0) instanceof SExpression<?> field) || field.getOperator() != Operators.FIELD
        || !(expression.getParam(1) instanceof SExpression<?> constant) || constant.getOperator() != Operators.CONSTANT) {
      throw denied("此行条件不支持安全写入");
    }
    String name = localField(field);
    if (!data.containsKey(name)) { return false; }
    Object expected = constant.getParam(0);
    return operator == Operators.IN && expected instanceof Collection<?> options
        ? options.contains(data.get(name)) : Objects.equals(expected, data.get(name));
  }

  private void collectFields(SExpression<?> expression, Set<String> fields) {
    if (expression.getOperator() == Operators.FIELD) { fields.add(localField(expression)); }
    else if (expression.getOperator() != Operators.CONSTANT) {
      expression.getParams().stream().filter(SExpression.class::isInstance).map(SExpression.class::cast)
          .forEach(child -> collectFields(child, fields));
    }
  }

  private SExpression<Boolean> normalizedRows(RowConstraint rows) {
    return NORMALIZE_CONTEXT.forClause("WHERE").normalizeCondition(rows.condition(), "root")
        .getOrElseThrow(cause -> new com.ouroboros.security.exception.AuthorizationDeniedException("行条件无法解析", cause));
  }

  private SExpression<Boolean> restrictedWhere(AccessControlParams params, SExpression<Boolean> where) {
    var rows = params.getRowConstraint();
    if (rows.mode() == RowConstraint.Mode.NONE) { throw denied("没有可访问的数据行"); }
    if (rows.mode() == RowConstraint.Mode.ALL) { return where; }
    return and(where, normalizedRows(rows));
  }

  private SExpression<Boolean> and(SExpression<Boolean> left, SExpression<Boolean> right) {
    return left == null || left.isEmpty() ? right : SExpression.create(Operators.AND, left, right);
  }

  private List<String> getOmitFields(QueryStatement statement) {
    if (statement instanceof ModelQueryStatement model && model.getOmitClause() != null) {
      return List.copyOf(model.getOmitClause().getFields());
    }
    return statement.getOmit();
  }

  private AccessControlParams getAccessControlParams(String authority) {
    var scoped = CurrentSecurityContextHolder.findAuthentication()
        .flatMap(authentication -> authentication.findScopedAccessControlParams(authority));
    if (scoped.isPresent()) { return scoped.get(); }
    var identity = CurrentSecurityContextHolder.findIdentity().orElseGet(() -> CurrentSecurityContextHolder.findAuthentication()
        .map(authentication -> authentication.getIdentity()).orElse(null));
    return com.ouroboros.security.authorize.AuthorizationEvaluator.standard()
        .evaluate(identity, com.ouroboros.security.authorize.AuthorizationEvaluator.PLATFORM_API, authority).requirePermit();
  }

  private boolean hasAuthority(String authority) {
    return com.ouroboros.security.authorize.AuthorizationEvaluator.standard()
        .evaluate(CurrentSecurityContextHolder.findIdentity().orElseGet(() -> CurrentSecurityContextHolder.findAuthentication()
            .map(authentication -> authentication.getIdentity()).orElse(null)),
            com.ouroboros.security.authorize.AuthorizationEvaluator.PLATFORM_API, authority)
        instanceof com.ouroboros.security.authorize.AuthorizationDecision.Permit;
  }

  private boolean isDelegated() {
    return CurrentSecurityContextHolder.findIdentity().map(identity -> identity.getCredentialContext().kind()
        == com.ouroboros.security.identity.CredentialContext.Kind.DELEGATED).orElse(false);
  }

  private com.ouroboros.security.exception.AuthorizationDeniedException denied(String reason) {
    return new com.ouroboros.security.exception.AuthorizationDeniedException(reason);
  }

  public static class Config {
    private String createAuthority, deleteAuthority, updateAuthority, readAuthority;
    private String requiredReadAuthority, deletedReadAuthority, deletedField;
    private String sourceModel, sourceKeyField, snapshotKeyField;
    private Map<String, String> sourceFieldMappings = Map.of();
    private Map<String, Object> fixedReadConditions = Map.of();

    public String getRequiredReadAuthority() { return requiredReadAuthority; }
    public void setRequiredReadAuthority(String value) { requiredReadAuthority = value; }
    public String getDeletedReadAuthority() { return deletedReadAuthority; }
    public void setDeletedReadAuthority(String value) { deletedReadAuthority = value; }
    public String getDeletedField() { return deletedField; }
    public void setDeletedField(String value) { deletedField = value; }
    public String getSourceModel() { return sourceModel; }
    public void setSourceModel(String value) { sourceModel = value; }
    public String getSourceKeyField() { return sourceKeyField; }
    public void setSourceKeyField(String value) { sourceKeyField = value; }
    public String getSnapshotKeyField() { return snapshotKeyField; }
    public void setSnapshotKeyField(String value) { snapshotKeyField = value; }
    public Map<String, String> getSourceFieldMappings() { return sourceFieldMappings; }
    public void setSourceFieldMappings(Map<String, String> value) { sourceFieldMappings = Map.copyOf(value); }
    public Map<String, Object> getFixedReadConditions() { return fixedReadConditions; }
    public void setFixedReadConditions(Map<String, Object> value) { fixedReadConditions = Map.copyOf(value); }


    public String getCreateAuthority() {
      return createAuthority;
    }

    public void setCreateAuthority(String createAuthority) {
      this.createAuthority = createAuthority;
    }

    public String getDeleteAuthority() {
      return deleteAuthority;
    }

    public void setDeleteAuthority(String deleteAuthority) {
      this.deleteAuthority = deleteAuthority;
    }

    public String getUpdateAuthority() {
      return updateAuthority;
    }

    public void setUpdateAuthority(String updateAuthority) {
      this.updateAuthority = updateAuthority;
    }

    public String getReadAuthority() {
      return readAuthority;
    }

    public void setReadAuthority(String readAuthority) {
      this.readAuthority = readAuthority;
    }
  }

  public static class Builder implements DataModelPluginBuilder {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Override
    public boolean support(String name) {
      return "AccessControl".equalsIgnoreCase(name);
    }

    @Override
    public Optional<DataModelPlugin> build(DataModel dataModel, Map<String, Object> configMap) {
      var configTry = Maps.tryToBean(Config.class, configMap);
      if (!configTry.isSuccess()) {
        logger.error("数据模型 {} 访问控制插件配置参数错误！", dataModel.getFullName());
        return Optional.empty();
      }
      var config = configTry.get();
      if (ObjectUtils.isEmpty(config.getCreateAuthority()) && ObjectUtils.isEmpty(config.getDeleteAuthority())
          && ObjectUtils.isEmpty(config.getUpdateAuthority()) && ObjectUtils.isEmpty(config.getReadAuthority())) {
        logger.warn("数据模型 {} 访问控制插件未设置任何参数，访问控制未生效！", dataModel.getFullName());
        return Optional.empty();
      }

      return Optional.of(new AccessControlDataModelPlugin(config));
    }
  }
}
