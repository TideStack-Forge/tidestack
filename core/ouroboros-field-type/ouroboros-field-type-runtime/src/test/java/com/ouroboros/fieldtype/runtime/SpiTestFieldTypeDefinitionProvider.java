package com.ouroboros.fieldtype.runtime;

import java.util.Collections;
import java.util.List;

import com.ouroboros.fieldtype.FieldTypeCatalogContext;
import com.ouroboros.fieldtype.FieldTypeDefinition;
import com.ouroboros.fieldtype.FieldTypeDefinitionProvider;
import com.ouroboros.fieldtype.FieldTypeDefinitionSource;

public class SpiTestFieldTypeDefinitionProvider implements FieldTypeDefinitionProvider {
  @Override
  public boolean supports(FieldTypeCatalogContext context) {
    return context != null;
  }

  @Override
  public List<FieldTypeDefinition> load(FieldTypeCatalogContext context) {
    FieldTypeDefinition definition = new FieldTypeDefinition();
    definition.setName("spiProvided");
    definition.setLabel("SPI Provided");
    definition.setSource(FieldTypeDefinitionSource.JAR);
    definition.setSourceLocation("spi:test");
    return Collections.singletonList(definition);
  }
}
