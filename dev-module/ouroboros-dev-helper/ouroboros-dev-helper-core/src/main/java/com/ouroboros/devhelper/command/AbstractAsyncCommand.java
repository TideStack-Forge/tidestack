package com.ouroboros.devhelper.command;

import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;

import org.apache.commons.lang3.StringUtils;

public abstract class AbstractAsyncCommand implements Command {
  private final String COMMAND;
  ConcurrentMap<String, CompletableFuture<CommandResult>> commandResultMap = new ConcurrentHashMap<>();
  Queue<CompletableFuture<CommandResult>> commandResponseList = new ConcurrentLinkedQueue<>();

  public AbstractAsyncCommand(String command) {
    COMMAND = command;
  }

  @Override
  public String getCommand() {
    return COMMAND;
  }

  @Override
  public CompletableFuture<CommandResult> execute(CommandRequest request) {
    CompletableFuture<CommandResult> future = new CompletableFuture<>();
    if (StringUtils.isEmpty(request.getSequenceId())) {
      commandResponseList.add(future);
    } else {
      commandResultMap.put(request.getSequenceId(), future);
    }
    try {
      doExecute(request);
    } catch (RuntimeException exception) {
      failure(request, exception.getMessage() == null ? "命令执行失败" : exception.getMessage());
    }
    return future;
  }

  protected abstract void doExecute(CommandRequest request);

  protected final void success(CommandRequest request, Object data) {
    CommandResult result = new CommandResult(request, data);
    complete(request, result);
  }

  protected final void failure(CommandRequest request, String message) {
    CommandResult result = new CommandResult(request);
    result.setSuccess(false);
    result.setMessage(message);
    complete(request, result);
  }

  protected final void complete(CommandRequest request, CommandResult result) {

    // 如果有sequenceId，则返回对应的future
    if (!StringUtils.isEmpty(request.getSequenceId())) {
      CompletableFuture<CommandResult> future = commandResultMap.remove(request.getSequenceId());
      if (future != null && !future.isDone()) {
        future.complete(result);
      }
    }

    // 否则把所有的命令都返回
    while (!commandResponseList.isEmpty()) {
      CompletableFuture<CommandResult> responseFuture = commandResponseList.poll();
      if (responseFuture.isDone()) {
        continue;
      }
      responseFuture.complete(result);
    }
  }
}
