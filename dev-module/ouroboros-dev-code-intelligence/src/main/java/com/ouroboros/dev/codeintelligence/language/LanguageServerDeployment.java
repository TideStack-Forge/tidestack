package com.ouroboros.dev.codeintelligence.language;

import java.net.URI;
import java.nio.file.Path;
import java.util.function.Consumer;
import com.fasterxml.jackson.databind.JsonNode;

/** Transport/deployment lifecycle; engine plugins keep ownership of language projection. */
@FunctionalInterface
public interface LanguageServerDeployment {
  record Started(LanguageServerConnection connection, URI documentRoot) {}
  Started start(LanguageServerProvider provider, Path workspace, LanguageEnvironment environment, Consumer<JsonNode> notifications) throws Exception;

  default boolean available(LanguageServerProvider provider) { return provider.available(); }

  static LanguageServerDeployment stdio() {
    return (provider, workspace, environment, notifications) ->
        new Started(LanguageServerConnection.stdio(provider.command(workspace, environment), workspace, notifications), workspace.toUri());
  }
}
