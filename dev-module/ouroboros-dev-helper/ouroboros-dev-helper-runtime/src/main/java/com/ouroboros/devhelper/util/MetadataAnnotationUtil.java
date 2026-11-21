package com.ouroboros.devhelper.util;

import java.util.Optional;

import jakarta.annotation.Priority;

import com.ouroboros.annotation.LowCodeProvider;
import com.ouroboros.dev.metadata.MetadataSourceType;

/**
 * @author liansz
 **/
public class MetadataAnnotationUtil {

  public static int getPriority(Class<?> clazz) {
    return Optional.ofNullable(
        clazz.getAnnotation(Priority.class)
    ).map(Priority::value).orElse(Integer.MAX_VALUE);
  }

  public static MetadataSourceType getSourceType(Class<?> clazz) {
    if (clazz.isAnnotationPresent(LowCodeProvider.class)) {
      return MetadataSourceType.LOW_CODE;
    }

    return MetadataSourceType.PRO_CODE;
  }
}
