package com.ouroboros.devhelper.command;

import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import com.ouroboros.devhelper.codeenvironment.ApplicationCodeEnvironment;
import com.ouroboros.devhelper.util.HelperRuntimeIdentity;
import org.springframework.stereotype.Component;

/** Only environment discovery/content transfer crosses the existing helper command channel. */
@Component
public final class CodeEnvironmentCommand implements Command {
  public static final String NAME = "codeEnvironment";
  private final ApplicationCodeEnvironment environment;
  public CodeEnvironmentCommand(ApplicationCodeEnvironment environment) { this.environment = environment; }
  public String getCommand() { return NAME; }
  public CompletableFuture<CommandResult> execute(CommandRequest request) {
    var params = request.getParams();
    if (params == null || !HelperRuntimeIdentity.current().getInstanceId().equals(params.get("helperInstanceId")))
      return CompletableFuture.completedFuture(new CommandResult(request).setMessage("开发助手实例已变更"));
    return CompletableFuture.supplyAsync(() -> {
      try {
        Object data = switch (String.valueOf(params.get("action"))) {
          case "manifest" -> environment.manifest();
          case "read" -> Base64.getEncoder().encodeToString(environment.read(String.valueOf(params.get("version")),
              String.valueOf(params.get("sha256")), ((Number) params.get("offset")).longValue()));
          default -> throw new IllegalArgumentException("Unknown code environment operation");
        };
        return new CommandResult(request, data);
      } catch (java.io.IOException | RuntimeException failure) {
        org.slf4j.LoggerFactory.getLogger(CodeEnvironmentCommand.class).warn("Application code environment export failed", failure);
        return new CommandResult(request).setMessage("应用代码环境同步失败: " + failure.getMessage());
      }
    });
  }
}
