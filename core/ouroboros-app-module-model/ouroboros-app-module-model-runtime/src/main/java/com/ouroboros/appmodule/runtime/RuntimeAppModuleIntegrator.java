package com.ouroboros.appmodule.runtime;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ObjectUtils;
import org.springframework.stereotype.Component;

import com.ouroboros.appmodule.*;
import com.ouroboros.util.SPI;

@Component
public class RuntimeAppModuleIntegrator implements AppModuleIntegrator {
  @Override
  public List<AppModule> getAppModules() {
    var moduleMap = SPI.getCachedSortedServiceStream(AppModuleProvider.class)
        .map(AppModuleProvider::getAppModules)
        .flatMap(List::stream)
        .collect(Collectors.groupingBy(AppModule::getFullName));
    var modules = moduleMap.values().stream()
        .map(list -> list.size() == 1 ? list.get(0) : list.stream().reduce(AppModule.MergedAppModule::new).get())
        .sorted(Comparator.comparingInt(m -> Optional.ofNullable(m.getOrder()).orElse(0)))
        .collect(Collectors.toList());

    return SPI.getCachedSortedServiceStream(AppModuleDiffProvider.class)
        .map(AppModuleDiffProvider::getAppModuleDiffs)
        .filter(ObjectUtils::isNotEmpty)
        .reduce(modules, AppModuleDiffUtil::patch, (a, b) -> a);
  }
}
