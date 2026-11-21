package com.ouroboros.organizationstructure.definition;

import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

import org.springframework.core.annotation.Order;

import com.ouroboros.AppContext;
import com.ouroboros.organizationstructure.PositionDefinition;
import com.ouroboros.organizationstructure.PositionDefinitionProvider;

@Order(RuntimeOverridePositionDefinitionProvider.ORDER)
public class RuntimeOverridePositionDefinitionProvider extends AbstractJsonDirectoryPositionDefinitionProvider
    implements PositionDefinitionProvider {
  static final int ORDER = 200;

  public RuntimeOverridePositionDefinitionProvider(AppContext appContext) {
    super(Collections.singletonList(Paths.get(appContext.getHomePath(), "data", "organization-structure", "positions")));
  }

  @Override
  public List<PositionDefinition> getDefinitions() {
    return loadDefinitions();
  }

  @Override
  public int getOrder() {
    return ORDER;
  }
}
