package com.ouroboros.uimodel.service.impl;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import io.vavr.Tuple;

import com.ouroboros.WebAppInfoContributor;
import com.ouroboros.common.app.util.SpringContextUtil;
import com.ouroboros.event.UIModelChangedEvent;
import com.ouroboros.lifecycle.event.OuroborosContextRefreshedEvent;
import com.ouroboros.uimodel.UIModel;
import com.ouroboros.uimodel.UIModelProvider;
import com.ouroboros.uimodel.service.UIRouteService;
import com.ouroboros.uiroute.UIRoute;
import com.ouroboros.util.SPI;

/**
 * @author liansz
 */
@Component
@SuppressWarnings("unused")
public class UIRouteServiceImpl implements UIRouteService {
  private static final Logger logger = LoggerFactory.getLogger(UIRouteServiceImpl.class);
  private List<UIRoute> uiRouteList = Collections.emptyList();

  @Override
  public List<UIRoute> getAllRoute() {
    return uiRouteList;
  }

  @EventListener(OuroborosContextRefreshedEvent.class)
  void onContextRefreshedEvent(OuroborosContextRefreshedEvent event) {
    initUiModelMap();
  }

  @EventListener(UIModelChangedEvent.class)
  void onUiModelChangedEvent(UIModelChangedEvent event) {
    initUiModelMap();
  }

  private void initUiModelMap() {
    var routes = new ArrayList<>(
        SPI.getCachedSortedServiceStream(UIModelProvider.class)
            .flatMap(provider ->
                provider.getUIModelMap().entrySet().stream())
            .map(entry -> Tuple.of(entry.getKey(), buildRoute(entry.getValue())))
            .collect(() -> new TreeMap<String, UIRoute>(String::compareToIgnoreCase),
                (a, b) -> a.put(b._1, b._2),
                TreeMap::putAll)
            .values()
    );

    uiRouteList = Collections.unmodifiableList(routes);
  }

  @SuppressWarnings("EnhancedSwitchMigration")
  private UIRoute buildRoute(UIModel uiModel) {
    var route = new UIRoute();
    var config = new HashMap<String, Object>();
    route.setPath(uiModel.getPath());
    route.setRouteType(uiModel.getType());
    route.setConfig(config);
    route.setName(uiModel.getTitle());
    switch (uiModel.getType()) {
      case "amis":
        config.put("schemaId", uiModel.getExtraProp("schemaId").orElse(uiModel.getPath()));
        break;
      case "micro":
        var entry = String.valueOf(uiModel.getExtraProp("entry").orElse(""));
        if (entry.isEmpty()) {
          logger.warn("Micro-frontend route '{}' is missing required 'entry' property, using empty string as fallback", 
              uiModel.getPath());
        }
        config.put("entry", entry);
        break;
      case "qb":
      case "internal":
      default:
        break;
    }
    return route;
  }

  public static class UIRouteWebAppInfoContributor implements WebAppInfoContributor {

    @Override
    public Map<String, Object> getWebAppInfo() {
      return Collections.singletonMap("routes", SpringContextUtil.getBean(UIRouteService.class).getAllRoute());
    }
  }
}
