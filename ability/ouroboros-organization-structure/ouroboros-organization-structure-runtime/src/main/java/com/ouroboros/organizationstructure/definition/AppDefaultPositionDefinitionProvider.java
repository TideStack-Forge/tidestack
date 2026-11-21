package com.ouroboros.organizationstructure.definition;

import java.util.Collections;
import java.util.List;

import org.springframework.core.annotation.Order;

import com.ouroboros.organizationstructure.PositionDefinition;
import com.ouroboros.organizationstructure.PositionDefinitionProvider;

@Order(AppDefaultPositionDefinitionProvider.ORDER)
public class AppDefaultPositionDefinitionProvider extends AbstractJsonDirectoryPositionDefinitionProvider
    implements PositionDefinitionProvider {
  static final int ORDER = 100;
  private static final String POSITION_DIRECTORY = "organization-structure/positions";

  public AppDefaultPositionDefinitionProvider(java.nio.file.Path appHome) {
    super(Collections.singletonList(appHome.resolve(POSITION_DIRECTORY)));
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
