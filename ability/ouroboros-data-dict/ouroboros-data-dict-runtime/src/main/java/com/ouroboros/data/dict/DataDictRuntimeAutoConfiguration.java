package com.ouroboros.data.dict;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import io.vavr.control.Try;

import com.ouroboros.AppContext;
import com.ouroboros.data.annotation.DataModelScan;
import com.ouroboros.data.dict.service.DataDictCoreService;
import com.ouroboros.data.dict.service.DataDictItemService;
import com.ouroboros.data.dict.provider.AppHomeDataDictMetadataProvider;
import com.ouroboros.data.dict.provider.DataDictMetadataProvider;
import com.ouroboros.data.dict.provider.JarDataDictMetadataProvider;
import com.ouroboros.data.model.DataOperationIdentityProviders;
import com.ouroboros.exception.InternalException;
import com.ouroboros.lifecycle.event.OuroborosModelRefreshedEvent;

/**
 * @author sunquan
 */
@DataModelScan
@Configuration
@ComponentScan
public class DataDictRuntimeAutoConfiguration {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  @Autowired
  private DataDictService dataDictService;

  @Autowired
  private DataDictCoreService dataDictCoreService;

  @Autowired
  private DataDictItemService dataDictItemService;

  @Autowired(required = false)
  private List<DataDictMetadataProvider> dataDictMetadataProviders = Collections.emptyList();

  @Bean
  @ConditionalOnMissingBean
  public JarDataDictMetadataProvider jarDataDictMetadataProvider() {
    return new JarDataDictMetadataProvider();
  }

  @Bean
  @ConditionalOnMissingBean
  public AppHomeDataDictMetadataProvider appHomeDataDictMetadataProvider(AppContext appContext) {
    return new AppHomeDataDictMetadataProvider(appContext);
  }

  @EventListener(classes = OuroborosModelRefreshedEvent.class)
  public void init() {
    logger.info("Ouroboros Data Dict Runtime Auto Configuration loaded");

    Map<String, ? extends DataDict> dictMapFromDb = dataDictCoreService.searchAll().stream().collect(
        Collectors.toMap(DataDict::getName, dict -> dict, (v1, v2) -> v2)
    );

    loadMetadataDataDicts().values().forEach(dict -> applyDataDict(dictMapFromDb, dict));
  }

  private Map<String, DataDict> loadMetadataDataDicts() {
    return dataDictMetadataProviders.stream()
        .sorted(AnnotationAwareOrderComparator.INSTANCE)
        .map(DataDictMetadataProvider::getDataDicts)
        .collect(
            () -> new TreeMap<String, DataDict>(String::compareToIgnoreCase),
            Map::putAll,
            TreeMap::putAll
        );
  }

  private void applyDataDict(Map<String, ? extends DataDict> dictMapFromDb, DataDict dict) {
    final String dictName = dict.getName();

    if (!dictMapFromDb.containsKey(dictName)) {
      // 字典不存在，直接插入（因为软删除的原因，插入前预防性删除）
      replaceDataDict(dictName, dict);
      return;
    }

    // 字典已经存在，且不允许修改，直接无脑删除重新插入，避免判断修改
    // TODO: 此处有一定的风险，如果原字典是可编辑的，后改为不可编辑，有可能会导致用户数据丢失！！！
    if (!dict.isEditable()) {
      replaceDataDict(dictName, dict);
    }
  }

  private void replaceDataDict(String dictName, DataDict dict) {
    DataOperationIdentityProviders.withCurrentOperator(DataOperationIdentityProviders.SYSTEM_OPERATOR, () -> {
      Try.of(() -> dataDictCoreService.drop(dictName))
          .mapTry(r -> dataDictItemService.dropByDict(dictName))
          .mapTry(r -> dataDictService.add(dict))
          .getOrElseThrow(e -> new InternalException("加载字典模型失败", e));
      return null;
    });
  }
}
