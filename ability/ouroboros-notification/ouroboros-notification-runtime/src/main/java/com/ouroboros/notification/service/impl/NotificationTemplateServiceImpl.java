package com.ouroboros.notification.service.impl;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Collection;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.TreeMap;
import java.sql.SQLException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ouroboros.data.service.ModelServiceImpl;
import com.ouroboros.data.exception.UniqueConstraintViolationException;
import com.ouroboros.data.model.TypedDataModel;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.exception.mapper.OuroborosExceptionMapper;
import com.ouroboros.notification.service.NotificationTemplateService;
import com.ouroboros.notification.service.model.NotificationTemplateModel;
import com.ouroboros.notification.template.NotificationTemplate;
import com.ouroboros.notification.template.NotificationTemplateRevision;

/**
 * @author liansz
 */
@Service
public class NotificationTemplateServiceImpl
    extends ModelServiceImpl<String, NotificationTemplate, NotificationTemplateModel> implements NotificationTemplateService {

  @Override
  public NotificationTemplate getByName(String name) {
    return getOverride(name);
  }

  @Override
  public NotificationTemplateModel getOverride(String name) {
    var rows = storage().query(Map.of("where", Map.of("nameKey", NotificationTemplateRevision.nameKey(name))))
        .getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
    if (rows.size() > 1) { throw new BusinessException(503, "[NOTIFICATION_TEMPLATE_DUPLICATE] 模板名称不唯一"); }
    if (!rows.isEmpty()) { return rows.getFirst(); }
    // Reads remain available while legacy rows await the explicit lifecycle backfill.
    return storage().query(Map.of("where", Collections.singletonMap("nameKey", null)))
        .getOrElseThrow(OuroborosExceptionMapper::toOuroborosException).stream()
        .filter(row -> name.equalsIgnoreCase(row.getName())).reduce((first, second) -> {
          throw new BusinessException(503, "[NOTIFICATION_TEMPLATE_DUPLICATE] 模板名称不唯一");
        }).orElse(null);
  }

  @Override
  public boolean insertOverride(NotificationTemplateModel template) {
    requirePrepared(template);
    var inserted = storage().insert(template);
    if (inserted.isSuccess()) { template.setId(inserted.get().getId()); return true; }
    if (isDuplicate(inserted.getCause())) { return false; }
    throw OuroborosExceptionMapper.toOuroborosException(inserted.getCause());
  }

  @Override
  public boolean replaceOverride(NotificationTemplateModel template, long expectedRevision) {
    requirePrepared(template);
    if (template.getRevision() != expectedRevision + 1) {
      throw new BusinessException(400, "通知模板修订号必须递增一次");
    }
    var values = new LinkedHashMap<String, Object>();
    values.put("title", template.getTitle()); values.put("payloadSchema", template.getPayloadSchema());
    values.put("channel", template.getChannel()); values.put("isEnabled", template.isEnabled());
    values.put("revision", template.getRevision()); values.put("baseDefaultRevision", template.getBaseDefaultRevision());
    values.put("updateBy", template.getUpdateBy()); values.put("updateAt", template.getUpdateAt());
    return storage().update(Map.of("id", template.getId(), "revision", expectedRevision, "nameKey", template.getNameKey()), values)
        .getOrElseThrow(OuroborosExceptionMapper::toOuroborosException) == 1;
  }

  @Override
  public boolean deleteOverride(String id, long expectedRevision) {
    return storage().delete(Map.of("id", id, "revision", expectedRevision))
        .getOrElseThrow(OuroborosExceptionMapper::toOuroborosException) == 1;
  }

  @Override
  @Transactional
  public int initializeRuntimeManagement() {
    var rows = storage().query(Map.of()).getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
    var names = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
    for (var row : rows) {
      String key = NotificationTemplateRevision.nameKey(row.getName());
      String previous = names.putIfAbsent(row.getName(), row.getId());
      if (previous != null) {
        throw new BusinessException(503, "[NOTIFICATION_TEMPLATE_DUPLICATE] 重复模板 " + row.getName()
            + "，记录 " + previous + "、" + row.getId() + "；需人工核对");
      }
      if (row.getNameKey() != null && !row.getNameKey().equals(key)) {
        throw new BusinessException(503, "[NOTIFICATION_TEMPLATE_IDENTITY_INVALID] 模板名称标识不一致：" + row.getId());
      }
      if (row.getRevision() != null && row.getRevision() < 1) {
        throw new BusinessException(503, "[NOTIFICATION_TEMPLATE_REVISION_INVALID] 模板修订号非法：" + row.getId());
      }
    }
    int updated = 0;
    for (var row : rows) {
      var where = new LinkedHashMap<String, Object>(); where.put("id", row.getId());
      var values = new HashMap<String, Object>();
      if (row.getRevision() == null) { where.put("revision", null); values.put("revision", 1L); }
      if (row.getNameKey() == null) { where.put("nameKey", null); values.put("nameKey", NotificationTemplateRevision.nameKey(row.getName())); }
      if (!values.isEmpty()) {
        updated += storage().update(where, values).getOrElseThrow(OuroborosExceptionMapper::toOuroborosException).intValue();
      }
    }
    return updated;
  }

  private TypedDataModel<String, NotificationTemplateModel> storage() {
    return getDataModel(String.class, NotificationTemplateModel.class);
  }

  private void requirePrepared(NotificationTemplateModel template) {
    if (template.getRevision() == null || template.getRevision() < 1
        || !NotificationTemplateRevision.nameKey(template.getName()).equals(template.getNameKey())) {
      throw new BusinessException(503, "[NOTIFICATION_TEMPLATE_MIGRATION_REQUIRED] 模板缺少有效并发元数据");
    }
  }

  private boolean isDuplicate(Throwable error) {
    for (Throwable cause = error; cause != null; cause = cause.getCause()) {
      if (cause instanceof UniqueConstraintViolationException) { return true; }
      if (cause instanceof SQLException sql && ("23505".equals(sql.getSQLState()) || sql.getErrorCode() == 1062)) { return true; }
    }
    return false;
  }

  // Unconditional generic CRUD cannot remain a second management write contract.
  @Override public NotificationTemplate add(NotificationTemplate template) { throw versionRequired(); }
  @Override public List<? extends NotificationTemplate> add(Collection<? extends NotificationTemplate> templates) { throw versionRequired(); }
  @Override public Boolean update(String id, NotificationTemplate template) { throw versionRequired(); }
  @Override public Boolean partialUpdate(String id, Map<String, Object> template) { throw versionRequired(); }
  @Override public Boolean delete(String id) { throw versionRequired(); }
  @Override public Boolean delete(Collection<String> ids) { throw versionRequired(); }

  private BusinessException versionRequired() {
    return new BusinessException(400, "[NOTIFICATION_TEMPLATE_VERSION_REQUIRED] 请通过运行时管理用例携带预期版本写入");
  }
}
