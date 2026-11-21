package com.ouroboros.businessmodel.behavior;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.ouroboros.businessmodel.action.BusinessActionRuntime;
import com.ouroboros.businessmodel.publication.BusinessModelPublication;
import com.ouroboros.businessmodel.security.BusinessBehaviorSecurity;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.web.WebApiResult;

/** Queries only the current published contract under model-management authorization. */
@RestController
@ConditionalOnBean(BusinessActionRuntime.class)
public class BusinessBehaviorCatalogController {
  private final BusinessModelBehaviorProvider provider;

  public BusinessBehaviorCatalogController(BusinessModelBehaviorProvider provider) { this.provider = provider; }

  public record Consumer(String modelFullName, EventSubscriptionDefinition subscription, List<BehaviorMemberSource> sources) { }
  public record ExternalEvent(String modelFullName, String eventName, String status, EventDefinition definition,
                              List<BehaviorMemberSource> sources) { }
  public record CatalogResponse(String view, String consumerCoverage, BusinessBehaviorCatalog catalog,
                                List<Consumer> consumers, List<ExternalEvent> externalEvents) { }

  @GetMapping("/api/business-model-behaviors/{modelFullName}/catalog")
  public WebApiResult<CatalogResponse> inspect(@PathVariable("modelFullName") String modelFullName) {
    if (!canInspect(modelFullName)) { throw new BusinessException("无权查看该业务模型的建模契约"); }
    return BusinessModelPublication.withPublishedContext(() -> {
      var behavior = provider.getBusinessModelBehavior(modelFullName)
          .orElseThrow(() -> new BusinessException("业务模型尚未发布可用行为契约"));
      return WebApiResult.ok(new CatalogResponse("published", "authorized-models-in-current-application",
          BusinessBehaviorCatalog.from(behavior), consumers(behavior), externalEvents(behavior)));
    });
  }

  private List<Consumer> consumers(BusinessModelBehavior producer) {
    var result = new ArrayList<Consumer>();
    var models = new TreeMap<String, BusinessModelBehavior>(String.CASE_INSENSITIVE_ORDER);
    models.putAll(provider.getBusinessModelBehaviorMap());
    models.forEach((name, behavior) -> {
      if (!canInspect(name)) { return; }
      behavior.getEventSubscriptionDefinitions().stream().filter(subscription -> subscription != null
          && producer.getModelFullName().equalsIgnoreCase(subscription.getSourceEvent().getModelFullName())
          && subscription.getSourceEvent().getEventName() != null && producer.getEvents().containsKey(subscription.getSourceEvent().getEventName()))
          .forEach(subscription -> result.add(new Consumer(name, subscription,
              sources(behavior, "subscription", subscription.getName()))));
    });
    return List.copyOf(result);
  }

  private List<ExternalEvent> externalEvents(BusinessModelBehavior behavior) {
    var result = new TreeMap<String, ExternalEvent>();
    behavior.getActionDefinitions().stream().filter(action -> action != null).forEach(action -> action.getEmits().stream()
        .filter(reference -> reference != null && reference.getModelFullName() != null && reference.getEventName() != null
            && !behavior.getModelFullName().equalsIgnoreCase(reference.getModelFullName()))
        .forEach(reference -> result.computeIfAbsent(reference.getModelFullName().toLowerCase(Locale.ROOT) + ":"
            + reference.getEventName().toLowerCase(Locale.ROOT), ignored -> externalEvent(reference))));
    return List.copyOf(result.values());
  }

  private ExternalEvent externalEvent(EventReference reference) {
    String model = reference.getModelFullName();
    String name = reference.getEventName();
    if (!canInspect(model)) { return new ExternalEvent(model, name, "forbidden", null, List.of()); }
    return provider.getBusinessModelBehavior(model).map(behavior -> {
      var event = behavior.getEvents().get(name);
      return event == null ? new ExternalEvent(model, name, "unavailable", null, List.of())
          : new ExternalEvent(model, name, "available", event, sources(behavior, "event", name));
    }).orElseGet(() -> new ExternalEvent(model, name, "unavailable", null, List.of()));
  }

  private static List<BehaviorMemberSource> sources(BusinessModelBehavior behavior, String type, String name) {
    return behavior.getMemberSources().stream().filter(source -> type.equals(source.memberType())
        && source.memberName().equalsIgnoreCase(name)).toList();
  }

  private static boolean canInspect(String model) {
    return BusinessBehaviorSecurity.hasAuthority(BusinessBehaviorAuthorities.inspect(model));
  }
}
