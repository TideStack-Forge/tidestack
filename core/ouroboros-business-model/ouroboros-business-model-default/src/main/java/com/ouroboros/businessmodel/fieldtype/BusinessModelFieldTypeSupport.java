package com.ouroboros.businessmodel.fieldtype;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.BeansException;

import com.ouroboros.common.app.util.SpringContextUtil;
import com.ouroboros.fieldtype.CompiledFieldType;
import com.ouroboros.fieldtype.DefaultFieldTypeCatalog;
import com.ouroboros.fieldtype.DefaultFieldTypeCompiler;
import com.ouroboros.fieldtype.FieldTypeCatalog;
import com.ouroboros.fieldtype.FieldTypeCatalogContext;
import com.ouroboros.fieldtype.FieldTypeDefinition;
import com.ouroboros.fieldtype.FieldTypeDefinitionReader;
import com.ouroboros.fieldtype.JarFieldTypeDefinitionProvider;
import com.ouroboros.uischema.schema.FormItemNode;

public final class BusinessModelFieldTypeSupport {
  private BusinessModelFieldTypeSupport() {
  }

  public static Optional<FieldTypeDefinition> getRuntimeMetadata(String name) {
    return getFieldTypeCatalog().flatMap(catalog -> catalog.getMetadata(FieldTypeCatalogContext.runtime(), name));
  }

  public static Optional<CompiledFieldType> compileRuntime(String name, Map<String, Object> params) {
    return getFieldTypeCatalog().flatMap(catalog ->
        catalog.compile(
                FieldTypeCatalogContext.runtime(),
                name,
                Optional.ofNullable(params).orElseGet(Collections::emptyMap))
            .toJavaOptional());
  }

  public static FormItemNode copyFormItem(FormItemNode item) {
    if (item == null) {
      return null;
    }
    var copy = new FormItemNode();
    copy.putProps(new LinkedHashMap<String, Object>(item.getProps()));
    return copy;
  }

  private static Optional<FieldTypeCatalog> getFieldTypeCatalog() {
    if (SpringContextUtil.getApplicationContext() != null) {
      try {
        return Optional.of(SpringContextUtil.getBean(FieldTypeCatalog.class));
      } catch (BeansException catalogLookupFailure) {
        // Fallback keeps model validation/projection available in plain unit tests and offline tooling.
        return Optional.of(FallbackFieldTypeCatalogHolder.INSTANCE);
      }
    }
    return Optional.of(FallbackFieldTypeCatalogHolder.INSTANCE);
  }

  private static class FallbackFieldTypeCatalogHolder {
    private static final FieldTypeCatalog INSTANCE = new DefaultFieldTypeCatalog(
        Collections.singletonList(new JarFieldTypeDefinitionProvider(new FieldTypeDefinitionReader())),
        new DefaultFieldTypeCompiler());
  }
}
