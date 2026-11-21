# Common Cache - 缓存工具指南

本文档描述了 `common-cache` 模块的使用方法,该模块提供了统一的缓存管理接口和实现。

## 模块概述

`common-cache` 模块提供了一个抽象的缓存管理接口,支持:
- 命名空间管理 (多个独立的缓存空间)
- 键值对存储和检索
- 缓存过期时间控制
- 基于 Spring Cache 的适配器
- 默认内存缓存实现

## 核心类和接口

### CacheManager 接口

路径: `com.ouroboros.cache.CacheManager`

缓存管理器的核心接口,提供了缓存的基本操作。

**主要方法:**

- `Optional<Object> get(String name, String key)` - 从指定缓存空间获取值
- `void put(String name, String key, Object value)` - 存储值到指定缓存空间
- `void put(String name, String key, Object value, long timeout)` - 存储值并设置过期时间(毫秒)
- `void evict(String name, String key)` - 删除指定缓存项
- `void clear(String name)` - 清空指定缓存空间
- `void clear()` - 清空所有缓存
- `void renew(String name, String key, long timeout)` - 更新缓存项的过期时间
- `<T> T getOrPut(String name, String key, Supplier<T> supplier)` - 获取缓存,不存在则计算并存储
- `static CacheManager getCacheManager()` - 获取 CacheManager 实例 (通过 SPI)

### AbstractSpringCacheManagerAdapter 抽象类

路径: `com.ouroboros.cache.AbstractSpringCacheManagerAdapter`

Spring Cache 适配器基类,可继承此类以集成不同的 Spring Cache 实现(如 Redis, Caffeine 等)。

**扩展示例:**
```java
public class RedisCacheManagerAdapter extends AbstractSpringCacheManagerAdapter {
    @Autowired
    private RedisCacheManager redisCacheManager;

    @Override
    protected org.springframework.cache.CacheManager getSpringCacheManager() {
        return redisCacheManager;
    }
}
```

### MemoryCacheManager 类

路径: `com.ouroboros.cache.MemoryCacheManager`

默认的内存缓存实现,基于 `ConcurrentMapCacheManager`,作为系统的默认缓存提供者。

## 使用示例

### 基本用法

```java
// 获取 CacheManager 实例
CacheManager cacheManager = CacheManager.getCacheManager();

// 存储值
cacheManager.put("user-cache", "user:123", userObject);

// 获取值
Optional<Object> value = cacheManager.get("user-cache", "user:123");
value.ifPresent(v -> {
    User user = (User) v;
    System.out.println(user.getName());
});

// 删除缓存项
cacheManager.evict("user-cache", "user:123");

// 清空整个缓存空间
cacheManager.clear("user-cache");
```

### 使用默认缓存空间

```java
// 使用默认缓存空间 "default"
cacheManager.get("myKey");  // 等同于 get("default", "myKey")
```

### 设置过期时间

```java
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Date;

// 使用毫秒数设置过期时间
cacheManager.put("session", "token:abc", sessionData, 3600000L); // 1小时

// 使用 Duration 设置过期时间
cacheManager.put("session", "token:abc", sessionData, Duration.ofHours(1));

// 使用 LocalDateTime 设置过期时间
LocalDateTime expireTime = LocalDateTime.now().plusHours(1);
cacheManager.put("session", "token:abc", sessionData, expireTime);

// 使用 Date 设置过期时间
Date expireDate = new Date(System.currentTimeMillis() + 3600000);
cacheManager.put("session", "token:abc", sessionData, expireDate);

// 更新过期时间
cacheManager.renew("session", "token:abc", 7200000L); // 延长2小时
```

### 懒加载缓存 (GetOrPut 模式)

```java
// 如果缓存存在则直接返回,否则执行 supplier 并缓存结果
User user = cacheManager.getOrPut("user-cache", "user:123", () -> {
    return userService.loadUserById(123);
});

// 带过期时间的懒加载
User user = cacheManager.getOrPut("user-cache", "user:123",
    () -> userService.loadUserById(123),
    Duration.ofMinutes(30).toMillis()
);
```

### Spring 集成示例

```java
@Component
public class UserSessionStorage {
    @Autowired
    private CacheManager cacheManager;

    public void saveSession(UserSession session) {
        // 存储会话,30分钟过期
        cacheManager.put("sessions", session.getId(), session,
            Duration.ofMinutes(30));
    }

    public Optional<UserSession> getSession(String sessionId) {
        return cacheManager.get("sessions", sessionId)
            .filter(UserSession.class::isInstance)
            .map(UserSession.class::cast);
    }

    public void deleteSession(String sessionId) {
        cacheManager.evict("sessions", sessionId);
    }

    public List<UserSession> getAllSessions() {
        return cacheManager.get("sessions", "all-sessions")
            .filter(List.class::isInstance)
            .map(l -> (List<?>) l)
            .orElseGet(Collections::emptyList)
            .stream()
            .filter(UserSession.class::isInstance)
            .map(UserSession.class::cast)
            .collect(Collectors.toList());
    }
}
```

### 自定义缓存实现 (SPI)

如果需要替换默认的内存缓存,可以通过 SPI 机制提供自定义实现:

1. 实现 `CacheManager` 接口或继承 `AbstractSpringCacheManagerAdapter`:

```java
@Priority(100)  // 优先级高于默认实现(0)
public class RedisCacheManager extends AbstractSpringCacheManagerAdapter {
    private org.springframework.cache.CacheManager springCacheManager;

    public RedisCacheManager(org.springframework.cache.CacheManager springCacheManager) {
        this.springCacheManager = springCacheManager;
    }

    @Override
    protected org.springframework.cache.CacheManager getSpringCacheManager() {
        return springCacheManager;
    }
}
```

2. 在 `META-INF/services/com.ouroboros.cache.CacheManager` 中注册:

```
com.yourpackage.RedisCacheManager
```

## 注意事项

### 1. 类型转换安全

从缓存获取值时需要进行类型检查和转换:

```java
// 推荐做法:使用 filter 和 map 进行安全转换
Optional<User> user = cacheManager.get("users", "user:123")
    .filter(User.class::isInstance)
    .map(User.class::cast);

// 避免直接强制转换
Optional<Object> value = cacheManager.get("users", "user:123");
User user = (User) value.get(); // 可能抛 ClassCastException
```

### 2. 缓存空间命名

使用有意义的缓存空间名称,避免不同模块之间的缓存冲突:

```java
// 好的命名
cacheManager.put("ouroboros:security:sessions", key, value);
cacheManager.put("user-profiles", key, value);

// 避免的命名
cacheManager.put("cache", key, value);  // 太通用
cacheManager.put("data", key, value);    // 含义不明确
```

### 3. 过期时间管理

- 过期时间基于定时任务清理,而非精确的 TTL
- 设置过期时间后会启动一个 ScheduledFuture 在指定时间后删除缓存项
- 多次调用 `put` 或 `renew` 会取消之前的定时任务并创建新的
- 对于大量短期缓存,建议使用 Redis 等支持原生 TTL 的缓存实现

### 4. 线程安全

- 默认的 `MemoryCacheManager` 基于 `ConcurrentHashMap`,支持并发读写
- 但复杂的原子操作(如 read-modify-write)需要外部同步
- 参考 `CacheBasedUserSessionStorage` 的 TODO 注释

### 5. 序列化问题

- 内存缓存直接存储对象引用,无需序列化
- 如果使用 Redis 等分布式缓存,需要确保缓存对象可序列化
- 建议使用 DTO 而非 Entity 对象缓存

### 6. GetOrPut 的异常处理

```java
// getOrPut 方法会捕获类型转换异常
// 如果缓存值类型不匹配,会重新执行 supplier
User user = cacheManager.getOrPut("cache", "key", () -> {
    // 这个 supplier 在缓存不存在或类型不匹配时执行
    return loadUser();
});
```

## 使用建议

1. **优先使用命名空间** - 使用 `get(name, key)` 而非 `get(key)`,避免缓存键冲突
2. **合理设置过期时间** - 避免缓存过期数据,也避免缓存占用过多内存
3. **使用 GetOrPut 模式** - 简化缓存使用代码,避免重复的判断逻辑
4. **生产环境使用分布式缓存** - 继承 `AbstractSpringCacheManagerAdapter` 集成 Redis 等缓存
5. **缓存键规范** - 使用统一的键命名规范,如 `entity:id` 或 `prefix:category:id`
6. **监控缓存命中率** - 评估缓存效果,优化缓存策略

## 扩展阅读

- Spring Cache 抽象层文档: https://spring.io/guides/gs/caching/
- SPI 工具类文档: `libs/util/ouroboros-util/GUIDE.md`
- 使用示例: `security/ouroboros-web-security/ouroboros-web-security-runtime/src/main/java/com/ouroboros/security/session/storage/CacheBasedUserSessionStorage.java`
