# 简化的装饰器实现示例

## 1. 审计字段装饰器

```java
package com.ouroboros.data.model.decorator.impl;

import com.ouroboros.data.model.DataModelMetaDecorator;
import com.ouroboros.data.model.DataModelMeta;
import com.ouroboros.data.model.DataModelFieldMeta;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 审计字段装饰器 - 使用深拷贝模式
 */
public class AuditFieldDecorator implements DataModelMetaDecorator {

    @Override
    public boolean supports(DataModelMeta originalMeta) {
        Boolean needAudit = originalMeta.getExtraProp(Boolean.class, "needAudit").orElse(false);
        return needAudit || isBusinessModel(originalMeta);
    }

    @Override
    public DataModelMeta decorate(DataModelMeta originalMeta) {
        DataModelMeta decorated = originalMeta.deepCopy();

        List<DataModelFieldMeta> fields = new ArrayList<>(decorated.getFields());
        fields.addAll(createAuditFields());
        decorated.setFields(fields);

        decorated.getExtraProps().put("auditApplied", true);
        return decorated;
    }

    private boolean isBusinessModel(DataModelMeta meta) {
        return meta.getFullName().startsWith("business.") ||
               meta.getFullName().startsWith("app.");
    }

    private List<DataModelFieldMeta> createAuditFields() {
        return Arrays.asList(
            createField("createdAt", "创建时间", "DateTime"),
            createField("updatedAt", "更新时间", "DateTime"),
            createField("createdBy", "创建人", "String"),
            createField("updatedBy", "更新人", "String")
        );
    }

    private DataModelFieldMeta createField(String name, String label, String type) {
        DataModelFieldMeta field = new DataModelFieldMeta();
        field.setName(name);
        field.setLabel(label);
        field.setType(type);
        field.setExtraProp("isSystem", true);
        return field;
    }
}
```

## 2. 软删除装饰器

```java
package com.ouroboros.data.model.decorator.impl;

import com.ouroboros.data.model.DataModelMetaDecorator;
import com.ouroboros.data.model.DataModelMeta;
import com.ouroboros.data.model.DataModelFieldMeta;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 软删除装饰器 - 使用深拷贝模式
 */
public class SoftDeleteDecorator implements DataModelMetaDecorator {

    @Override
    public boolean supports(DataModelMeta originalMeta) {
        Boolean softDeleteEnabled = originalMeta.getExtraProp(Boolean.class, "softDelete").orElse(false);
        return softDeleteEnabled && !hasDeleteField(originalMeta);
    }

    @Override
    public DataModelMeta decorate(DataModelMeta originalMeta) {
        DataModelMeta decorated = originalMeta.deepCopy();

        List<DataModelFieldMeta> fields = new ArrayList<>(decorated.getFields());
        fields.add(createDeleteField());
        fields.add(createDeleteTimeField());
        decorated.setFields(fields);

        decorated.getExtraProps().put("softDeleteApplied", true);
        return decorated;
    }

    private boolean hasDeleteField(DataModelMeta meta) {
        return meta.getFields().stream()
            .anyMatch(field -> "deleted".equals(field.getName()));
    }

    private DataModelFieldMeta createDeleteField() {
        DataModelFieldMeta field = new DataModelFieldMeta();
        field.setName("deleted");
        field.setLabel("删除标记");
        field.setType("Boolean");
        field.setIsNullable(false);
        field.setDefaultValue("false");
        field.setExtraProp("isSystem", true);
        return field;
    }

    private DataModelFieldMeta createDeleteTimeField() {
        DataModelFieldMeta field = new DataModelFieldMeta();
        field.setName("deletedAt");
        field.setLabel("删除时间");
        field.setType("DateTime");
        field.setExtraProp("isSystem", true);
        return field;
    }
}
```

## 3. 多租户代理装饰器

```java
package com.ouroboros.data.model.decorator.impl;

import com.ouroboros.data.model.decorator.DataModelMetaDecorator;
import com.ouroboros.data.model.DataModelMeta;

/**
 * 多租户装饰器 - 使用代理模式
 */
public class MultiTenantDecorator implements DataModelMetaDecorator {

    @Override
    public boolean supports(DataModelMeta originalMeta) {
        return originalMeta.getExtraProp(Boolean.class, "multiTenant").orElse(false);
    }

    @Override
    public DataModelMeta decorate(DataModelMeta originalMeta) {
        return new MultiTenantMetaProxy(originalMeta);
    }

    private static class MultiTenantMetaProxy implements DataModelMeta {
        private final DataModelMeta delegate;
        private volatile List<DataModelFieldMeta> cachedFields;

        public MultiTenantMetaProxy(DataModelMeta delegate) {
            this.delegate = delegate;
        }

        @Override
        public List<DataModelFieldMeta> getFields() {
            if (cachedFields == null) {
                synchronized (this) {
                    if (cachedFields == null) {
                        List<DataModelFieldMeta> fields = new ArrayList<>(delegate.getFields());
                        fields.add(createTenantField());
                        cachedFields = Collections.unmodifiableList(fields);
                    }
                }
            }
            return cachedFields;
        }

        // 其他方法代理到delegate
        @Override
        public String getName() { return delegate.getName(); }

        @Override
        public String getNamespace() { return delegate.getNamespace(); }

        // ... 其他代理方法

        private DataModelFieldMeta createTenantField() {
            DataModelFieldMeta field = new DataModelFieldMeta();
            field.setName("tenantId");
            field.setLabel("租户ID");
            field.setFieldType(FieldTypeEnum.STRING);
            field.setLength(32);
            field.setIsNotNull(true);
            field.setIsSystem(true);
            return field;
        }
    }
}
```