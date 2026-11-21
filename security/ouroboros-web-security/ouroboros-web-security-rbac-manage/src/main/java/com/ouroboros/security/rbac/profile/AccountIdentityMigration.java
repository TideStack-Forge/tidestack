package com.ouroboros.security.rbac.profile;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ouroboros.data.dsl.query.Query;
import com.ouroboros.data.model.TypedDataModel;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.exception.mapper.OuroborosExceptionMapper;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.identity.SystemSecurityIdentities;
import com.ouroboros.security.rbac.entity.UserEntity;

/** Explicit, resumable migration through the original QueryDSL model and protection plugin. */
@Service
public class AccountIdentityMigration {
  private final TypedDataModel<String, UserEntity> model;
  public AccountIdentityMigration(TypedDataModel<String, UserEntity> model) { this.model = model; }

  @Transactional
  public Map<String, Object> batch(String afterIdentity, int batchSize) {
    if (CurrentSecurityContextHolder.findIdentity().orElse(null) != SystemSecurityIdentities.system()) {
      throw new BusinessException(403, "公开标识迁移只允许受信系统操作");
    }
    if (batchSize < 1 || batchSize > 500) { throw new BusinessException(400, "迁移批次须为 1 至 500"); }
    var query = model.query().select(java.util.List.of("userIdentity", "publicSubjectId", "profileRevision",
        "emailVerified", "phoneNumberVerified")).where(Query.or(Query.field("isDeleted").eq(true), Query.field("isDeleted").eq(false),
        Query.field("isDeleted").eq(null))).clause("ORDER", "userIdentity ASC").clause("LIMIT", batchSize);
    if (afterIdentity != null) { query.andWhere(Query.field("userIdentity").gt(afterIdentity)); }
    var rows = query.execute().getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
    var identities = rows.stream().map(UserEntity::getUserIdentity).toList();
    var missingEmailVerification = missingFlag(identities, "emailVerified");
    var missingPhoneVerification = missingFlag(identities, "phoneNumberVerified");
    int updated = 0;
    for (UserEntity row : rows) {
      if (row.getPublicSubjectId() != null && !AccountProtectionDataModelPlugin.validSubject(row.getPublicSubjectId())) {
        throw new BusinessException(503, "[ACCOUNT_IDENTITY_INVALID] 存在非法公开标识，停止迁移");
      }
      var values = new HashMap<String, Object>();
      Map<String, Object> where = new LinkedHashMap<>();
      where.put("userIdentity", row.getUserIdentity());
      where.put("OR", java.util.Arrays.asList(Map.of("isDeleted", true), Map.of("isDeleted", false),
          java.util.Collections.singletonMap("isDeleted", null)));
      if (row.getPublicSubjectId() == null) {
        where.put("publicSubjectId", null);
        values.put("publicSubjectId", UUID.randomUUID().toString());
      }
      if (row.getProfileRevision() == null) { where.put("profileRevision", null); values.put("profileRevision", 0L); }
      if (missingEmailVerification.contains(row.getUserIdentity())) { where.put("emailVerified", null); values.put("emailVerified", false); }
      if (missingPhoneVerification.contains(row.getUserIdentity())) { where.put("phoneNumberVerified", null); values.put("phoneNumberVerified", false); }
      if (!values.isEmpty()) {
        updated += model.update(where, values).getOrElseThrow(OuroborosExceptionMapper::toOuroborosException).intValue();
      }
    }
    return Map.of("scanned", rows.size(), "updated", updated, "lastIdentity",
        rows.isEmpty() ? (afterIdentity == null ? "" : afterIdentity) : rows.getLast().getUserIdentity(), "hasMore", rows.size() == batchSize);
  }

  // BooleanValue intentionally normalizes null to false; detect missing storage values in the query.
  private java.util.Set<String> missingFlag(java.util.List<String> identities, String field) {
    if (identities.isEmpty()) { return java.util.Set.of(); }
    return model.query().select("userIdentity").where(Query.field("userIdentity").in(identities.toArray()), Query.field(field).eq(null),
        Query.or(Query.field("isDeleted").eq(true), Query.field("isDeleted").eq(false), Query.field("isDeleted").eq(null)))
        .execute().getOrElseThrow(OuroborosExceptionMapper::toOuroborosException).stream()
        .map(UserEntity::getUserIdentity).collect(java.util.stream.Collectors.toSet());
  }

}
