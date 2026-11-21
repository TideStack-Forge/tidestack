package com.ouroboros.security.rbac.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/**
 * @author liansz
 */
public class CollectionCalculateUtils {

  /**
   * 对比新旧两个集合，查找新增的集合
   *
   * @param oldCollection
   * @param newCollection
   * @param equalsFunction
   * @param <T>
   * @return
   */
  public static <T> List<T> findNewCollection(Collection<T> oldCollection, Collection<T> newCollection,
                                              BiFunction<T, T, Boolean> equalsFunction) {
    return getDifference(newCollection, oldCollection, equalsFunction);
  }

  /**
   * 对比新旧两个集合，查找移除的集合
   *
   * @param oldCollection
   * @param newCollection
   * @param equalsFunction
   * @param <T>
   * @return
   */
  public static <T> List<T> findRemoveCollection(Collection<T> oldCollection, Collection<T> newCollection,
                                                 BiFunction<T, T, Boolean> equalsFunction) {
    return getDifference(oldCollection, newCollection, equalsFunction);
  }

  /**
   * 对比新旧两个集合，查找发生变化的集合
   *
   * @param oldCollection
   * @param newCollection
   * @param primaryEqualsFunction
   * @param dataEqualsFunction
   * @param <T>
   * @return
   */
  public static <T> List<T> findUpdateCollection(Collection<T> oldCollection, Collection<T> newCollection,
                                                 BiFunction<T, T, Boolean> primaryEqualsFunction,
                                                 BiFunction<T, T, Boolean> dataEqualsFunction) {
    Collection<T> oldIntersection = CollectionCalculateUtils.getIntersection(oldCollection, newCollection, primaryEqualsFunction);
    Collection<T> newIntersection = CollectionCalculateUtils.getIntersection(newCollection, oldCollection, primaryEqualsFunction);

    return oldIntersection.stream()
        .map(oldElement -> newIntersection.stream()
            .filter(n -> primaryEqualsFunction.apply(n, oldElement))
            .filter(n -> !dataEqualsFunction.apply(n, oldElement))
            .findFirst()
            .orElse(null))
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  /**
   * 求差集
   *
   * @param l1
   * @param l2
   * @param equalsFunction
   * @return
   */
  public static <T> List<T> getDifference(Collection<T> l1, Collection<T> l2, BiFunction<T, T, Boolean> equalsFunction) {
    return calculateCollection(l1, l2, (ia, notNullL2) -> notNullL2.stream().noneMatch(ib -> equalsFunction.apply(ia, ib)));
  }

  /**
   * 求交集
   *
   * @param l1
   * @param l2
   * @param equalsFunction
   * @return
   */
  public static <T> List<T> getIntersection(Collection<T> l1, Collection<T> l2, BiFunction<T, T, Boolean> equalsFunction) {
    return calculateCollection(l1, l2, (ia, notNullL2) -> notNullL2.stream().anyMatch(ib -> equalsFunction.apply(ia, ib)));
  }

  /**
   * 根据表达式计算集合
   *
   * @param l1
   * @param l2
   * @param predicate
   * @return
   */
  private static <T> List<T> calculateCollection(Collection<T> l1, Collection<T> l2, BiFunction<T, Collection<T>, Boolean> predicate) {
    if (l1 == null) {
      l1 = new ArrayList<>(0);
    }
    if (l2 == null) {
      l2 = new ArrayList<>(0);
    }

    Collection<T> finalL2 = l2;
    return l1.stream()
        .filter(ia -> predicate.apply(ia, finalL2))
        .collect(Collectors.toList());
  }
}
