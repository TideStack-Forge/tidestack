package com.ouroboros.container.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import io.vavr.control.Either;

import com.ouroboros.container.DockerEventListener;
import com.ouroboros.container.model.Container;

/**
 * @author liansz
 */
public interface ContainerService {

  /**
   * 创建容器
   *
   * @param container
   * @return Either<异常, 容器id>
   */
  Either<Exception, String> create(Container container);

  /**
   * 删除容器
   *
   * @param id 容器id
   * @return
   */
  Either<Exception, Void> delete(String id);

  /**
   * 启动容器
   *
   * @param id 容器id
   * @return
   */
  Either<Exception, Void> start(String id);

  /**
   * 暂停容器
   *
   * @param id 容器id
   * @return
   */
  Either<Exception, Void> pause(String id);

  Either<Exception, Void> unpause(String id);

  /**
   * 停止容器
   *
   * @param id 容器id
   * @return
   */
  Either<Exception, Void> stop(String id);

  Either<Exception, Void> kill(String id);

  /**
   * 重启容器
   *
   * @param id 容器id
   * @return
   */
  Either<Exception, Void> restart(String id);

  /**
   * 注册事件监听器
   *
   * @param listener
   * @return
   */
  void registerEventListener(DockerEventListener listener);

  /**
   * 通过Id获取容器信息
   *
   * @param id 容器id
   * @return
   */
  Either<Exception, Container> getContainerInfoById(String id);

  /**
   * 通过Id获取容器信息
   *
   * @param name 容器名
   * @return
   */
  Either<Exception, Container> getContainerInfoByName(String name);

  /**
   * 获取所有容器信息
   *
   * @return
   */
  Either<Exception, List<Container>> getAllContainerInfo();

  /**
   * 获取所有容器信息
   *
   * @return
   */
  Either<Exception, List<Container>> searchContainerInfo(Map<String, Collection<String>> filterMap);

  Either<Exception, List<String>> getAllImageNames(String reference);

  Either<Exception, List<String>> getAllTaggedImageNames();

  Either<Exception, List<String>> getLogs(String id, Integer tailLines);

  CompletableFuture<Void> pullImageAsync(String imageName);
}
