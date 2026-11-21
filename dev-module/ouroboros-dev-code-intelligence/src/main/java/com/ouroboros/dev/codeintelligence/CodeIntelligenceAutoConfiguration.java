package com.ouroboros.dev.codeintelligence;

import com.ouroboros.dev.codeintelligence.service.CodeContextService;
import java.util.List;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import com.ouroboros.dev.metadata.ApplicationMetadataSnapshotProvider;
import com.ouroboros.dev.codeintelligence.language.*;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
@AutoConfiguration
@Import({CodeContextController.class, EditorLanguageController.class, CodeEnvironmentController.class})
public class CodeIntelligenceAutoConfiguration {
  @Bean
  public EditorLanguageSessions editorLanguageSessions(CodeContextService contexts, EditorLanguageServices languages, CodeEnvironmentResolver environments) {
    return new EditorLanguageSessions(contexts, languages, environments);
  }
  @Bean
  public JavascriptLanguageServerProvider javascriptLanguageServerProvider() { return new JavascriptLanguageServerProvider(); }
  @Bean
  public GroovyLanguageServerProvider groovyLanguageServerProvider(
      @Value("${ouroboros.code-intelligence.groovy.distribution:}") String distribution,
      @Value("${ouroboros.code-intelligence.groovy.max-heap-mib:768}") int heap,
      @Value("${ouroboros.code-intelligence.groovy.processors:4}") int processors) {
    return new GroovyLanguageServerProvider(distribution.isBlank() ? null : Path.of(distribution), heap, processors);
  }
  @Bean
  @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean(LanguageServerDeployment.class)
  public LanguageServerDeployment languageServerDeployment(org.springframework.core.env.Environment environment,
      org.springframework.beans.factory.ObjectProvider<com.ouroboros.dev.runtime.service.DevRuntimeService> runtimes) {
    String mode = environment.getProperty("ouroboros.code-intelligence.language.deployment", "stdio");
    return switch (mode) {
      case "stdio" -> LanguageServerDeployment.stdio();
      case "container" -> new ManagedLanguageServerDeployment(runtimes.getObject(),
          environment.getProperty("ouroboros.code-intelligence.language.backend", com.ouroboros.dev.runtime.model.BackendType.class, com.ouroboros.dev.runtime.model.BackendType.DOCKER),
          org.springframework.boot.context.properties.bind.Binder.get(environment).bind("ouroboros.code-intelligence.language.images", org.springframework.boot.context.properties.bind.Bindable.mapOf(String.class, String.class)).orElse(java.util.Map.of()),
          environment.getProperty("ouroboros.code-intelligence.language.memory-bytes", Long.class, 1073741824L),
          environment.getProperty("ouroboros.code-intelligence.language.nano-cpus", Long.class, 4000000000L));
      default -> throw new IllegalArgumentException("Unknown language deployment: " + mode);
    };
  }
  @Bean
  public EditorLanguageServices editorLanguageServices(List<LanguageServerProvider> providers, LanguageServerDeployment deployment,
      @Value("${ouroboros.code-intelligence.language.storage:${java.io.tmpdir}/ouroboros-language}") String storage,
      @Value("${ouroboros.code-intelligence.language.max-processes:4}") int capacity,
      @Value("${ouroboros.code-intelligence.language.idle-minutes:10}") int idle) {
    return new EditorLanguageServices(providers, Path.of(storage).resolve("workspaces"), capacity, java.time.Duration.ofMinutes(idle), java.time.Clock.systemUTC(), deployment);
  }
  @Bean
  public CodeEnvironmentResolver codeEnvironmentResolver(com.ouroboros.devhelper.dev.DevHelperCommandService commands,
      @Value("${ouroboros.code-intelligence.language.storage:${java.io.tmpdir}/ouroboros-language}") String storage,
      @Value("${ouroboros.code-intelligence.language.max-cache-bytes:1073741824}") long cacheBytes,
      @Value("${ouroboros.code-intelligence.language.manifest-timeout-millis:90000}") long manifestTimeoutMillis) {
    var local = new io.github.classgraph.ClassGraph().getClasspathFiles().stream().map(java.io.File::toPath).toList();
    return new RemoteCodeEnvironments(commands, Path.of(storage).resolve("artifacts"), cacheBytes, local, manifestTimeoutMillis);
  }
  @Bean
  public CodeContextService codeContextService(List<CodeContextProvider> providers, List<ApplicationMetadataSnapshotProvider> snapshots, EditorLanguageServices services, CodeEnvironmentResolver environments) {
    var references = new PlatformCodeEnvironment();
    return new CodeContextService(providers, snapshots, references::describe, new LanguageScriptOutputAnalysis(services, environments));
  }
}
