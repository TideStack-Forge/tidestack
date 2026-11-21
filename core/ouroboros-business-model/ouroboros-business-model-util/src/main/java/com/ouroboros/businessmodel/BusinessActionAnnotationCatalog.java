package com.ouroboros.businessmodel;

import com.ouroboros.common.app.util.SpringContextUtil;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

import com.ouroboros.businessmodel.action.BusinessActionContext;
import com.ouroboros.businessmodel.action.BusinessActionHandler;
import com.ouroboros.businessmodel.action.BusinessActionInput;
import com.ouroboros.businessmodel.action.PreparedBusinessActionInput;
import com.ouroboros.businessmodel.annotation.BusinessAction;
import com.ouroboros.businessmodel.annotation.BusinessActionBinding;
import com.ouroboros.businessmodel.behavior.ActionBinding;
import com.ouroboros.businessmodel.behavior.ActionDefinition;
import com.ouroboros.businessmodel.behavior.BusinessActionBindingContribution;
import com.ouroboros.businessmodel.behavior.BusinessModelBehaviorContribution;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.util.Json;

/** Collects annotated Spring methods after application beans are ready. */
@Component
public class BusinessActionAnnotationCatalog implements SmartInitializingSingleton {
  private volatile Snapshot snapshot = Snapshot.empty();

  private final ConfigurableListableBeanFactory beanFactory;

  public BusinessActionAnnotationCatalog(ConfigurableListableBeanFactory beanFactory) {
    this.beanFactory = beanFactory;
  }

  @Override
  public void afterSingletonsInstantiated() {
    List<BusinessModelBehaviorContribution> definitions = new ArrayList<>();
    List<BusinessActionBindingContribution> bindings = new ArrayList<>();
    List<BusinessActionHandler> handlers = new ArrayList<>();
    for (String beanName : beanFactory.getBeanDefinitionNames()) {
      if (!beanFactory.containsSingleton(beanName)) {
        continue;
      }
      Object bean = beanFactory.getBean(beanName);
      Class<?> targetClass = AopUtils.getTargetClass(bean);
      for (Method method : targetClass.getMethods()) {
        BusinessAction action = method.getAnnotation(BusinessAction.class);
        BusinessActionBinding binding = method.getAnnotation(BusinessActionBinding.class);
        if (action == null && binding == null) {
          continue;
        }
        if (action != null && binding != null) {
          throw new IllegalStateException("A method cannot declare both @BusinessAction and @BusinessActionBinding: "
              + targetClass.getName() + "#" + method.getName());
        }
        String handlerName = action == null ? binding.handlerName() : action.handlerName();
        boolean logicFlowBinding = action == null && !binding.flowName().isBlank();
        if (action == null && logicFlowBinding == !handlerName.isBlank()) {
          throw new IllegalStateException("@BusinessActionBinding must declare exactly one of handlerName or flowName: "
              + targetClass.getName() + "#" + method.getName());
        }
        if (action != null && handlerName.isBlank()) {
          throw new IllegalStateException("@BusinessAction handlerName must not be blank: "
              + targetClass.getName() + "#" + method.getName());
        }
        String source = targetClass.getName() + "#" + method.getName();
        if (action != null) {
          validateHandlerMethod(bean, method, targetClass);
          Method invocableMethod = AopUtils.selectInvocableMethod(method, bean.getClass());
          handlers.add(new AnnotatedHandler(handlerName, bean, method, invocableMethod));
          definitions.add(new BusinessModelBehaviorContribution(action.modelFullName(), source,
              List.of(actionDefinition(action)), List.of(), List.of(), "2.0"));
        } else if (logicFlowBinding) {
          bindings.add(new BusinessActionBindingContribution(binding.modelFullName(), binding.actionName(),
              source, logicFlowBinding(binding.flowName()), "2.0"));
        } else {
          validateHandlerMethod(bean, method, targetClass);
          Method invocableMethod = AopUtils.selectInvocableMethod(method, bean.getClass());
          handlers.add(new AnnotatedHandler(handlerName, bean, method, invocableMethod));
          bindings.add(new BusinessActionBindingContribution(binding.modelFullName(), binding.actionName(),
              source, handlerBinding(handlerName), "2.0"));
        }
      }
    }
    snapshot = new Snapshot(definitions, bindings, handlers);
  }

  public static List<BusinessModelBehaviorContribution> getDefinitionContributions() {
    return currentSnapshot().definitions();
  }

  public static List<BusinessActionBindingContribution> getBindingContributions() {
    return currentSnapshot().bindings();
  }

  public List<BusinessActionHandler> getHandlers() {
    return snapshot.handlers();
  }

  private static ActionDefinition actionDefinition(BusinessAction annotation) {
    ActionDefinition action = new ActionDefinition();
    action.setName(annotation.name());
    action.setTitle(annotation.title());
    action.setDescription(annotation.description());
    action.setIsEnabled(annotation.isEnabled());
    action.setInputSchema(Json.tryToMap(annotation.inputSchema()).getOrElseThrow(error ->
        new IllegalStateException("Invalid @BusinessAction inputSchema", error)));
    action.setOutputSchema(Json.tryToMap(annotation.outputSchema()).getOrElseThrow(error ->
        new IllegalStateException("Invalid @BusinessAction outputSchema", error)));
    action.setReadOnly(annotation.readOnly());
    if (!annotation.precondition().isBlank()) {
      action.setPrecondition(Json.tryToMap(annotation.precondition()).getOrElseThrow(error ->
          new IllegalStateException("Invalid @BusinessAction precondition", error)));
    }
    action.setEmits(Json.tryToBean(new com.fasterxml.jackson.core.type.TypeReference<
        List<com.ouroboros.businessmodel.behavior.EventReference>>() { }, annotation.emits()).getOrElseThrow(error ->
            new IllegalStateException("Invalid @BusinessAction emits", error)));
    action.setBinding(handlerBinding(annotation.handlerName()));
    return action;
  }

  private static ActionBinding handlerBinding(String handlerName) {
    ActionBinding binding = new ActionBinding();
    binding.setKind("handler");
    binding.setHandlerName(handlerName);
    return binding;
  }

  private static ActionBinding logicFlowBinding(String flowName) {
    ActionBinding binding = new ActionBinding();
    binding.setKind("logicflow");
    binding.setFlowName(flowName);
    return binding;
  }

  private static void validateHandlerMethod(Object bean, Method method, Class<?> targetClass) {
    if (!BusinessBindingCapabilities.isSynchronous(bean, method)
        || java.util.concurrent.Future.class.isAssignableFrom(method.getReturnType())
        || java.util.concurrent.CompletionStage.class.isAssignableFrom(method.getReturnType())) {
      throw new IllegalStateException("Annotated Action handler must complete synchronously: "
          + targetClass.getName() + "#" + method.getName());
    }
  }

  public static void validateBinding(ActionDefinition action) {
    if (action.getBinding() != null && "handler".equalsIgnoreCase(action.getBinding().getKind())) {
      currentSnapshot().handlers().stream().filter(handler -> handler.getName().equalsIgnoreCase(action.getBinding().getHandlerName()))
          .forEach(handler -> handler.validateContract(action));
    }
  }

  private static Snapshot currentSnapshot() {
    var context = SpringContextUtil.getApplicationContext();
    if (context == null) {
      return Snapshot.empty();
    }
    var catalog = context.getBeanProvider(BusinessActionAnnotationCatalog.class).getIfAvailable();
    return catalog == null ? Snapshot.empty() : catalog.snapshot;
  }

  private record Snapshot(List<BusinessModelBehaviorContribution> definitions,
                          List<BusinessActionBindingContribution> bindings,
                          List<BusinessActionHandler> handlers) {
    private Snapshot {
      definitions = List.copyOf(definitions);
      bindings = List.copyOf(bindings);
      handlers = List.copyOf(handlers);
    }

    private static Snapshot empty() {
      return new Snapshot(Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
    }
  }

  private static final class AnnotatedHandler implements BusinessActionHandler {
    private final String name;
    private final Object bean;
    private final Method declaration;
    private final Method method;
    private final BusinessActionMethodBinding parameterBinding;

    private AnnotatedHandler(String name, Object bean, Method declaration, Method method) {
      this.name = name;
      this.bean = bean;
      this.declaration = declaration;
      this.method = method;
      this.parameterBinding = new BusinessActionMethodBinding(declaration);
    }

    @Override
    public String getName() {
      return name;
    }

    @Override
    public String getImplementationRef() {
      return declaration.getDeclaringClass().getName() + "#" + declaration.getName();
    }

    @Override
    public String getImplementationVersion() {
      return declaration.getDeclaringClass().getPackage().getImplementationVersion();
    }

    @Override
    public void validateContract(ActionDefinition action) {
      parameterBinding.validate(action);
    }

    @Override
    public Object handle(BusinessActionContext context, BusinessActionInput<?, ?> input) {
      if (!(input instanceof PreparedBusinessActionInput prepared)) {
        throw new BusinessException("业务 Action 输入尚未准备");
      }
      try {
        return method.invoke(bean, parameterBinding.bind(context, input, prepared.schema()));
      } catch (IllegalAccessException error) {
        throw new BusinessException("业务 Action 处理器不可访问", error);
      } catch (InvocationTargetException error) {
        Throwable cause = error.getCause();
        if (cause instanceof RuntimeException runtimeException) {
          throw runtimeException;
        }
        throw new BusinessException("业务 Action 处理器执行失败", cause);
      }
    }
  }
}
