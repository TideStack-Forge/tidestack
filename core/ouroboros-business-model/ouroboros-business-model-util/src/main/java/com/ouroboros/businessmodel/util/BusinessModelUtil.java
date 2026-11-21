package com.ouroboros.businessmodel.util;

import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.ouroboros.businessmodel.BusinessModel;

import com.ouroboros.util.Strings;

public class BusinessModelUtil {
  public static String buildKebabCasePath(BusinessModel model) {
    return buildKebabCasePath(model.getFullName());
  }

  public static String buildKebabCasePath(String fullName) {
    final var path = Stream.of(fullName.split("\\."))
        .map(part -> Strings.toKebabCase(part))
        .collect(Collectors.joining("/"));
    return "/" + path;
  }
}
