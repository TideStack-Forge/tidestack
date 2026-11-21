package com.ouroboros.logicflow.support;

import java.util.Comparator;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import jakarta.annotation.Priority;

public final class LogicFlowServices {
  private static final ConcurrentMap<Class<?>, List<Object>> CACHED_SERVICES = new ConcurrentHashMap<>();

  private LogicFlowServices() {
  }

  public static <T> Stream<T> serviceStream(Class<T> serviceClass) {
    return StreamSupport.stream(ServiceLoader.load(serviceClass).spliterator(), false);
  }

  public static <T> Stream<T> sortedServiceStream(Class<T> serviceClass) {
    return serviceStream(serviceClass).sorted(LogicFlowServices::sortByPriority);
  }

  public static <T> Stream<T> reversedServiceStream(Class<T> serviceClass) {
    return serviceStream(serviceClass).sorted(LogicFlowServices.<T>priorityComparator().reversed());
  }

  public static <T> Stream<T> cachedSortedServiceStream(Class<T> serviceClass) {
    return cachedServices(serviceClass).stream().map(serviceClass::cast);
  }

  public static <T> Stream<T> cachedReversedServiceStream(Class<T> serviceClass) {
    return cachedServices(serviceClass).stream()
        .map(serviceClass::cast)
        .sorted(LogicFlowServices.<T>priorityComparator().reversed());
  }

  private static List<Object> cachedServices(Class<?> serviceClass) {
    return CACHED_SERVICES.computeIfAbsent(serviceClass, key -> sortedServiceStream(key).map(Object.class::cast).toList());
  }

  private static <T> Comparator<T> priorityComparator() {
    return LogicFlowServices::sortByPriority;
  }

  private static <T> int sortByPriority(T left, T right) {
    return priorityOf(left) - priorityOf(right);
  }

  private static int priorityOf(Object service) {
    if (service == null) {
      return 0;
    }
    var priority = service.getClass().getAnnotation(Priority.class);
    return priority == null ? 0 : priority.value();
  }
}
