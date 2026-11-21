package com.ouroboros.dev;

import java.util.function.Function;
import java.util.function.Supplier;

import org.apache.commons.lang3.StringUtils;

/**
 * @author liansz
 */
public class DevAppHolder {

  private static final ThreadLocal<String> APPID_THREAD_LOCAL = new ThreadLocal<>();

  public static String getAppName() {
    return APPID_THREAD_LOCAL.get();
  }

  public static void setAppName(String appName) {
    APPID_THREAD_LOCAL.set(appName);
  }

  public static void removeAppName() {
    APPID_THREAD_LOCAL.remove();
  }

  public static <T> Function<Supplier<T>, T> withAppName(String appName) {
    return supplier -> {
      String oldAppName = getAppName();
      setAppName(appName);
      try {
        return supplier.get();
      } finally {
        removeAppName();
        if (StringUtils.isNotBlank(oldAppName)) {
          setAppName(oldAppName);
        }
      }
    };
  }

  public static <T> T runWithAppName(String appName, Supplier<T> supplier) {
    Function<Supplier<T>, T> fun = withAppName(appName);
    return fun.apply(supplier);
  }
}
