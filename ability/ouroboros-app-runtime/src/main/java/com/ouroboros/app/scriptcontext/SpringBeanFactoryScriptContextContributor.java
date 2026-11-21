package com.ouroboros.app.scriptcontext;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.ResolvableType;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import io.vavr.control.Try;

import com.ouroboros.script.ScriptContextContributor;

@Component
public class SpringBeanFactoryScriptContextContributor {
  private final static Logger logger = LoggerFactory.getLogger(SpringBeanFactoryScriptContextContributor.class);
  BeanFactory beanFactory;
  private final Map<String, Object> bindings = new HashMap<>();

  public SpringBeanFactoryScriptContextContributor(BeanFactory beanFactory) {
    this.beanFactory = beanFactory;
    bindings.put("BeanFactory", new SpringBeanCenter() {
      @Override
      public Object get(String beanName) {
        return Try.of(() -> beanFactory.getBean(beanName))
            .onFailure(e -> logger.error("获取 Spring Bean 失败", e))
            .getOrElse(() -> null);
      }

      @NotNull
      @Override
      public Object getBean(@NotNull String name) throws BeansException {
        return beanFactory.getBean(name);
      }

      @NotNull
      @Override
      public <T> T getBean(@NotNull String name, @NotNull Class<T> requiredType) throws BeansException {
        return beanFactory.getBean(name, requiredType);
      }

      @NotNull
      @Override
      public Object getBean(@NotNull String name, @NotNull Object... args) throws BeansException {
        return beanFactory.getBean(name, args);
      }

      @NotNull
      @Override
      public <T> T getBean(@NotNull Class<T> requiredType) throws BeansException {
        return beanFactory.getBean(requiredType);
      }

      @NotNull
      @Override
      public <T> T getBean(@NotNull Class<T> requiredType, @NotNull Object... args) throws BeansException {
        return beanFactory.getBean(requiredType, args);
      }

      @NotNull
      @Override
      public <T> ObjectProvider<T> getBeanProvider(@NotNull Class<T> requiredType) {
        return beanFactory.getBeanProvider(requiredType);
      }

      @NotNull
      @Override
      public <T> ObjectProvider<T> getBeanProvider(@NotNull ResolvableType requiredType) {
        return beanFactory.getBeanProvider(requiredType);
      }

      @Override
      public boolean containsBean(@NotNull String name) {
        return beanFactory.containsBean(name);
      }

      @Override
      public boolean isSingleton(@NotNull String name) throws NoSuchBeanDefinitionException {
        return beanFactory.isSingleton(name);
      }

      @Override
      public boolean isPrototype(@NotNull String name) throws NoSuchBeanDefinitionException {
        return beanFactory.isPrototype(name);
      }

      @Override
      public boolean isTypeMatch(@NotNull String name, @NotNull ResolvableType typeToMatch) throws NoSuchBeanDefinitionException {
        return beanFactory.isTypeMatch(name, typeToMatch);
      }

      @Override
      public boolean isTypeMatch(@NotNull String name, @NotNull Class<?> typeToMatch) throws NoSuchBeanDefinitionException {
        return beanFactory.isTypeMatch(name, typeToMatch);
      }

      @Nullable
      @Override
      public Class<?> getType(@NotNull String name) throws NoSuchBeanDefinitionException {
        return beanFactory.getType(name);
      }

      @Nullable
      @Override
      public Class<?> getType(@NotNull String name, boolean allowFactoryBeanInit) throws NoSuchBeanDefinitionException {
        return beanFactory.getType(name, allowFactoryBeanInit);
      }

      @Override
      @NonNull
      public String[] getAliases(@NotNull String name) {
        return beanFactory.getAliases(name);
      }
    });
  }

  public interface SpringBeanCenter extends BeanFactory {
    Object get(String beanName);
  }

  public static class SingletonHolder implements ScriptContextContributor {
    @Override
    public Map<String, Object> getBindings() {
      return Collections.unmodifiableMap(com.ouroboros.common.app.util.SpringContextUtil
          .getBean(SpringBeanFactoryScriptContextContributor.class).bindings);
    }
  }
}
