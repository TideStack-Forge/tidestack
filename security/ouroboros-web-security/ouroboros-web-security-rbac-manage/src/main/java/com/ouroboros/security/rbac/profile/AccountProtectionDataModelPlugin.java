package com.ouroboros.security.rbac.profile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import com.ouroboros.data.dsl.Operators;
import com.ouroboros.data.dsl.SExpression;
import com.ouroboros.data.model.DataModel;
import com.ouroboros.data.model.DataModelPlugin;
import com.ouroboros.data.model.DataModelPluginBuilder;
import com.ouroboros.data.model.DataModelPluginContext;
import com.ouroboros.data.record.Record;
import com.ouroboros.data.record.RecordList;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.identity.SystemSecurityIdentities;
import io.vavr.control.Try;

/** Account invariants apply to typed, untyped, initialization and batch writes alike. */
public final class AccountProtectionDataModelPlugin implements DataModelPlugin {
  private static final Set<String> PROFILE = Set.of("nickname", "email", "emailVerified", "phoneNumber",
      "phoneNumberVerified", "profileValues", "profileRevision");

  @Override
  public Try<Record> insert(Map<String, Object> data, DataModelPluginContext context) {
    return Try.of(() -> prepareInsert(data)).flatMap(value -> DataModelPlugin.super.insert(value, context));
  }

  @Override
  public Try<RecordList> batchInsert(List<Map<String, Object>> data, DataModelPluginContext context) {
    return Try.of(() -> data.stream().map(this::prepareInsert).toList())
        .flatMap(values -> DataModelPlugin.super.batchInsert(values, context));
  }

  private Map<String, Object> prepareInsert(Map<String, Object> data) {
    if (data.get("publicSubjectId") != null || Boolean.TRUE.equals(data.get("emailVerified"))
        || Boolean.TRUE.equals(data.get("phoneNumberVerified"))
        || (data.get("profileRevision") instanceof Number n && n.longValue() != 0)) {
      throw denied();
    }
    var result = new LinkedHashMap<>(data);
    result.put("publicSubjectId", UUID.randomUUID().toString());
    result.put("profileRevision", 0L);
    result.put("emailVerified", false);
    result.put("phoneNumberVerified", false);
    return result;
  }

  @Override
  public Try<Long> update(SExpression<Boolean> where, Map<String, Object> data, DataModelPluginContext context) {
    boolean system = CurrentSecurityContextHolder.findIdentity().orElse(null) == SystemSecurityIdentities.system();
    if (data.containsKey("publicSubjectId")) {
      Object subject = data.get("publicSubjectId");
      if (!system || !(subject instanceof String text) || !validSubject(text)) { return Try.failure(denied()); }
      where = SExpression.create(Operators.AND, where,
          SExpression.create(Operators.IS_NULL, SExpression.field("publicSubjectId")));
    }
    if (!system && data.keySet().stream().anyMatch(PROFILE::contains)) { return Try.failure(denied()); }
    return DataModelPlugin.super.update(where, data, context);
  }

  public static boolean validSubject(String value) {
    return value != null && value.matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");
  }

  private static BusinessException denied() {
    return new BusinessException(403, "[ACCOUNT_PROTECTED_FIELD] 公开标识和资料状态只能通过账号资料服务维护");
  }

  public static final class Builder implements DataModelPluginBuilder {
    public boolean support(String name) { return "AccountProtection".equals(name); }
    public Optional<DataModelPlugin> build(DataModel model, Map<String, Object> config) {
      return Optional.of(new AccountProtectionDataModelPlugin());
    }
  }
}
