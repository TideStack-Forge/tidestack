package com.ouroboros.security.rbac.service.impl;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.cache.annotation.AnnotationCacheOperationSource;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.cache.interceptor.CacheInterceptor;
import org.springframework.cache.interceptor.SimpleKey;
import org.springframework.test.util.ReflectionTestUtils;

import io.vavr.control.Try;

import com.ouroboros.data.model.TypedDataModel;
import com.ouroboros.exception.OuroborosException;
import com.ouroboros.security.rbac.entity.RoleAuthorityMapEntity;
import com.ouroboros.security.rbac.entity.RoleEntity;
import com.ouroboros.security.rbac.entity.UserEntity;
import com.ouroboros.security.rbac.entity.UserRoleMapEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

class RbacCacheRecoveryTest {
  @TestFactory
  Stream<DynamicTest> failedLoadsRecoverWithoutRestart() {
    return Stream.of(
        scenarios(UserServiceImpl.class, UserServiceImpl::new, UserEntity.class,
            "ouroboros:users", UserServiceImpl::doSearchAll),
        scenarios(RoleServiceImpl.class, RoleServiceImpl::new, RoleEntity.class,
            "ouroboros:roles", RoleServiceImpl::doSearchAll),
        scenarios(UserRoleServiceImpl.class, UserRoleServiceImpl::new, UserRoleMapEntity.class,
            "ouroboros:user:role", UserRoleServiceImpl::searchAll),
        scenarios(RoleAuthorityServiceImpl.class, RoleAuthorityServiceImpl::new, RoleAuthorityMapEntity.class,
            "ouroboros:role:authority", RoleAuthorityServiceImpl::searchAll)
    ).flatMap(Function.identity());
  }

  private <S, E> Stream<DynamicTest> scenarios(Class<S> serviceType, Supplier<S> factory,
                                               Class<E> entityType, String cacheName,
                                               Function<S, List<E>> load) {
    return Stream.of(
        DynamicTest.dynamicTest(cacheName + " retries failed loads and caches recovery", () -> {
          TypedDataModel<String, E> model = mock(TypedDataModel.class);
          var cause = new IllegalStateException("database read timed out");
          List<E> recovered = List.of(mock(entityType));
          when(model.query(anyMap())).thenReturn(Try.failure(cause), Try.success(recovered));
          var cacheManager = new ConcurrentMapCacheManager(cacheName);
          S service = cachedService(serviceType, factory.get(), model, cacheManager);

          var failure = assertThrows(OuroborosException.class, () -> load.apply(service));
          assertSame(cause, failure.getCause());
          assertNull(cacheManager.getCache(cacheName).get(SimpleKey.EMPTY));
          assertEquals(recovered, load.apply(service));
          assertEquals(recovered, load.apply(service));
          verify(model, times(2)).query(anyMap());
        }),
        DynamicTest.dynamicTest(cacheName + " concurrent recovery never publishes failed empty data", () -> {
          TypedDataModel<String, E> model = mock(TypedDataModel.class);
          List<E> recovered = List.of(mock(entityType));
          var entered = new CountDownLatch(1);
          var release = new CountDownLatch(1);
          when(model.query(anyMap())).thenAnswer(invocation -> {
            entered.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return Try.failure(new IllegalStateException("read timed out"));
          }).thenReturn(Try.success(recovered));
          var cacheManager = new ConcurrentMapCacheManager(cacheName);
          S service = cachedService(serviceType, factory.get(), model, cacheManager);
          try (var executor = Executors.newFixedThreadPool(2)) {
            var failed = executor.submit(() -> assertThrows(OuroborosException.class, () -> load.apply(service)));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            var waiting = executor.submit(() -> load.apply(service));
            release.countDown();
            failed.get(5, TimeUnit.SECONDS);
            assertEquals(recovered, waiting.get(5, TimeUnit.SECONDS));
            assertEquals(recovered, load.apply(service));
            verify(model, times(2)).query(anyMap());
          } finally {
            release.countDown();
          }
        }),
        DynamicTest.dynamicTest(cacheName + " caches a successfully queried empty list", () -> {
          TypedDataModel<String, E> model = mock(TypedDataModel.class);
          when(model.query(anyMap())).thenReturn(Try.success(List.of()));
          var cacheManager = new ConcurrentMapCacheManager(cacheName);
          S service = cachedService(serviceType, factory.get(), model, cacheManager);

          assertTrue(load.apply(service).isEmpty());
          assertTrue(load.apply(service).isEmpty());
          assertNotNull(cacheManager.getCache(cacheName).get(SimpleKey.EMPTY));
          verify(model).query(anyMap());
        })
    );
  }

  private <S, E> S cachedService(Class<S> type, S target, TypedDataModel<String, E> model,
                                 ConcurrentMapCacheManager cacheManager) {
    ReflectionTestUtils.setField(target, "dataModel", model);
    var interceptor = new CacheInterceptor();
    interceptor.setCacheOperationSources(new AnnotationCacheOperationSource());
    interceptor.setCacheManager(cacheManager);
    interceptor.afterPropertiesSet();
    interceptor.afterSingletonsInstantiated();
    var proxyFactory = new ProxyFactory(target);
    proxyFactory.setProxyTargetClass(true);
    proxyFactory.addAdvice(interceptor);
    return type.cast(proxyFactory.getProxy());
  }
}
