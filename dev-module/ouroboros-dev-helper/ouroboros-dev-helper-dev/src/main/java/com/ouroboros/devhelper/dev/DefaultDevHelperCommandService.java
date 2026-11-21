package com.ouroboros.devhelper.dev;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

import jakarta.annotation.PreDestroy;

import org.apache.commons.lang3.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.ouroboros.dev.DevAppHolder;
import com.ouroboros.devhelper.command.CommandRequest;
import com.ouroboros.devhelper.command.CommandResult;
import com.ouroboros.devhelper.command.CommandFailureType;
import com.ouroboros.devhelper.command.Commands;
import com.ouroboros.devhelper.metadata.MetadataRefreshProtocol;
import com.ouroboros.devhelper.transport.DevHelperClientTransport;
import com.ouroboros.devhelper.transport.TransportSubscription;

/**
 * @author liansz
 **/
@Component
public class DefaultDevHelperCommandService implements DevHelperCommandService {
  private static final Logger logger = LoggerFactory.getLogger(DefaultDevHelperCommandService.class);
  private final static long DEFAULT_TIMEOUT = 30000;

  private final DevHelperClientTransport transport;
  private final TransportSubscription resultSubscription;
  private final ScheduledExecutorService deadlineScheduler;

  // 全局命令监听器
  private final List<Consumer<CommandResult>> GLOBAL_COMMAND_LISTENERS;

  // 命令监听器
  private final ConcurrentMap<String, List<Consumer<CommandResult>>> COMMAND_LISTENERS;

  // 普通命令结果监听器
  private final ConcurrentMap<String, List<CompletableFuture<CommandResult>>> COMMAND_RESULTS;

  // 序列命令结果监听器
  private final ConcurrentMap<String, PendingCommandResult> SEQ_COMMAND_RESULTS;

  // 创建命令发送通道
  private final ConcurrentMap<String, DisposableStreamChannel> STREAM_CHANNELS;

  @Autowired
  public DefaultDevHelperCommandService(DevHelperClientTransport transport) {
    this(transport, Executors.newSingleThreadScheduledExecutor(runnable -> {
      Thread thread = new Thread(runnable, "dev-helper-command-deadline");
      thread.setDaemon(true);
      return thread;
    }));
  }

  DefaultDevHelperCommandService(DevHelperClientTransport transport, ScheduledExecutorService deadlineScheduler) {
    this.GLOBAL_COMMAND_LISTENERS = Collections.synchronizedList(new ArrayList<>());
    this.COMMAND_LISTENERS = new ConcurrentHashMap<>();
    this.COMMAND_RESULTS = new ConcurrentHashMap<>();
    this.SEQ_COMMAND_RESULTS = new ConcurrentHashMap<>();
    this.STREAM_CHANNELS = new ConcurrentHashMap<>();
    this.deadlineScheduler = deadlineScheduler;

    this.transport = transport;
    registerBuiltInCommandListeners();
    resultSubscription = transport.subscribe(this::handleCommandResult);
    logger.info("DevHelperCommandService initialized");
  }

  // 开发平台启动时，发送一次 Knock Knock
  @EventListener(ApplicationStartedEvent.class)
  private void onApplicationStarted() {
    logger.info("Application started, sending knock knock");
    try { knockKnock(); }
    catch (RuntimeException error) {
      logger.warn("Initial helper discovery failed; the next command will reconnect", error);
    }
  }

  // 处理返回结果
  private void handleCommandResult(CommandResult result) {
    var expected = result.getSequenceId() == null ? null : SEQ_COMMAND_RESULTS.get(result.getSequenceId());
    if (expected != null && !expected.accepts(result)) {
      logger.warn("Ignored helper response from a different process: command={}, sequenceId={}", result.getCommand(), result.getSequenceId());
      return;
    }
    PendingCommandResult pending = claimSequencedResult(result);
    try {
      dispatchCommandResult(result);
    } finally {
      if (pending != null) {
        pending.complete(result);
      }
    }
  }

  private void dispatchCommandResult(CommandResult result) {
    for (Consumer<CommandResult> handler : GLOBAL_COMMAND_LISTENERS) {
      handler.accept(result);
    }
  }

  private PendingCommandResult claimSequencedResult(CommandResult result) {
    String sequenceId = result.getSequenceId();
    if (ObjectUtils.isEmpty(sequenceId)) {
      return null;
    }
    PendingCommandResult pending = SEQ_COMMAND_RESULTS.remove(sequenceId);
    if (pending != null) {
      pending.cancelDeadline();
    }
    return pending;
  }

  // 注册内置命令监听器
  private void registerBuiltInCommandListeners() {
    registerGlobalResultListener(this::builtInCommandListenersHandler);
    registerGlobalResultListener(this::builtInNoSeqCommandResultsHandler);
  }

  // 调用注册的命令监听器
  private void builtInCommandListenersHandler(CommandResult result) {
    String cmd = Optional.ofNullable(result.getCommand()).orElse("").toUpperCase();
    List<Consumer<CommandResult>> handlers = COMMAND_LISTENERS.get(cmd);
    if (ObjectUtils.isEmpty(handlers)) {
      return;
    }
    handlers.forEach(handler -> handler.accept(result));
  }

  // 返回无序列号的命令的异步结果
  private void builtInNoSeqCommandResultsHandler(CommandResult result) {
    if (ObjectUtils.isNotEmpty(result.getChannelName())) {
      return;
    }
    String cmd = Optional.ofNullable(result.getCommand()).orElse("").toUpperCase();
    List<CompletableFuture<CommandResult>> results = COMMAND_RESULTS.remove(cmd);
    if (ObjectUtils.isEmpty(results)) {
      return;
    }
    logger.debug("Completing {} futures for command: {}", results.size(), cmd);
    for (CompletableFuture<CommandResult> future : results) {
      if (future.isDone()) {
        continue;
      }
      future.complete(result);
    }
  }

  // 发送 Knock Knock
  private void knockKnock() {
    CommandRequest result = new CommandRequest()
        .setAppName("")
        .setCommand(Commands.KNOCK_KNOCK.getCommand())
        .setParams(Collections.singletonMap("source", "OuroborosDevelop"))
        .setTimeout(1000);
    sendRequest(result);
  }

  @Override
  public CompletableFuture<CommandResult> execRough(String command, Map<String, Object> params) {
    return execRough(command, params, DEFAULT_TIMEOUT);
  }


  @Override
  public CompletableFuture<CommandResult> execRough(String command, Map<String, Object> params, long timeout) {
    String appName = DevAppHolder.getAppName();
    return execRough(appName, command, params, timeout);
  }

  @Override
  public CompletableFuture<CommandResult> execRough(String appName, String command, Map<String, Object> params) {
    return execRough(appName, command, params, DEFAULT_TIMEOUT);
  }

  @Override
  public CompletableFuture<CommandResult> exec(String command, Map<String, Object> params) {
    return exec(command, params, DEFAULT_TIMEOUT);
  }

  @Override
  public CompletableFuture<CommandResult> exec(String command, Map<String, Object> params, long timeout) {
    String appName = DevAppHolder.getAppName();
    return exec(appName, command, params, timeout);
  }

  @Override
  public CompletableFuture<CommandResult> exec(String appName, String command, Map<String, Object> params) {
    return exec(appName, command, params, DEFAULT_TIMEOUT);
  }

  @Override
  public CompletableFuture<CommandResult> exec(String appName, String command, Map<String, Object> params, long timeout) {
    CommandRequest request = new CommandRequest();
    request.setAppName(appName);
    request.setCommand(command);
    request.setParams(params);
    request.setTimeout(timeout);
    request.setSequenceId(UUID.randomUUID().toString());
    return sendBoundRequest(request);
  }

  @Override
  public CompletableFuture<CommandResult> execRough(String appName, String command, Map<String, Object> params, long timeout) {
    CommandRequest request = new CommandRequest();
    request.setAppName(appName);
    request.setCommand(command);
    request.setParams(params);
    request.setTimeout(timeout);
    return sendBoundRequest(request);
  }

  private CompletableFuture<CommandResult> sendBoundRequest(CommandRequest request) {
    hydrateRefreshProtocol(request);
    CompletableFuture<CommandResult> result = bindRequestResult(request);
    try {
      sendRequest(request);
    } catch (RuntimeException sendFailure) {
      unbindRequestResult(request, result);
      throw sendFailure;
    }
    scheduleRequestDeadline(request, result);
    return result;
  }

  private CompletableFuture<CommandResult> bindRequestResult(CommandRequest request) {
    CompletableFuture<CommandResult> future = new CompletableFuture<>();
    String seqId = request.getSequenceId();
    String cmd = Optional.ofNullable(request.getCommand()).orElse("").toUpperCase();
    if (ObjectUtils.isEmpty(seqId)) {
      COMMAND_RESULTS
          .computeIfAbsent(cmd, k -> Collections.synchronizedList(new ArrayList<>()))
          .add(future);
    } else {
      SEQ_COMMAND_RESULTS.put(seqId, new PendingCommandResult(future, request));
    }
    return future;
  }

  private void unbindRequestResult(CommandRequest request, CompletableFuture<CommandResult> future) {
    String seqId = request.getSequenceId();
    if (ObjectUtils.isNotEmpty(seqId)) {
      PendingCommandResult pending = SEQ_COMMAND_RESULTS.get(seqId);
      if (pending != null && pending.matches(future) && SEQ_COMMAND_RESULTS.remove(seqId, pending)) {
        pending.cancelDeadline();
      }
      return;
    }
    String cmd = Optional.ofNullable(request.getCommand()).orElse("").toUpperCase();
    COMMAND_RESULTS.computeIfPresent(cmd, (ignored, futures) -> {
      synchronized (futures) {
        futures.remove(future);
        return futures.isEmpty() ? null : futures;
      }
    });
  }

  private void sendRequest(CommandRequest request) {
    logger.debug("Sending command request: command={}, appName={}, sequenceId={}", 
        request.getCommand(), request.getAppName(), request.getSequenceId());
    transport.send(request);
  }

  private void scheduleRequestDeadline(
      CommandRequest request,
      CompletableFuture<CommandResult> future
  ) {
    String sequenceId = request.getSequenceId();
    if (ObjectUtils.isEmpty(sequenceId)) {
      return;
    }
    PendingCommandResult pending = SEQ_COMMAND_RESULTS.get(sequenceId);
    if (pending == null || !pending.matches(future)) {
      return;
    }
    long timeout = request.getTimeout() > 0L ? request.getTimeout() : DEFAULT_TIMEOUT;
    ScheduledFuture<?> deadline = deadlineScheduler.schedule(
        () -> timeoutRequest(request, pending, timeout),
        timeout,
        TimeUnit.MILLISECONDS
    );
    pending.setDeadline(deadline);
  }

  private void timeoutRequest(CommandRequest request, PendingCommandResult pending, long timeout) {
    String sequenceId = request.getSequenceId();
    if (!SEQ_COMMAND_RESULTS.remove(sequenceId, pending)) {
      return;
    }
    pending.cancelDeadline();
    CommandResult timeoutResult = new CommandResult(request)
        .setFailureType(CommandFailureType.RESPONSE_TIMEOUT)
        .setMessage("等待命令响应超时（" + timeout + "ms）");
    logger.warn(
        "Command response timeout: command={}, appName={}, sequenceId={}",
        request.getCommand(),
        request.getAppName(),
        sequenceId
    );
    try {
      dispatchCommandResult(timeoutResult);
    } finally {
      pending.complete(timeoutResult);
    }
  }

  @PreDestroy
  void shutdownDeadlineScheduler() {
    try {
      resultSubscription.close();
    } finally {
      deadlineScheduler.shutdownNow();
    }
  }

  private void hydrateRefreshProtocol(CommandRequest request) {
    if (request == null) {
      return;
    }
    Map<String, Object> params = request.getParams();
    if (params == null) {
      return;
    }
    if (request.getProfile() == null && params.get("profile") instanceof String profile) {
      request.setProfile(profile);
    }
    if (request.getRuntimeGeneration() == null && params.containsKey("runtimeGeneration")) {
      request.setRuntimeGeneration(resolveRuntimeGeneration(params.get("runtimeGeneration")));
    }
    if (request.getHelperInstanceId() == null && params.containsKey(MetadataRefreshProtocol.HELPER_INSTANCE_ID_KEY)) {
      request.setHelperInstanceId(String.valueOf(params.get(MetadataRefreshProtocol.HELPER_INSTANCE_ID_KEY)));
    }
    if (request.getHelperStartedAtEpochMillis() == null
        && params.containsKey(MetadataRefreshProtocol.HELPER_STARTED_AT_EPOCH_MILLIS_KEY)) {
      request.setHelperStartedAtEpochMillis(resolveLong(
          params.get(MetadataRefreshProtocol.HELPER_STARTED_AT_EPOCH_MILLIS_KEY)
      ));
    }
    if (request.getAppIncarnationId() == null
        && params.containsKey(MetadataRefreshProtocol.APP_INCARNATION_ID_KEY)) {
      request.setAppIncarnationId(String.valueOf(params.get(MetadataRefreshProtocol.APP_INCARNATION_ID_KEY)));
    }
    if (request.getRefreshOperationId() == null
        && params.containsKey(MetadataRefreshProtocol.REFRESH_OPERATION_ID_KEY)) {
      request.setRefreshOperationId(String.valueOf(params.get(MetadataRefreshProtocol.REFRESH_OPERATION_ID_KEY)));
    }
  }

  private Long resolveRuntimeGeneration(Object value) {
    return resolveLong(value);
  }

  private Long resolveLong(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof Number) {
      return Long.valueOf(((Number) value).longValue());
    }
    try {
      return Long.valueOf(value.toString());
    } catch (NumberFormatException exception) {
      return null;
    }
  }

  private static final class PendingCommandResult {
    private final CompletableFuture<CommandResult> future;
    private ScheduledFuture<?> deadline;
    private boolean completed;
    private final String appName;
    private final String command;
    private final String profile;
    private final String helperInstanceId;
    private final Long helperStartedAt;
    private final String incarnation;
    private final Long generation;

    private PendingCommandResult(CompletableFuture<CommandResult> future, CommandRequest request) {
      this.future = future;
      appName = request.getAppName();
      command = request.getCommand();
      profile = request.getProfile();
      helperInstanceId = request.getHelperInstanceId();
      helperStartedAt = request.getHelperStartedAtEpochMillis();
      incarnation = request.getAppIncarnationId();
      generation = request.getRuntimeGeneration();
    }

    boolean accepts(CommandResult result) {
      return (appName == null || appName.isBlank() || appName.equals(result.getAppName()))
          && Objects.equals(command, result.getCommand())
          && (profile == null || profile.equals(result.getProfile()))
          && (helperInstanceId == null || helperInstanceId.equals(result.getHelperInstanceId()))
          && (helperStartedAt == null || helperStartedAt.equals(result.getHelperStartedAtEpochMillis()))
          && (incarnation == null || incarnation.equals(result.getAppIncarnationId()))
          && (generation == null || generation.equals(result.getRuntimeGeneration()));
    }

    synchronized void setDeadline(ScheduledFuture<?> deadline) {
      if (completed) {
        deadline.cancel(false);
        return;
      }
      this.deadline = deadline;
    }

    synchronized void cancelDeadline() {
      completed = true;
      if (deadline != null) {
        deadline.cancel(false);
      }
    }

    boolean matches(CompletableFuture<CommandResult> candidate) {
      return future == candidate;
    }

    void complete(CommandResult result) {
      future.complete(result);
    }
  }

  @Override
  public void registerGlobalResultListener(Consumer<CommandResult> listener) {
    GLOBAL_COMMAND_LISTENERS.add(listener);
  }

  @Override
  public void unregisterGlobalResultListener(Consumer<CommandResult> listener) {
    GLOBAL_COMMAND_LISTENERS.remove(listener);
  }

  @Override
  public void registerCommandResultListener(String command, Consumer<CommandResult> listener) {
    COMMAND_LISTENERS
        .computeIfAbsent(command.toUpperCase(), k -> Collections.synchronizedList(new ArrayList<>()))
        .add(listener);
  }

  @Override
  public void unregisterCommandResultListener(String command, Consumer<CommandResult> listener) {
    COMMAND_LISTENERS
        .computeIfAbsent(command.toUpperCase(), k -> Collections.synchronizedList(new ArrayList<>()))
        .remove(listener);
  }

  @Override
  public StreamChannel createStreamChannel(String channelName, Boolean strictInOrder) {
    if (channelName == null) {
      channelName = "null";
    }
    if (STREAM_CHANNELS.containsKey(channelName)) {
      logger.debug("Returning existing stream channel: {}", channelName);
      return STREAM_CHANNELS.get(channelName);
    }
    logger.info("Creating stream channel: channelName={}, strictInOrder={}", channelName, strictInOrder);
    DisposableStreamChannel channel = strictInOrder ? new SharedTopicQueueStreamChannel(channelName) : new SharedTopicStreamChannel(channelName);
    return STREAM_CHANNELS.put(channelName, channel);
  }

  @Override
  public void destroyStreamChannel(String channelName) {
    if (channelName == null) {
      channelName = "null";
    }
    DisposableStreamChannel channel = STREAM_CHANNELS.remove(channelName);
    if (channel != null) {
      logger.info("Destroying stream channel: {}", channelName);
      channel.dispose();
    }
  }

  public abstract static class DisposableStreamChannel implements StreamChannel {
    abstract public void dispose();
  }

  public class SharedTopicStreamChannel extends DisposableStreamChannel {
    private final String channelName;
    private final List<Consumer<CommandResult>> listeners;

    public SharedTopicStreamChannel(String channelName) {
      this.listeners = new CopyOnWriteArrayList<>();
      this.channelName = channelName;
      registerGlobalResultListener(this::handleResult);
    }

    @Override
    public void send(String command, Map<String, Object> params) {
      send(command, params, DEFAULT_TIMEOUT);
    }

    @Override
    public void send(String command, Map<String, Object> params, long timeout) {
      String appName = DevAppHolder.getAppName();
      send(appName, command, params, timeout);
    }

    @Override
    public void send(String appName, String command, Map<String, Object> params) {
      send(appName, command, params, DEFAULT_TIMEOUT);
    }

    @Override
    public void send(String appName, String command, Map<String, Object> params, long timeout) {
      CommandRequest request = new CommandRequest();
      request.setAppName(appName);
      request.setCommand(command);
      request.setParams(params);
      request.setTimeout(timeout);
      request.setChannelName(channelName);
      doSendRequest(request);
    }

    @Override
    public void subscribe(Consumer<CommandResult> handler) {
      listeners.add(handler);
    }

    @Override
    public void unsubscribe(Consumer<CommandResult> handler) {
      listeners.remove(handler);
    }

    protected void doSendRequest(CommandRequest request) {
      sendRequest(request);
    }

    protected void handleResult(CommandResult result) {
      if (!channelName.equals(result.getChannelName())) {
        return;
      }
      for (Consumer<CommandResult> listener : listeners) {
        listener.accept(result);
      }
    }

    @Override
    public void dispose() {
      unregisterGlobalResultListener(this::handleResult);
    }
  }

  public class SharedTopicQueueStreamChannel extends SharedTopicStreamChannel {
    private final String channelName;
    private final ConcurrentMap<String, Queue<CommandRequest>> commandRequestQueueMap;
    private final ConcurrentMap<String, CompletableFuture<CommandResult>> latestFutureMap;

    public SharedTopicQueueStreamChannel(String channelName) {
      super(channelName);
      this.channelName = channelName;
      commandRequestQueueMap = new ConcurrentHashMap<>();
      latestFutureMap = new ConcurrentHashMap<>();
    }

    @Override
    protected void doSendRequest(CommandRequest request) {
      String appName = request.getAppName();
      // TODO: 是否有必要加上命令序列号？
      // 加入到队列
      commandRequestQueueMap
          .computeIfAbsent(appName, k -> new ConcurrentLinkedQueue<>())
          .add(request);
      processQueue(appName);
    }

    // 按应用名处理队列
    private void processQueue(String appName) {
      // 检查当前应用的的Future已存在且未完成，则先跳过
      if (latestFutureMap.containsKey(appName) && !latestFutureMap.get(appName).isDone()) {
        return;
      }

      // 从队列中取出一个请求
      CommandRequest request = commandRequestQueueMap.get(appName).poll();
      if (ObjectUtils.isEmpty(request)) {
        return;
      }

      // 创建新的Future，并发送请求
      CompletableFuture<CommandResult> newFuture = new CompletableFuture<>();
      newFuture.thenAccept(super::handleResult);
      latestFutureMap.put(appName, newFuture);
      sendRequest(request);
    }

    @Override
    protected void handleResult(CommandResult result) {
      if (!channelName.equals(result.getChannelName())) {
        return;
      }

      // 如果当前Future已完成，则忽略
      CompletableFuture<CommandResult> future = latestFutureMap.remove(result.getAppName());
      if (future != null && !future.isDone()) {
        future.complete(result);
      }

      // 继续处理队列
      processQueue(result.getAppName());
    }
  }
}
