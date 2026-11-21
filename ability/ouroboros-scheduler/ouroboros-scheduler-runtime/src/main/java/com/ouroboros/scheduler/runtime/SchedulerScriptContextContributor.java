package com.ouroboros.scheduler.runtime;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import io.vavr.control.Try;

import com.ouroboros.exception.InternalException;
import com.ouroboros.scheduler.ScheduleTask;
import com.ouroboros.scheduler.Scheduler;
import com.ouroboros.script.ScriptContextContributor;

@Component
public class SchedulerScriptContextContributor {
  private static final Logger logger = LoggerFactory.getLogger(SchedulerScriptContextContributor.class);
  private final Scheduler defaultScheduler;

  public SchedulerScriptContextContributor(ObjectProvider<Scheduler> schedulerProvider) {
    var schedulerList = schedulerProvider.stream().collect(Collectors.toList());
    if (schedulerList.isEmpty()) {
      var error = new InternalException("没有计划任务调度器实例");
      logger.error("没有计划任务调度器实例", error);
      defaultScheduler = new NoScheduler(error);
    } else if (schedulerList.size() > 1) {
      logger.warn("计划任务调度器实例数量大于1，将使用第一个实例");
      defaultScheduler = schedulerList.get(0);
    } else {
      defaultScheduler = schedulerList.get(0);
    }
    SingletonHolder.INSTANCE = this;
  }

  public static class SingletonHolder implements ScriptContextContributor {
    private static SchedulerScriptContextContributor INSTANCE;

    @Override
    public Map<String, Object> getBindings() {
      return new SingletonImmutableLazyMap<String, Object>("Scheduler", () -> INSTANCE.defaultScheduler);
    }
  }

  private static class NoScheduler implements Scheduler {
    private final InternalException error;

    public NoScheduler(InternalException error) {
      this.error = error;
    }

    public NoScheduler() {
      this.error = new InternalException("没有计划任务调度器实例");
    }

    @Override
    public Try<LocalDateTime> schedule(ScheduleTask task) {
      return Try.failure(error);
    }

    @Override
    public Try<LocalDateTime> scheduleCron(String taskName, String cron, String flowName, Map<String, Object> data) {
      return Try.failure(error);
    }

    @Override
    public Try<LocalDateTime> scheduleOnce(String taskName, Date dateTime, String flowName, Map<String, Object> data) {
      return Try.failure(error);
    }

    @Override
    public Try<LocalDateTime> scheduleOnce(String taskName, LocalDateTime dateTime, String flowName, Map<String, Object> data) {
      return Try.failure(error);
    }

    @Override
    public Try<LocalDateTime> scheduleOnce(String taskName, Duration delay, String flowName, Map<String, Object> data) {
      return Try.failure(error);
    }

    @Override
    public Try<LocalDateTime> scheduleOnce(String taskName, Long millisecond, String flowName, Map<String, Object> data) {
      return Try.failure(error);
    }

    @Override
    public Try<Void> cancel(String taskName) {
      return Try.failure(new Exception("计划任务调度器未配置"));
    }

    @Override
    public boolean isScheduled(String taskName) {
      return false;
    }
  }

  private static class SingletonImmutableLazyMap<K, V> implements Map<K, V> {
    private final K key;
    private final Supplier<V> valueSupplier;

    public SingletonImmutableLazyMap(K key, Supplier<V> valueSupplier) {
      this.key = key;
      this.valueSupplier = valueSupplier;
    }

    @Override
    public int size() {
      return 1;
    }

    @Override
    public boolean isEmpty() {
      return false;
    }

    @Override
    public boolean containsKey(Object key) {
      return this.key.equals(key);
    }

    @Override
    public boolean containsValue(Object value) {
      return Objects.equals(valueSupplier.get(), value);
    }

    @Override
    public V get(Object key) {
      if (this.key.equals(key)) {
        return valueSupplier.get();
      }
      return null;
    }

    @Override
    public V put(K key, V value) {
      return null;
    }

    @Override
    public V remove(Object key) {
      return null;
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {

    }

    @Override
    public void clear() {

    }

    @Override
    public Set<K> keySet() {
      return Collections.singleton(key);
    }

    @Override
    public Collection<V> values() {
      return Collections.singleton(valueSupplier.get());
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
      return Collections.singleton(new Entry<>() {
        @Override
        public K getKey() {
          return key;
        }

        @Override
        public V getValue() {
          return valueSupplier.get();
        }

        @Override
        public V setValue(V value) {
          return null;
        }
      });
    }
  }
}
