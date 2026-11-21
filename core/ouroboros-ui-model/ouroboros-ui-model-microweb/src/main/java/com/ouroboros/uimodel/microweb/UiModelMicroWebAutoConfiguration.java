package com.ouroboros.uimodel.microweb;

import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

import jakarta.annotation.PostConstruct;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;

import io.vavr.Tuple;

import com.ouroboros.lifecycle.event.OuroborosModelRefreshedEvent;
import com.ouroboros.util.SPI;

/**
 * @author liansz
 */
@Configuration
@ComponentScan
public class UiModelMicroWebAutoConfiguration {

  private final static Logger logger = LoggerFactory.getLogger(UiModelMicroWebAutoConfiguration.class);

  /**
   * 微前端元数据路径
   */
  private final String MICRO_WEB_METADATA_PATH = "META-INF/ouroboros/micro-web/";

  /**
   * 微前端包路径
   */
  private final String MICRO_WEB_PACKAGE_PATH = MICRO_WEB_METADATA_PATH + "package/";

  @Autowired(required = false)
  private MicroWebDeployService microWebDeployService;

  @Autowired
  Environment env;

  @Value("${ouroboros.microWeb.autoDeploy:false}")
  private Boolean autoDeploy;

  @Value("${ouroboros.microWeb.deployPath:${ouroboros.web-root:}}")
  private String webRoot;

  @PostConstruct
  void init() {
    logger.info("Ouroboros UI Model Microweb Auto Configuration loaded");
  }

  @EventListener(OuroborosModelRefreshedEvent.class)
  public void deployMicroWeb(OuroborosModelRefreshedEvent event) {
    if (ObjectUtils.isEmpty(microWebDeployService)) {
      logger.warn("Micro Web Deploy Service 未找到，跳过部署微前端");
      return;
    }
    // 扫描微应用
    List<MicroWebModel> models = SPI.getSortedServiceStream(MicroWebModelProvider.class)
        .map(MicroWebModelProvider::getAllMicroWebModel)
        .flatMap(List::stream)
        .collect(Collectors.toList());

    // 检查微应用资源是否合法、存在
    Map<String, InputStream> microWebMap = models.stream()
        .map(model -> Tuple.of(
            resolveDeployPath(model),
            model.getPackageContent()))
        .filter(tuple -> StringUtils.isNotEmpty(tuple._1)
            && !tuple._1.contains("://")
            && ObjectUtils.isNotEmpty(tuple._2))
        .collect(
            LinkedHashMap::new,
            (map, tuple) -> map.put(tuple._1, tuple._2),
            HashMap::putAll
        );


    var isLegacyConfig = !env.containsProperty("ouroboros.micro-web.deploy-path") && env.containsProperty("ouroboros.web-root");
    var skipDeploy = webRoot.isEmpty() || !(autoDeploy || isLegacyConfig);
    if (skipDeploy) {
      return;
    }

    // 部署微应用
    if (ObjectUtils.isNotEmpty(microWebMap)) {
      microWebMap.forEach(microWebDeployService::deploy);
      logger.info("微前端 {} 部署成功", String.join(",", microWebMap.keySet()));
    }
  }

  static String resolveDeployPath(MicroWebModel model) {
    if (model == null) {
      return null;
    }
    return StringUtils.isNotBlank(model.getEntry()) ? model.getEntry() : model.getPublicPath();
  }
}
