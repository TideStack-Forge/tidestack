package com.ouroboros.organizationstructure.definition;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.core.annotation.Order;

import io.vavr.control.Try;

import com.ouroboros.metadata.resource.ClasspathMetadataSource;
import com.ouroboros.metadata.resource.MetadataResource;
import com.ouroboros.metadata.resource.TypedMetadataCatalog;
import com.ouroboros.metadata.resource.TypedMetadataSpec;
import com.ouroboros.organizationstructure.PositionDefinition;
import com.ouroboros.organizationstructure.PositionDefinitionProvider;

@Order(BundledDefaultPositionDefinitionProvider.ORDER)
public class BundledDefaultPositionDefinitionProvider extends AbstractJsonDirectoryPositionDefinitionProvider
    implements PositionDefinitionProvider {
  static final int ORDER = 0;

  public BundledDefaultPositionDefinitionProvider() {
    super(java.util.Collections.emptyList());
  }

  @Override
  public List<PositionDefinition> getDefinitions() {
    var spec = TypedMetadataSpec.<PositionDefinition>builder(PositionDefinition.class)
        .typedSuffix(POSITION_DEFINITION_SUFFIX)
        .resourceParser(resource -> Try.of(() -> readDefinition(resource)))
        .keyExtractor(PositionDefinition::getKey)
        .parseFailurePolicy(TypedMetadataSpec.ParseFailurePolicy.ERROR)
        .blankKeyPolicy(TypedMetadataSpec.BlankKeyPolicy.ERROR)
        .build();
    return new TypedMetadataCatalog(new ClasspathMetadataSource()).load(spec)
        .values()
        .values()
        .stream()
        .collect(Collectors.toList());
  }

  private PositionDefinition readDefinition(MetadataResource resource) {
    return readDefinition(resource.content(), resource.resourceName());
  }

  @Override
  public int getOrder() {
    return ORDER;
  }
}
