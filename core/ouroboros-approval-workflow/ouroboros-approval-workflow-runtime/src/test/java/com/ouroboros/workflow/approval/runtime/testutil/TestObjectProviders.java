package com.ouroboros.workflow.approval.runtime.testutil;

import java.util.stream.Stream;

import org.springframework.beans.factory.ObjectProvider;

/**
 * 测试脚手架：{@link ObjectProvider} 匿名 5 方法实现去重工具。
 *
 * <p>测试中常需向被测对象注入一个仅用于占位/单例承载的 {@code ObjectProvider}，
 * 此处统一提供两种语义，避免各测试文件重复内联匿名类。
 */
public final class TestObjectProviders {

  private TestObjectProviders() {
  }

  /** 空提供器：getObject/getIfAvailable/getIfUnique 返回 {@code null}，stream 返回空流。 */
  public static <T> ObjectProvider<T> empty() {
    return new ObjectProvider<T>() {
      @Override
      public T getObject() {
        return null;
      }

      @Override
      public T getObject(Object... args) {
        return null;
      }

      @Override
      public T getIfAvailable() {
        return null;
      }

      @Override
      public T getIfUnique() {
        return null;
      }

      @Override
      public Stream<T> stream() {
        return Stream.empty();
      }
    };
  }

  /** 单例提供器：全部取值方法返回 {@code instance}，stream 返回含该实例的流。 */
  public static <T> ObjectProvider<T> of(T instance) {
    return new ObjectProvider<T>() {
      @Override
      public T getObject() {
        return instance;
      }

      @Override
      public T getObject(Object... args) {
        return instance;
      }

      @Override
      public T getIfAvailable() {
        return instance;
      }

      @Override
      public T getIfUnique() {
        return instance;
      }

      @Override
      public Stream<T> stream() {
        return Stream.of(instance);
      }
    };
  }
}
