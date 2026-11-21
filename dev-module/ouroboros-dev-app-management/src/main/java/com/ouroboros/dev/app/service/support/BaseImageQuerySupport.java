package com.ouroboros.dev.app.service.support;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

import org.springframework.stereotype.Component;

import io.vavr.control.Try;

import com.ouroboros.dev.app.model.BaseImageReference;
import com.ouroboros.exception.BusinessException;

@Component
public class BaseImageQuerySupport {

  private final BaseImageReferenceResolver baseImageReferenceResolver;

  public BaseImageQuerySupport(BaseImageReferenceResolver baseImageReferenceResolver) {
    this.baseImageReferenceResolver = baseImageReferenceResolver;
  }

  public Try<List<BaseImageReference>> getBaseImages(String configuredImageName, List<String> localTaggedImageNames) {
    return baseImageReferenceResolver.parseBaseImage(configuredImageName)
        .map(ignore -> {
          var baseImagesByName = new TreeMap<String, BaseImageReference>();
          for (String imageName : localTaggedImageNames) {
            var baseImage = baseImageReferenceResolver.requireBaseImage(configuredImageName, imageName)
                .flatMap(baseImageReferenceResolver::parseBaseImage)
                .getOrNull();
            if (baseImage == null) {
              continue;
            }
            baseImagesByName.put(baseImage.getImageName(), baseImage);
          }
          return new ArrayList<>(baseImagesByName.values());
        });
  }

  public Try<String> getDefaultImage(String configuredImageName, List<String> localTaggedImageNames) {
    return getBaseImages(configuredImageName, localTaggedImageNames)
        .map(baseImages -> {
          if (baseImages.isEmpty()) {
            return "";
          }

          return baseImages.stream()
              .filter(baseImage -> configuredImageName.equals(baseImage.getImageName()))
              .findFirst()
              .map(BaseImageReference::getImageName)
              .orElseGet(() -> baseImages.get(0).getImageName());
        });
  }

  public Try<String> requirePulledBaseImage(String configuredImageName, List<String> localTaggedImageNames,
                                            String imageName) {
    return getBaseImages(configuredImageName, localTaggedImageNames)
        .flatMap(baseImages -> baseImages.stream()
            .filter(baseImage -> imageName.equals(baseImage.getImageName()))
            .findFirst()
            .map(BaseImageReference::getImageName)
            .map(Try::success)
            .orElseGet(() -> Try.failure(new BusinessException("镜像未在本地基座镜像列表中找到"))));
  }

  public Try<String> buildImageNameFromTag(String configuredImageName, String tag) {
    return baseImageReferenceResolver.buildImageNameFromTag(configuredImageName, tag);
  }
}
