package com.ouroboros.widget.service.impl;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import jakarta.annotation.Priority;

import org.junit.jupiter.api.Test;

import com.ouroboros.widget.WidgetDefinition;
import com.ouroboros.widget.WidgetDefinitionProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WidgetDefinitionServiceImplTest {

  @Test
  void highPriorityProviderOverridesLowPriorityWidgetDefinitionWithSameName() {
    var service = new WidgetDefinitionServiceImpl(List.of(new HighPriorityProvider(), new LowPriorityProvider()));

    var definitions = service.getAllWidgetDefinitions();

    assertEquals(1, definitions.size());
    assertEquals("high", definitions.get(0).getTitle());
  }

  @Priority(0)
  private static class LowPriorityProvider implements WidgetDefinitionProvider {
    @Override
    public Optional<WidgetDefinition> getWidgetDefinition(String name) {
      return Optional.of(widget("shared", "low"));
    }

    @Override
    public List<WidgetDefinition> getAllWidgetDefinitions() {
      return Collections.singletonList(widget("shared", "low"));
    }
  }

  @Priority(100)
  private static class HighPriorityProvider implements WidgetDefinitionProvider {
    @Override
    public Optional<WidgetDefinition> getWidgetDefinition(String name) {
      return Optional.of(widget("shared", "high"));
    }

    @Override
    public List<WidgetDefinition> getAllWidgetDefinitions() {
      return Collections.singletonList(widget("shared", "high"));
    }
  }

  private static WidgetDefinition widget(String name, String title) {
    WidgetDefinition definition = new WidgetDefinition();
    definition.setName(name);
    definition.setTitle(title);
    return definition;
  }
}
