package com.ouroboros.upload;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import io.vavr.Tuple;
import io.vavr.Tuple3;
import io.vavr.control.Try;

/**
 * 基于Timer的清理调度器，默认每小时清理一次
 */
public class TimerCleanUpScheduler implements CleanUpScheduler, AutoCloseable {
  private volatile boolean closed;
  Timer timer;
  List<Tuple3<String, LocalDateTime, ? extends Set<Runnable>>> taskList = Collections.synchronizedList(new ArrayList<>());

  public TimerCleanUpScheduler() {
    this(Duration.ofHours(1));
  }

  public TimerCleanUpScheduler(Duration cleanDuration) {
    this.timer = new Timer("upload-cleanup", true);
    this.timer.scheduleAtFixedRate(new TimerTask() {
      @Override
      public void run() {
        trigger();
      }
    }, cleanDuration.toMillis(), cleanDuration.toMillis());
  }

  @Override
  public void trigger() {
    syncRun(() -> {
      LocalDateTime now = LocalDateTime.now();
      Set<Runnable> tasks = taskList.stream()
          .filter(tuple -> tuple._2.isBefore(now))
          .map(Tuple3::_3)
          .flatMap(Set::stream)
          .collect(Collectors.toSet());
      taskList.removeIf(tuple -> tuple._2.isBefore(now));
      asyncRunTasks(tasks);
    });
  }

  @Override
  public Boolean addCleanUpTask(String taskKey, Runnable task, Duration cleanDuration) {
    return syncRun(() -> {
      if (taskList.stream().noneMatch(tuple -> tuple._1.equals(taskKey))) {
        Tuple3<String, LocalDateTime, Set<Runnable>> tuple = Tuple.of(
            taskKey,
            LocalDateTime.now().plus(cleanDuration),
            Collections.singleton(task)
        );
        taskList.add(tuple);
        return true;
      }
      return false;
    });
  }

  @Override
  public Boolean addCleanUpTask(String taskKey, Collection<Runnable> tasks, Duration cleanDuration) {
    return syncRun(() -> {
      if (taskList.stream().noneMatch(tuple -> tuple._1.equals(taskKey))) {
        Tuple3<String, LocalDateTime, Set<Runnable>> tuple = Tuple.of(
            taskKey,
            LocalDateTime.now().plus(cleanDuration),
            new HashSet<>(tasks)
        );
        taskList.add(tuple);
        return true;
      }
      return false;
    });
  }

  @Override
  public Boolean joinCleanUpTask(String taskKey, Runnable task, Duration cleanDuration) {
    return syncRun(() -> {
      LocalDateTime expiredTime = LocalDateTime.now().plus(cleanDuration);
      AtomicReference<Boolean> result = new AtomicReference<>(false);
      this.taskList = taskList.stream()
          .map(tuple -> {
            if (tuple._1.equals(taskKey)) {
              result.set(true);
              return tuple.map2(__ -> expiredTime)
                  .map3(set -> {
                    Set<Runnable> newSet = new HashSet<>(set);
                    newSet.add(task);
                    return newSet;
                  });
            }
            return tuple;
          })
          .collect(Collectors.toList());
      return result.get();
    });
  }

  @Override
  public Boolean joinCleanUpTask(String taskKey, Runnable task) {
    return syncRun(() -> {
      AtomicReference<Boolean> result = new AtomicReference<>(false);
      this.taskList = taskList.stream()
          .map(tuple -> {
            if (tuple._1.equals(taskKey)) {
              result.set(true);
              return tuple.map3(set -> {
                Set<Runnable> newSet = new HashSet<>(set);
                newSet.add(task);
                return newSet;
              });
            }
            return tuple;
          })
          .collect(Collectors.toList());
      return result.get();
    });
  }

  @Override
  public Boolean updateCleanUpTask(String taskKey, Duration cleanDuration) {
    return syncRun(() -> {
      LocalDateTime expiredTime = LocalDateTime.now().plus(cleanDuration);
      AtomicReference<Boolean> result = new AtomicReference<>(false);
      this.taskList = taskList.stream()
          .map(tuple -> {
            if (tuple._1.equals(taskKey)) {
              result.set(true);
              return tuple.map2(__ -> expiredTime);
            }
            return tuple;
          })
          .collect(Collectors.toList());
      return result.get();
    });
  }

  @Override
  public void runCleanUpTask(String taskKey) {
    syncRun(() -> {
      Set<Runnable> tasks = taskList.stream()
          .filter(tuple -> tuple._1.equals(taskKey))
          .map(Tuple3::_3)
          .flatMap(Set::stream)
          .collect(Collectors.toSet());
      taskList.removeIf(tuple -> tuple._1.equals(taskKey));
      asyncRunTasks(tasks);
    });
  }

  @Override
  public void runAllCleanUpTask() {
    syncRun(() -> {
      Set<Runnable> tasks = taskList.stream()
          .map(Tuple3::_3)
          .flatMap(Set::stream)
          .collect(Collectors.toSet());
      taskList.clear();
      asyncRunTasks(tasks);
    });
  }

  @Override
  public void removeCleanUpTask(String taskKey) {
    syncRun(() -> taskList.removeIf(tuple -> tuple._1.equals(taskKey)));
  }

  @Override
  public void removeAllCleanUpTask() {
    syncRun(taskList::clear);
  }

  void syncRun(Runnable task) {
    synchronized (this) {
      task.run();
    }
  }

  <T> T syncRun(Supplier<T> task) {
    synchronized (this) {
      return task.get();
    }
  }

  @Override public synchronized void close() { closed = true; timer.cancel(); taskList.clear(); }

  void asyncRunTasks(Set<Runnable> tasks) {
    CompletableFuture.runAsync(() -> {
      synchronized (this) {
        if (closed) { return; }
        for (var task : tasks) {
          try { task.run(); }
          catch (RuntimeException e) { java.util.logging.Logger.getLogger(TimerCleanUpScheduler.class.getName()).log(java.util.logging.Level.WARNING, "Upload cleanup task failed", e); }
        }
      }
    });
  }
}
