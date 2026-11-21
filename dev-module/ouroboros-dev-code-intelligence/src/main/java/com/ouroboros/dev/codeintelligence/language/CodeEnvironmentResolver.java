package com.ouroboros.dev.codeintelligence.language;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@FunctionalInterface
public interface CodeEnvironmentResolver {
  CompletableFuture<LanguageEnvironment> resolve(String app, Map<String, Object> identity);
}
