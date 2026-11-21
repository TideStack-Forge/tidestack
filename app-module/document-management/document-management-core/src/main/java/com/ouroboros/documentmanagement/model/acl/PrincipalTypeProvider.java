package com.ouroboros.documentmanagement.model.acl;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * 权限控制主体类型提供者接口
 *
 * <p>使用 Java SPI 机制动态发现和加载主体类型定义。
 *
 * <p>SPI 注册方式：在 {@code META-INF/services/com.ouroboros.documentmanagement.model.acl.PrincipalTypeProvider}
 * 文件中注册实现类：
 * <pre>
 * com.ouroboros.example.CustomPrincipalTypeProvider
 * </pre>
 *
 * <p>实现示例：
 * <pre>
 * public class UserPrincipalTypeProvider implements PrincipalTypeProvider {
 *     {@literal @}Override
 *     public List&lt;PrincipalType&gt; getPrincipalTypes() {
 *         return Collections.singletonList(new UserPrincipalType());
 *     }
 * }
 * </pre>
 */
public interface PrincipalTypeProvider {

  /**
   * 获取该提供者提供的所有主体类型
   *
   * @return 主体类型列表
   */
  List<PrincipalType> getPrincipalTypes();

  /**
   * 根据类型名称获取主体类型
   *
   * @param name 主体类型名称
   * @return 主体类型，如果不存在则返回 empty
   */
  static Optional<PrincipalType> get(String name) {
    return PrincipalTypeRegistry.getInstance().get(name);
  }

  /**
   * 获取所有已注册的主体类型
   *
   * @return 所有主体类型列表
   */
  static List<PrincipalType> getAll() {
    return PrincipalTypeRegistry.getInstance().getAll();
  }

  /**
   * 主体类型注册表（单例）
   *
   * <p>通过 SPI 自动加载所有 {@link PrincipalTypeProvider} 实现，
   * 并缓存所有提供的 {@link PrincipalType}。
   */
  class PrincipalTypeRegistry {

    private static final PrincipalTypeRegistry INSTANCE = new PrincipalTypeRegistry();

    private final Map<String, PrincipalType> registry = new ConcurrentHashMap<>();

    private PrincipalTypeRegistry() {
      loadPrincipalTypes();
    }

    /**
     * 通过 SPI 加载所有主体类型
     *
     * <p>加载顺序：
     * <ol>
     *   <li>加载所有通过 PrincipalTypeProvider 提供的主体类型</li>
     *   <li>加载所有直接注册的 PrincipalType 实现</li>
     * </ol>
     */
    private void loadPrincipalTypes() {
      // 1. 加载所有 PrincipalTypeProvider 提供的类型
      ServiceLoader<PrincipalTypeProvider> providerLoader =
          ServiceLoader.load(PrincipalTypeProvider.class);

      for (PrincipalTypeProvider provider : providerLoader) {
        List<PrincipalType> types = provider.getPrincipalTypes();
        if (types != null) {
          for (PrincipalType type : types) {
            if (type != null && type.getName() != null) {
              registry.put(type.getName(), type);
            }
          }
        }
      }

      // 2. 加载所有直接注册的 PrincipalType
      ServiceLoader<PrincipalType> typeLoader =
          ServiceLoader.load(PrincipalType.class);

      for (PrincipalType type : typeLoader) {
        if (type != null && type.getName() != null) {
          registry.put(type.getName(), type);
        }
      }
    }

    public static PrincipalTypeRegistry getInstance() {
      return INSTANCE;
    }

    /**
     * 根据名称获取主体类型
     *
     * @param name 主体类型名称
     * @return 主体类型，如果不存在则返回 empty
     */
    public Optional<PrincipalType> get(String name) {
      if (name == null || name.isEmpty()) {
        return Optional.empty();
      }
      return Optional.ofNullable(registry.get(name));
    }

    /**
     * 获取所有已注册的主体类型
     *
     * @return 所有主体类型列表
     */
    public List<PrincipalType> getAll() {
      return new ArrayList<>(registry.values());
    }

    /**
     * 检查指定类型是否已注册
     *
     * @param name 主体类型名称
     * @return 如果已注册返回 true
     */
    public boolean contains(String name) {
      return name != null && registry.containsKey(name);
    }

    /**
     * 获取已注册的主体类型数量
     *
     * @return 类型数量
     */
    public int size() {
      return registry.size();
    }
  }
}
