package com.ouroboros.devhelper.dev;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import com.ouroboros.devhelper.command.CommandResult;

/**
 * @author liansz
 **/
public interface DevHelperCommandService {

  /**
   * 向当前应用的开发助手发送命令
   *
   * @param command 命令名
   * @return 异步返回结果
   */
  default CompletableFuture<CommandResult> execRough(String command) {
    return execRough(command, Collections.emptyMap());
  }

  /**
   * 向当前应用的开发助手发送命令
   *
   * @param command 命令名
   * @param params  命令参数
   * @return 异步返回结果
   */
  CompletableFuture<CommandResult> execRough(String command, Map<String, Object> params);


  /**
   * 向当前应用的开发助手发送命令
   *
   * @param command 命令名
   * @param params  命令参数
   * @param timeout 超时时间
   * @return 异步返回结果
   */
  CompletableFuture<CommandResult> execRough(String command, Map<String, Object> params, long timeout);

  /**
   * 向指定应用的开发助手发送命令
   *
   * @param appName 应用名
   * @param command 命令名
   * @return 异步返回结果
   */
  default CompletableFuture<CommandResult> execRough(String appName, String command) {
    return execRough(appName, command, Collections.emptyMap());
  }


  /**
   * 向指定应用的开发助手发送命令
   *
   * @param appName 应用名
   * @param command 命令名
   * @param params  命令参数
   * @return 异步返回结果
   */
  CompletableFuture<CommandResult> execRough(String appName, String command, Map<String, Object> params);

  /**
   * 向指定应用的开发助手发送命令
   *
   * @param appName 应用名
   * @param command 命令名
   * @param params  命令参数
   * @param timeout 超时时间
   * @return 异步返回结果
   */
  CompletableFuture<CommandResult> execRough(String appName, String command, Map<String, Object> params, long timeout);


  /**
   * 命令与响应严格对应，会在命令中夹带命令序列号
   *
   * @param command 命令名
   * @return 异步返回结果
   */
  default CompletableFuture<CommandResult> exec(String command) {
    return exec(command, Collections.emptyMap());
  }

  /**
   * 命令与响应严格对应，会在命令中夹带命令序列号
   *
   * @param command 命令名
   * @param params  命令参数
   * @return 异步返回结果
   */
  CompletableFuture<CommandResult> exec(String command, Map<String, Object> params);

  /**
   * 命令与响应严格对应，会在命令中夹带命令序列号
   *
   * @param command 命令名
   * @param params  命令参数
   * @return 异步返回结果
   */
  CompletableFuture<CommandResult> exec(String command, Map<String, Object> params, long timeout);


  /**
   * 命令与响应严格对应，会在命令中夹带命令序列号
   *
   * @param command 命令名
   * @return 异步返回结果
   */
  default CompletableFuture<CommandResult> exec(String appName, String command) {
    return exec(appName, command, Collections.emptyMap());
  }

  /**
   * 命令与响应严格对应，会在命令中夹带命令序列号
   *
   * @param appName 应用名
   * @param command 命令名
   * @param params  命令参数
   * @return 异步返回结果
   */
  CompletableFuture<CommandResult> exec(String appName, String command, Map<String, Object> params);


  /**
   * 命令与响应严格对应，会在命令中夹带命令序列号
   *
   * @param appName 应用名
   * @param command 命令名
   * @param params  命令参数
   * @param timeout 超时时间
   * @return 异步返回结果
   */
  CompletableFuture<CommandResult> exec(String appName, String command, Map<String, Object> params, long timeout);

  void registerGlobalResultListener(Consumer<CommandResult> listener);

  void unregisterGlobalResultListener(Consumer<CommandResult> listener);

  void registerCommandResultListener(String command, Consumer<CommandResult> listener);

  void unregisterCommandResultListener(String command, Consumer<CommandResult> listener);

  /**
   * 创建一个流式通道
   *
   * @param channelName   通道名
   * @param strictInOrder 是否严格顺序
   * @return 创建的流式通道
   */
  StreamChannel createStreamChannel(String channelName, Boolean strictInOrder);

  /**
   * 销毁一个流式通道
   *
   * @param channelName 通道名
   */
  void destroyStreamChannel(String channelName);

  interface StreamChannel {
    void send(String command, Map<String, Object> params);

    void send(String command, Map<String, Object> params, long timeout);

    void send(String appName, String command, Map<String, Object> params);

    void send(String appName, String command, Map<String, Object> params, long timeout);

    void subscribe(Consumer<CommandResult> handler);

    void unsubscribe(Consumer<CommandResult> handler);
  }
}
