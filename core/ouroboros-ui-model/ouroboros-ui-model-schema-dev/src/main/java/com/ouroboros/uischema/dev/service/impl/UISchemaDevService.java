package com.ouroboros.uischema.dev.service.impl;

import java.util.Comparator;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import io.vavr.control.Try;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.modelmanage.annotation.DevelopModule;
import com.ouroboros.modelmanage.base.BaseDevelopService;
import com.ouroboros.uischema.wrapper.SchemaNodeWrapper;
import com.ouroboros.util.Json;
import com.ouroboros.vcs.file.VCSFile;
import com.ouroboros.vcs.wrapper.ModelFileMetaWrapper;

@Service
@DevelopModule("ui-schema")
@SuppressWarnings("unused")
public class UISchemaDevService extends BaseDevelopService<SchemaNodeWrapper> {

  static final String MODEL_FILE_DIRECTORY = "ui-schema";
  static final String MODEL_FILE_EXTENSION = ".ui-schema.json";
  private static final String LEGACY_MODEL_FILE_EXTENSION = ".json";


  @Override
  protected String getModelDisplayName() {
    return "UI Schema";
  }

  @Override
  protected String getModelFileDirectory() {
    return MODEL_FILE_DIRECTORY;
  }

  @Override
  protected String getModelFileExtension() {
    return MODEL_FILE_EXTENSION;
  }

  @Override
  protected List<String> getLegacyModelFileExtensions() {
    return Collections.singletonList(LEGACY_MODEL_FILE_EXTENSION);
  }

  @Override
  protected boolean isAutoCommitWhenUpdate() {
    return false;
  }

  @Override
  protected String getFullName(SchemaNodeWrapper model) {
    return model.getName();
  }

  @Override
  protected Function<VCSFile, Try<?>> buildSearchResultMapper() {
    return file -> {
      var model = Json.tryToBean(SchemaNodeWrapper.class, file.getContent());
      return model.map(m -> new ModelFileMetaWrapper<>(m, file));
    };
  }

  @Override
  protected Predicate<ModelFileMetaWrapper<SchemaNodeWrapper>> buildSearchFilter(Map<String, Object> searchParam) {
    var keyword = searchParam.getOrDefault("keyword", "").toString().toLowerCase();
    if (StringUtils.isBlank(keyword)) {
      return m -> true;
    }
    return m -> {
      var name = Optional.ofNullable(m.getModel().getName()).orElse("").toLowerCase();
      var title = Optional.ofNullable(m.getModel().getTitle()).orElse("").toLowerCase();
      return name.contains(keyword) || title.contains(keyword);
    };
  }

  @Override
  public Try<SchemaNodeWrapper> buildModelFromFileContent(String fileContent) {
    return super.buildModelFromFileContent(fileContent)
        .map(node -> {
          String name = Stream.of(node.getName().split("/"))
              .filter(StringUtils::isNotBlank)
              .collect(Collectors.joining("."));
          node.setName(name);
          return node;
        });
  }

  @Override
  protected Try<Void> validateModel(SchemaNodeWrapper model) {
    if (model == null) {
      return Try.failure(new BusinessException("UI Schema不能为空"));
    }
    return Try.success(null);
  }

  @Override
  public List<ModelFileMetaWrapper<SchemaNodeWrapper>> search(Map<String, Object> searchParam) {
    var result = (List<ModelFileMetaWrapper<SchemaNodeWrapper>>) super.search(searchParam);
    return result.stream()
        .sorted(Comparator.comparing(o -> StringUtils.defaultIfBlank(o.getModel().getName(), "")))
        .collect(Collectors.toList());
  }
}
