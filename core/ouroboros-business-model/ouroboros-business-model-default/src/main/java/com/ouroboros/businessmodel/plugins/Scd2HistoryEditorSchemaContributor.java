package com.ouroboros.businessmodel.plugins;

import java.util.Map;

import com.ouroboros.businessmodel.schema.PluginEditorSchemaContributor;
import com.ouroboros.businessmodel.util.EditorSchemaTemplates;
import com.ouroboros.uischema.schema.SchemaNode;

public class Scd2HistoryEditorSchemaContributor implements PluginEditorSchemaContributor {

  @Override
  public String getPluginId() {
    return "scd2History";
  }

  @Override
  public SchemaNode contributeEditorFragment(Map<String, Object> context) {
    return EditorSchemaTemplates.generate("plugins/scd2-history", context, SchemaNode.class);
  }

  @Override
  public int getOrder() {
    return 110;
  }
}
