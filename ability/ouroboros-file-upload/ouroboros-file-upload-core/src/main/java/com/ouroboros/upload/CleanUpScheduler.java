package com.ouroboros.upload;

import java.time.Duration;
import java.util.Collection;

/**
 * 清理调度器，用于清理临时文件、过期的分片上传碎片等
 */
@SuppressWarnings({"unused", "UnusedReturnValue"})
public interface CleanUpScheduler {
  /**
   * 手动触发清理动作
   */
  void trigger();

  /**
   * 添加清理任务
   *
   * @param taskKey       任务key
   * @param task          清理任务
   * @param cleanDuration 清理时间
   * @return 是否添加成功
   */
  Boolean addCleanUpTask(String taskKey, Runnable task, Duration cleanDuration);

  /**
   * 添加清理任务
   *
   * @param taskKey       任务key
   * @param tasks         清理任务列表
   * @param cleanDuration 清理时间
   * @return 是否添加成功
   */
  Boolean addCleanUpTask(String taskKey, Collection<Runnable> tasks, Duration cleanDuration);

  /**
   * 追加清理任务
   *
   * @param taskKey       任务key
   * @param task          清理任务
   * @param cleanDuration 新的清理时间
   * @return 是否追加成功
   */
  Boolean joinCleanUpTask(String taskKey, Runnable task, Duration cleanDuration);

  /**
   * 追加清理任务
   *
   * @param taskKey 任务key
   * @param task    清理任务
   * @return 是否追加成功
   */
  Boolean joinCleanUpTask(String taskKey, Runnable task);

  /**
   * 更新清理任务
   *
   * @param taskKey       任务key
   * @param cleanDuration 新的清理时间
   * @return 是否更新成功
   */
  Boolean updateCleanUpTask(String taskKey, Duration cleanDuration);

  /**
   * 执行指定清理任务
   *
   * @param taskKey 任务key
   */
  void runCleanUpTask(String taskKey);

  /**
   * 执行所有清理任务
   */
  void runAllCleanUpTask();

  /**
   * 移除指定清理任务
   *
   * @param taskKey 任务key
   */
  void removeCleanUpTask(String taskKey);

  /**
   * 移除所有清理任务
   */
  void removeAllCleanUpTask();
}
