package com.ouroboros.businessmodel.plugins;

import java.util.Map;

import com.ouroboros.businessmodel.schema.PluginEditorSchemaContributor;
import com.ouroboros.businessmodel.util.EditorSchemaTemplates;
import com.ouroboros.uischema.schema.SchemaNode;

public class ArchiveDeleteEditorSchemaContributor implements PluginEditorSchemaContributor {

  @Override
  public String getPluginId() {
    return "archiveDelete";
  }

  @Override
  public SchemaNode contributeEditorFragment(Map<String, Object> context) {
    return EditorSchemaTemplates.generate("plugins/archive-delete", context, SchemaNode.class);
  }

  @Override
  public int getOrder() {
    return 100;
  }
}
