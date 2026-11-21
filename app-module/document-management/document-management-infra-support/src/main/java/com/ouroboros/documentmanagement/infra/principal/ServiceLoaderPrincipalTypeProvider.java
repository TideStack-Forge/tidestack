package com.ouroboros.documentmanagement.infra.principal;

import com.ouroboros.documentmanagement.model.acl.PrincipalType;
import com.ouroboros.documentmanagement.model.acl.PrincipalTypeProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

/**
 * 通用主体类型提供者
 *
 * <p>通过 Java SPI 机制自动加载所有注册的 {@link PrincipalType} 实现。
 *
 * <p>这是一个通用的聚合提供者，它使用 {@link ServiceLoader} 发现并加载所有直接注册为 SPI 服务的
 * {@link PrincipalType} 实现。
 *
 * <p>注册方式：
 * <ol>
 *   <li>实现 {@link PrincipalType} 接口</li>
 *   <li>在 {@code META-INF/services/com.ouroboros.documentmanagement.model.acl.PrincipalType}
 *       文件中注册实现类</li>
 * </ol>
 *
 * <p>本提供者本身也通过 SPI 机制注册：在 {@code META-INF/services/com.ouroboros.documentmanagement.model.acl.PrincipalTypeProvider}
 * 中注册。
 */
public class ServiceLoaderPrincipalTypeProvider implements PrincipalTypeProvider {

  @Override
  public List<PrincipalType> getPrincipalTypes() {
    List<PrincipalType> types = new ArrayList<>();

    ServiceLoader<PrincipalType> loader = ServiceLoader.load(PrincipalType.class);

    for (PrincipalType type : loader) {
      if (type != null) {
        types.add(type);
      }
    }

    return types;
  }
}
