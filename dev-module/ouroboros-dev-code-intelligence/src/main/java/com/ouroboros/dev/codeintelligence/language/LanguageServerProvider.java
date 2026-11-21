package com.ouroboros.dev.codeintelligence.language;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import com.fasterxml.jackson.databind.JsonNode;

/** Platform extensions contribute a Spring bean; the shared runtime owns LSP and process lifecycle. */
public interface LanguageServerProvider {
  String languageId();
  String fileExtension();
  boolean available();
  default String engineVersion() { return languageId(); }

  /** Prepare a trusted server command from verified, platform-local analysis inputs. */
  List<String> command(Path workspace, LanguageEnvironment environment) throws Exception;
  /** Translate the shared context snapshot to the server's declarations or protocol extension. */
  List<JsonNode> contextMessages(Path workspace, JsonNode context);

  /** Optional server-specific fence; standard servers deliver asynchronous diagnostics via polling. */
  default Optional<String> synchronizationMethod() { return Optional.empty(); }
}
