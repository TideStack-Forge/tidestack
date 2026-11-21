package com.ouroboros.dev.app.controller;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;

import com.ouroboros.dev.app.metadata.MetadataGroupOptions;
import com.ouroboros.dev.metadata.Metadata;
import com.ouroboros.dev.metadata.MetadataProvider;
import com.ouroboros.dev.metadata.MetadataProviderMixer;
import com.ouroboros.web.ItemsWebApiResult;
import com.ouroboros.web.SelectOption;
import com.ouroboros.web.WebApiResult;

/**
 * @author liansz
 **/
@RestController
@RequestMapping("/api/dev/{appName}/metadata/{moduleName}")
public class DevAppMetadataController {

  private final MetadataProviderMixer metadataProviderMixer;

  public DevAppMetadataController(List<MetadataProvider> metadataProviders) {
    this.metadataProviderMixer = MetadataProviderMixer
        .getDistinctMixer(metadataProviders == null ? Collections.emptyList() : metadataProviders);
  }

  @GetMapping("/{name}")
  public WebApiResult<?> detail(@PathVariable("appName") String appName,
                                @PathVariable("moduleName") String moduleName,
                                @PathVariable("name") String name,
                                @RequestParam(defaultValue = "select") String mode) {
    var found = metadataProviderMixer.getOne(appName, moduleName, name);
    Optional<?> result;
    switch (mode) {
      case "select":
        result = found.map(metadata -> new SelectOption(metadata.getName(), metadata.getLabel()));
        break;
      case "raw":
        result = found.map(Metadata::getDetails);
        break;
      default:
        result = found;
    }
    return WebApiResult.of(result);
  }

  @GetMapping
  public ItemsWebApiResult<?> list(@PathVariable("appName") String appName,
                                   @PathVariable("moduleName") String moduleName,
                                   @RequestParam(defaultValue = "") String name,
                                   @RequestParam(defaultValue = "") String keyword,
                                   @RequestParam(defaultValue = "select") String mode,
                                   @RequestParam(required = false) List<String> value) {
    var finalKeyword = keyword.isEmpty() ? name : keyword;
    var finalValueList = value == null ? Collections.emptyList() : value;

    // 根据关键词过滤
    Predicate<Metadata> filterByKeyword = StringUtils.isBlank(finalKeyword)
        ? metadata -> true
        : metadata -> StringUtils.contains(metadata.getLabel(), keyword)
        || StringUtils.contains(metadata.getName(), keyword);

    // 最终的过滤器为：如果有值列表，则根据值列表过滤
    Predicate<Metadata> finalFilter = finalValueList.isEmpty()
        ? filterByKeyword
        : metadata -> finalValueList.contains(metadata.getName());
    List<Metadata> searchResult = metadataProviderMixer.getAppAllMetadataByModule(appName, moduleName)
        .stream()
        .filter(finalFilter)
        .collect(Collectors.toList());
    Stream<?> result;
    switch (mode) {
      case "select":
        result = searchResult.stream()
            .map(metadata -> new SelectOption(metadata.getName(), metadata.getLabel()));
        break;
      case "groups":
        result = MetadataGroupOptions.groups(searchResult).stream();
        break;
      case "raw":
        result = searchResult.stream().map(Metadata::getDetails);
        break;
      default:
        result = searchResult.stream();
    }
    return ItemsWebApiResult.ok(result.collect(Collectors.toList()));
  }
}
