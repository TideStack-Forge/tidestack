package com.ouroboros.devhelper;

import jakarta.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.ouroboros.devhelper.command.Command;
import com.ouroboros.devhelper.command.CommandRequest;
import com.ouroboros.devhelper.command.CommandResult;
import com.ouroboros.devhelper.command.Commands;
import com.ouroboros.devhelper.metadata.MetadataRefreshProtocol;
import com.ouroboros.devhelper.util.HelperRuntimeIdentity;
import com.ouroboros.devhelper.util.JarUtil;
import com.ouroboros.devhelper.transport.DevHelperServerTransport;
import com.ouroboros.devhelper.transport.TransportSubscription;

@Component
public class HelperCommandDispatcher {
  private static final Logger logger = LoggerFactory.getLogger(HelperCommandDispatcher.class);
  private static final long DEFAULT_TIMEOUT = 30000;

  private final DevHelperServerTransport transport;
  private final TransportSubscription commandSubscription;

  private String appName;
  private final String profile;
  private final boolean testApp;
  private Map<String, Command> COMMANDS = new TreeMap<>(String::compareToIgnoreCase);
  private Map<String, CompletableFuture<Object>> commandResponseMap;

  private final ScheduledExecutorService scheduler =
      Executors.newSingleThreadScheduledExecutor(task -> {
        var thread = new Thread(task, "dev-helper-command-timeout");
        thread.setDaemon(true);
        return thread;
      });

  public HelperCommandDispatcher(String appName, DevHelperServerTransport transport, List<Command> commands) {
    this(appName, "", transport, commands);
  }

  public HelperCommandDispatcher(String appName, String profile, DevHelperServerTransport transport, List<Command> commands) {
    this(appName, profile, false, transport, commands);
  }

  @Autowired
  public HelperCommandDispatcher(@Value("${spring.application.name:default}") String appName, @Value("${spring.profiles.active:}") String profile, @Value("${ouroboros.dev-helper.test-app:false}") boolean testApp, DevHelperServerTransport transport, @Autowired(required = false) List<Command> commands) {
    this.appName = appName;
    this.profile = profile;
    this.testApp = testApp;
    this.transport = transport;
    Optional.ofNullable(commands)
        .orElseGet(Collections::emptyList)
        .forEach(c -> COMMANDS.put(c.getCommand(), c));
    commandSubscription = transport.subscribe(this::handleCommand);
    logger.info("HelperCommandDispatcher initialized for app: {}, commands: {}", appName, COMMANDS.keySet());
  }

  // 助手启动后，发送上线通知
  @EventListener
  void onApplicationStarted(ApplicationStartedEvent event) {
    if (!testApp) {
      logger.info("Application started, sending knock knock notification");
      knockKnock();
    }
  }

  private void handleCommand(CommandRequest cmd) {
    if (cmd == null || cmd.getCommand() == null) {
      logger.warn("Rejected malformed helper command");
      return;
    }
    var isSendToMe = appName.equals(cmd.getAppName());
    var isKnockKnock = Commands.KNOCK_KNOCK.getCommand().equalsIgnoreCase(cmd.getCommand());
    if (!isSendToMe && !isKnockKnock) {
      logger.debug("Command not for this app: command={}, targetApp={}, thisApp={}",
          cmd.getCommand(), cmd.getAppName(), appName);
      return;
    }

    if (isKnockKnock) {
      cmd.setAppName(appName);
    }

    var identity = HelperRuntimeIdentity.current();
    if ((cmd.getProfile() != null && !cmd.getProfile().equals(profile))
        || (cmd.getHelperInstanceId() != null && !cmd.getHelperInstanceId().equals(identity.getInstanceId()))
        || (cmd.getHelperStartedAtEpochMillis() != null && cmd.getHelperStartedAtEpochMillis() != identity.getStartedAtEpochMillis())
        || (cmd.getAppIncarnationId() != null && !cmd.getAppIncarnationId().equals(identity.getAppIncarnationId()))) {
      logger.debug("Ignored helper command for a different process or profile");
      return;
    }

    // 检查命令是否存在
    Command command = COMMANDS.get(cmd.getCommand());
    if (command == null) {
      logger.warn("Unknown command received: {}", cmd.getCommand());
      return;
    }

    logger.info("Executing command: command={}, appName={}, timeout={}",
        cmd.getCommand(), cmd.getAppName(), cmd.getTimeout());

    // 准备命令执行超时机制
    CompletableFuture<Void> deadline = getDelayCompletableFuture(cmd.getTimeout());
    CompletableFuture<CommandResult> timeoutResult = deadline
        .thenApply(__ -> {
          logger.warn("Command execution timeout: command={}, appName={}", cmd.getCommand(), cmd.getAppName());
          return new CommandResult(cmd).setMessage("执行超时");
        });

    // 执行命令
    CompletableFuture<CommandResult> result;
    try {
      result = command.execute(cmd)
          .handle((res, ex) -> {
            if (ex != null) {
              logger.error("Command execution failed: command={}, appName={}", cmd.getCommand(), cmd.getAppName(), ex);
              return new CommandResult(cmd).setMessage(ex.getMessage() == null ? "命令执行失败" : ex.getMessage());
            } else {
              logger.info("Command execution completed: command={}, appName={}, success={}",
                  cmd.getCommand(), cmd.getAppName(), res.getSuccess());
            }
            return res;
          });
    } catch (RuntimeException exception) {
      deadline.cancel(true);
      logger.error("Command execution failed before returning a result: command={}, appName={}",
          cmd.getCommand(), cmd.getAppName(), exception);
      sendResult(new CommandResult(cmd)
          .setSuccess(false)
          .setMessage(exception.getMessage() == null ? "命令执行失败" : exception.getMessage()));
      return;
    }

    // 发送结果
    result.handle((value, failure) -> failure == null ? value : new CommandResult(cmd)
        .setSuccess(false).setMessage("命令执行失败: " + failure.getClass().getSimpleName()))
        .acceptEither(timeoutResult, this::sendResult).whenComplete((ignored, error) -> {
          deadline.cancel(true);
          if (error != null) logger.error("Could not send command result: command={}, appName={}", cmd.getCommand(), cmd.getAppName(), error);
        });
  }

  // 发送命令执行结果
  private void sendResult(CommandResult result) {
    var identity = HelperRuntimeIdentity.current();
    result.setAppName(appName).setProfile(profile).setHelperInstanceId(identity.getInstanceId())
        .setHelperStartedAtEpochMillis(identity.getStartedAtEpochMillis()).setAppIncarnationId(identity.getAppIncarnationId());
    logger.debug("Sending command result: command={}, appName={}, success={}", 
        result.getCommand(), result.getAppName(), result.getSuccess());
    transport.send(result);
  }

  // 发送上线通知
  public void knockKnock() {
    Map<String, Object> payload = new HashMap<>();
    payload.put("version", JarUtil.getHelperVersion());
    payload.put("source", "DevHelper");
    MetadataRefreshProtocol.addHelperIdentity(payload, HelperRuntimeIdentity.current());
    CommandResult notify = new CommandResult()
        .setAppName(appName)
        .setCommand(Commands.KNOCK_KNOCK.getCommand())
        .setData(payload)
        .setSuccess(true);
    sendResult(notify);
  }

  @PreDestroy
  void shutdown() {
    try {
      commandSubscription.close();
    } finally {
      scheduler.shutdownNow();
    }
  }

  // 创建一个可取消的延迟任务
  private CompletableFuture<Void> getDelayCompletableFuture(long timeout) {
    CompletableFuture<Void> future = new CompletableFuture<>();
    ScheduledFuture<?> scheduled = scheduler.schedule(() -> future.complete(null),
        timeout > 0 ? timeout : DEFAULT_TIMEOUT,
        TimeUnit.MILLISECONDS);
    future.whenComplete((__, ___) -> scheduled.cancel(true));
    return future;
  }

}
