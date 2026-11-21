package com.ouroboros.webapi.util;

import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.ouroboros.util.Paths;
import com.ouroboros.webapi.WebAPIBasePathSupplier;
import com.ouroboros.webapi.WebAPIScope;

/**
 * @author liansz
 **/
@Component
public class WebAPIUtils {
  private static final Set<String> API_PREFIXES = Arrays.stream(WebAPIScope.values()).map(WebAPIScope::getPrefix).collect(Collectors.toSet());

  private static String baseApiPath;

  private static List<WebAPIBasePathSupplier> webAPIBasePathSuppliers = Collections.emptyList();

  public WebAPIUtils(List<WebAPIBasePathSupplier> webAPIBasePathSuppliers) {
    WebAPIUtils.webAPIBasePathSuppliers = webAPIBasePathSuppliers;
  }

  public static String pathNormalizer(String path) {
    return pathNormalizer(
        path,
        API_PREFIXES.stream().filter(
            path::startsWith
        ).findFirst().map(
            WebAPIScope::fromPrefix
        ).orElse(WebAPIScope.INTERNAL)
    );
  }

  public static String pathNormalizer(String path, WebAPIScope scope) {
    String prefix = scope.getPrefix();
    if (path.startsWith(prefix)) {
      path = path.replace(prefix, "");
    }

    String baseApiPath = getBaseApiPath();
    if (!path.startsWith(baseApiPath)) {
      path = Paths.Url.of(baseApiPath, path).toString();
    }

    if (!path.startsWith(prefix)) {
      path = Paths.Url.of(prefix, path).toString();
    }

    return path;
  }

  /**
   * 获取基础API路径
   *
   * @return
   */
  public static String getBaseApiPath() {
    if (baseApiPath == null) {
      baseApiPath = Optional.ofNullable(webAPIBasePathSuppliers).flatMap(suppliers ->
          suppliers.stream()
              .sorted(
                  Comparator.comparingInt(supplier ->
                      supplier.getClass().getAnnotation(Order.class) != null
                          ? supplier.getClass().getAnnotation(Order.class).value()
                          : supplier.getClass().getName().hashCode()
                  )
              )
              .filter(supplier -> StringUtils.isNotEmpty(supplier.getWebAPIBasePath()))
              .findFirst()
              .map(WebAPIBasePathSupplier::getWebAPIBasePath)
              .map(path -> path.startsWith("/") ? path : "/" + path)
              .map(path -> path.startsWith("/api") ? path.substring(4) : path)
      ).orElse("");
    }

    return baseApiPath;
  }
}
