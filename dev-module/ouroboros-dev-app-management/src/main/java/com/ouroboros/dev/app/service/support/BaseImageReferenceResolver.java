package com.ouroboros.dev.app.service.support;

import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import io.vavr.control.Try;

import com.ouroboros.dev.app.model.BaseImageReference;
import com.ouroboros.exception.BusinessException;

@Component
public class BaseImageReferenceResolver {

  private static final Pattern TAG_PATTERN = Pattern.compile("^[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}$");

  public Try<BaseImageReference> parseBaseImage(String configuredImageName) {
    return parse(configuredImageName, true);
  }

  public Try<String> buildImageNameFromTag(String configuredImageName, String tag) {
    return parseBaseImage(configuredImageName)
        .flatMap(reference -> validateTag(tag).map(validTag -> reference.getRepositoryWithName() + ":" + validTag));
  }

  public Try<String> requireBaseImage(String configuredImageName, String imageName) {
    return parseBaseImage(configuredImageName)
        .flatMap(reference -> parse(imageName, false)
            .flatMap(candidate -> {
              if (!reference.getRepositoryWithName().equals(candidate.getRepositoryWithName())) {
                return Try.failure(new BusinessException("镜像不属于基座镜像范围"));
              }
              return Try.success(candidate.getImageName());
            }));
  }

  private Try<BaseImageReference> parse(String imageName, boolean configured) {
    if (StringUtils.isBlank(imageName)) {
      return Try.failure(new BusinessException(configured ? "默认基座镜像配置不能为空" : "镜像名不能为空"));
    }
    if (imageName.contains("@")) {
      return Try.failure(new BusinessException(configured ? "默认基座镜像配置不支持 digest 形式" : "镜像名不支持 digest 形式"));
    }

    var tagSeparator = imageName.lastIndexOf(':');
    var pathSeparator = imageName.lastIndexOf('/');
    if (tagSeparator <= pathSeparator || tagSeparator == imageName.length() - 1) {
      return Try.failure(new BusinessException(configured ? "默认基座镜像配置必须带显式 tag" : "镜像名必须带显式 tag"));
    }

    var repositoryWithName = imageName.substring(0, tagSeparator);
    var tag = imageName.substring(tagSeparator + 1);
    return validateTag(tag)
        .map(validTag -> new BaseImageReference(repositoryWithName, validTag, repositoryWithName + ":" + validTag));
  }

  private Try<String> validateTag(String tag) {
    var normalizedTag = StringUtils.trimToEmpty(tag);
    if (!TAG_PATTERN.matcher(normalizedTag).matches()) {
      return Try.failure(new BusinessException("tag 格式非法"));
    }
    return Try.success(normalizedTag);
  }
}
