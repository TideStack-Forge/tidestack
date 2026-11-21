package com.ouroboros.container.service.impl;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.control.Either;
import io.vavr.control.Try;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.ListContainersCmd;
import com.github.dockerjava.api.command.LogContainerCmd;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.ouroboros.container.*;
import com.ouroboros.container.EventObject;
import com.ouroboros.container.config.DockerConfiguration;
import com.ouroboros.container.model.Container;
import com.ouroboros.container.service.ContainerService;

/**
 * @author liansz
 */
//TODO: 这里的所有 DockerClient 可能抛出的异常都没有处理，需要完善
@Service
public class DockerContainerServiceImpl implements ContainerService {

  private final static Logger logger = LoggerFactory.getLogger(DockerContainerServiceImpl.class);

  private final List<DockerEventListener> dockerEventListeners = new ArrayList<>();

  @Autowired
  private DockerConfiguration dockerConfiguration;

  private DockerClient dockerClient;

  @Override
  public Either<Exception, String> create(Container container) {
    try (var builder = getDockerClient().createContainerCmd(container.getImage())) {
      // 构建端口绑定信息
      Ports ports = new Ports();
      if (container.getPortBindings() != null) {
        container.getPortBindings().forEach((protocol, portMap) ->
            portMap.forEach((containerPort, hostPort) -> {
              ExposedPort exposedPort = ExposedPort.parse(containerPort + "/" + protocol);
              ports.bind(exposedPort, container.getPortBindAddress() == null ? Ports.Binding.bindPort(hostPort) : Ports.Binding.bindIpAndPort(container.getPortBindAddress(), hostPort));
            })
        );
      }

      // 构建卷绑定信息
      Map<String, String> volumeMap = container.getVolumeBindings() != null ? container.getVolumeBindings() : new HashMap<>();
      List<Bind> binds = volumeMap.keySet().stream()
          .map(containerPath -> new Bind(volumeMap.get(containerPath), new Volume(containerPath), AccessMode.rw))
          .collect(Collectors.toList());
      List<Volume> volumes = binds.stream().map(Bind::getVolume).collect(Collectors.toList());

      // 构建主机配置
      HostConfig hostConfig = HostConfig.newHostConfig()
          .withPortBindings(ports)
          .withAutoRemove(container.isAutoRemove())
          .withBinds(binds);
      if (container.getMemoryLimitBytes() != null) hostConfig.withMemory(container.getMemoryLimitBytes());
      if (container.getNanoCpus() != null) hostConfig.withNanoCPUs(container.getNanoCpus());
      if (ObjectUtils.isNotEmpty(container.getNetwork())) {
        hostConfig.withNetworkMode(container.getNetwork());
      }

      // 构建环境变量
      List<String> environmentalVariables = new ArrayList<>();
      if (ObjectUtils.isNotEmpty(container.getEnvironmentalVariables())) {
        container.getEnvironmentalVariables().forEach((key, value) -> environmentalVariables.add(key + "=" + value));
      }

      // 准备LABELS
      Map<String, String> labels = new LinkedHashMap<>();
      if (ObjectUtils.isNotEmpty(container.getLabels())) {
        labels.putAll(container.getLabels());
      }
      // 如果没有设置 APP_NAME 则默认为 $$ERROR$$UNKNOWN$$APP$$
      // 这是为了在创建监听器时过滤容器，避免监听其他容器事件造成资源浪费
      if (!labels.containsKey("OUROBOROS_APP_NAME") && !labels.containsKey("OUROBOROS_WORKLOAD_ID")) {
        labels.put("OUROBOROS_APP_NAME", "$$ERROR$$UNKNOWN$$APP$$");
      }

      var response = builder
          .withName(container.getName())
          .withStdinOpen(true)
          .withTty(true)
          .withHostConfig(hostConfig)
          .withExposedPorts(ports.getBindings().keySet().toArray(new ExposedPort[0]))
          .withVolumes(volumes)
          .withEnv(environmentalVariables)
          .withLabels(labels)
          .exec();

      String containerId = response.getId();
      if (response.getId() == null) {
        throw new IllegalStateException("创建容器失败");
      }

      return Either.right(containerId);
    } catch (Exception e) {
      return Either.left(e);
    }
  }

  @Override
  public Either<Exception, Void> delete(String id) {
    try (var cmd = getDockerClient().removeContainerCmd(id)) {
      cmd.exec();
      return Either.right(null);
    } catch (Exception e) {
      return Either.left(e);
    }
  }

  @Override
  public Either<Exception, Void> start(String id) {
    try (var cmd = getDockerClient().startContainerCmd(id)) {
      cmd.exec();
      return Either.right(null);
    } catch (Exception e) {
      return Either.left(e);
    }
  }

  @Override
  public Either<Exception, Void> pause(String id) {
    try (var cmd = getDockerClient().pauseContainerCmd(id)) {
      cmd.exec();
      return Either.right(null);
    } catch (Exception e) {
      return Either.left(e);
    }
  }

  @Override
  public Either<Exception, Void> unpause(String id) {
    try (var cmd = getDockerClient().unpauseContainerCmd(id)) {
      cmd.exec();
      return Either.right(null);
    } catch (Exception e) {
      return Either.left(e);
    }
  }

  @Override
  public Either<Exception, Void> stop(String id) {
    try (var cmd = getDockerClient().stopContainerCmd(id)) {
      cmd.exec();
      return Either.right(null);
    } catch (Exception e) {
      return Either.left(e);
    }
  }

  @Override
  public Either<Exception, Void> kill(String id) {
    try (var cmd = getDockerClient().killContainerCmd(id)) {
      cmd.exec();
      return Either.right(null);
    } catch (Exception e) {
      return Either.left(e);
    }
  }

  @Override
  public Either<Exception, Void> restart(String id) {
    try (var cmd = getDockerClient().restartContainerCmd(id)) {
      cmd.exec();
      return Either.right(null);
    } catch (Exception e) {
      return Either.left(e);
    }
  }

  @Override
  public void registerEventListener(DockerEventListener listener) {
    boolean isEnableListener = dockerEventListeners.isEmpty();
    dockerEventListeners.add(listener);

    if (isEnableListener) {
      this.enableEventListener();
    }
  }

  @Override
  public Either<Exception, Container> getContainerInfoById(String id) {
    try {
      // 获取容器信息
      InspectContainerResponse response = getDockerClient().inspectContainerCmd(id).exec();

      // TODO: 这里考虑用image相关命令来检查镜像是否过期，而不是再list查一遍
      var containers = getDockerClient().listContainersCmd()
          .withShowAll(true)
          .withNameFilter(Collections.singletonList(id)).exec();

      // 获取容器IP
      NetworkSettings networkSettings = response.getNetworkSettings();
      String ip = networkSettings != null ? getContainerIp(networkSettings.getNetworks()) : null;
      String network = networkSettings != null && networkSettings.getNetworks() != null && !networkSettings.getNetworks().isEmpty()
          ? networkSettings.getNetworks().keySet().iterator().next()
          : null;

      // 构建端口映射
      Map<String, Map<Integer, Integer>> portBindingMap = new HashMap<>();
      HostConfig hostConfig = response.getHostConfig();
      if (hostConfig != null) {
        Ports portBindings = networkSettings != null && networkSettings.getPorts() != null
            ? networkSettings.getPorts() : hostConfig.getPortBindings();
        if (portBindings != null) {
          Map<ExposedPort, Ports.Binding[]> bindingMap = portBindings.getBindings();
          if (ObjectUtils.isNotEmpty(bindingMap)) {
            bindingMap.forEach((exposedPort, bindings) -> {
              if (bindings == null || bindings.length == 0) {
                return;
              }

              String protocol = exposedPort.getProtocol().toString();
              int containerPort = exposedPort.getPort();
              int hostPort = Integer.parseInt(bindings[0].getHostPortSpec());

              portBindingMap.computeIfAbsent(protocol, k -> new HashMap<>()).put(containerPort, hostPort);
            });
          }
        }
      }

      // 构建目录映射
      Map<String, String> volumeBindingMap = new HashMap<>();
      List<InspectContainerResponse.Mount> mounts = response.getMounts();
      if (mounts != null) {
        for (InspectContainerResponse.Mount mount : mounts) {
          Volume destination = mount.getDestination();
          if (destination != null) {
            String containerPath = destination.getPath();
            String hostPath = mount.getSource();

            volumeBindingMap.put(containerPath, hostPath);
          }
        }
      }

      // 获取环境变量
      Map<String, String> environmentalVariableMap = new HashMap<>();
      String[] environmentalVariables = response.getConfig().getEnv();
      if (environmentalVariables != null) {
        Map<String, String> map = Arrays.stream(environmentalVariables)
            .filter(evn -> evn.contains("="))
            .map(evn -> {
              int i = evn.indexOf("=");
              return Tuple.of(evn.substring(0, i), evn.substring(i + 1));
            })
            .collect(Collectors.toMap(
                Tuple2::_1,
                Tuple2::_2,
                (v1, v2) -> v2
            ));

        environmentalVariableMap.putAll(map);
      }

      response.getImageId();

      Container result = new Container();
      result.setId(response.getId());
      result.setIp(ip);
      result.setName(response.getName().substring(1));
      result.setImage(containers.isEmpty() ? response.getConfig().getImage() : containers.get(0).getImage());
      result.setPortBindings(portBindingMap);
      result.setVolumeBindings(volumeBindingMap);
      result.setEnvironmentalVariables(environmentalVariableMap);
      result.setLabels(response.getConfig().getLabels());
      result.setNetwork(network);
      result.setStatus(statusConvert(response.getState().getStatus()));
      if (response.getState().getHealth() != null && !"healthy".equals(response.getState().getHealth().getStatus())
          && result.getStatus() == ContainerStatus.RUNNING) result.setStatus(ContainerStatus.LAUNCHING);

      return Either.right(result);
    } catch (Exception e) {
      return Either.left(e);
    }
  }

  @Override
  public Either<Exception, Container> getContainerInfoByName(String name) {
    return getContainerInfoById(name);
  }

  @Override
  public Either<Exception, List<Container>> getAllContainerInfo() {
    return searchContainerInfo(null);
  }

  @Override
  public Either<Exception, List<Container>> searchContainerInfo(Map<String, Collection<String>> filterMap) {
    ListContainersCmd containersCmd = getDockerClient().listContainersCmd().withShowAll(true);
    if (ObjectUtils.isNotEmpty(filterMap)) {
      filterMap.forEach(containersCmd::withFilter);
    }

    List<com.github.dockerjava.api.model.Container> dockerContainers = containersCmd.exec();

    List<Container> containers = dockerContainers.stream()
        .map(dockerContainer -> {
          // 获取容器IP
          ContainerNetworkSettings networkSettings = dockerContainer.getNetworkSettings();
          String ip = networkSettings != null ? getContainerIp(networkSettings.getNetworks()) : null;
          String network = networkSettings != null && networkSettings.getNetworks() != null && !networkSettings.getNetworks().isEmpty()
              ? networkSettings.getNetworks().keySet().iterator().next()
              : null;

          // 构建端口映射
          Map<String, Map<Integer, Integer>> portBindings = new HashMap<>();
          for (ContainerPort port : dockerContainer.ports) {
            String protocol = port.getType();
            Integer containerPort = port.getPrivatePort();
            Integer hostPort = port.getPublicPort();

            if (hostPort == null) {
              continue;
            }

            portBindings.computeIfAbsent(protocol, k -> new HashMap<>()).put(containerPort, hostPort);
          }

          // 构建卷映射信息
          Map<String, String> volumeBindings = dockerContainer.getMounts()
              .stream()
              .collect(Collectors.toMap(
                  ContainerMount::getDestination,
                  (cm) -> String.valueOf(cm.getSource()),
                  (v1, v2) -> v2));

          Container container = new Container();
          container.setId(dockerContainer.getId());
          container.setIp(ip);
          container.setName(dockerContainer.getNames()[0].substring(1));
          container.setImage(dockerContainer.getImage());
          container.setStatus(statusConvert(dockerContainer.getState()));
          container.setPortBindings(portBindings);
          container.setVolumeBindings(volumeBindings);
          container.setNetwork(network);
          container.setLabels(dockerContainer.getLabels());

          return container;
        })
        .collect(Collectors.toList());

    return Either.right(containers);
  }

  @Override
  public Either<Exception, List<String>> getAllImageNames(String reference) {
    return Try.of(() -> getDockerClient().listImagesCmd()
            .withFilter("reference", Arrays.asList(reference, "*/" + reference, "*/*/" + reference))
            .exec()
            .stream()
            .filter(d -> Optional.ofNullable(d.getRepoTags()).map(tags -> tags.length > 0).orElse(false))
            .flatMap(d -> Arrays.stream(d.getRepoTags()))
            .collect(Collectors.toList()))
        .toEither()
        .mapLeft(DockerContainerServiceImpl::toException);
  }

  @Override
  public Either<Exception, List<String>> getAllTaggedImageNames() {
    return Try.of(() -> getDockerClient().listImagesCmd()
            .exec()
            .stream()
            .filter(image -> Optional.ofNullable(image.getRepoTags()).map(tags -> tags.length > 0).orElse(false))
            .flatMap(image -> Arrays.stream(image.getRepoTags()))
            .filter(this::isTaggedImageName)
            .collect(Collectors.toList()))
        .toEither()
        .mapLeft(DockerContainerServiceImpl::toException);
  }

  @Override
  public Either<Exception, List<String>> getLogs(String id, Integer tailLines) {
    try (LogContainerCmd cmd = getDockerClient().logContainerCmd(id)) {
      List<String> logs = new ArrayList<String>();
      StringBuilder pending = new StringBuilder();
      cmd.withStdOut(true).withStdErr(true);
      if (tailLines != null) {
        cmd.withTail(tailLines);
      }
      ResultCallback.Adapter<Frame> callback = new ResultCallback.Adapter<Frame>() {
        @Override
        public void onNext(Frame frame) {
          collectLogLines(logs, pending, new String(frame.getPayload(), StandardCharsets.UTF_8));
        }
      };
      cmd.exec(callback).awaitCompletion();
      if (pending.length() > 0) {
        logs.add(pending.toString());
      }
      return Either.right(logs);
    } catch (Exception e) {
      return Either.left(e);
    }
  }

  @Override
  public CompletableFuture<Void> pullImageAsync(String imageName) {
    if (StringUtils.isBlank(imageName)) {
      throw new IllegalArgumentException("镜像名不得为空");
    }
    var pullImageCmd = Try.of(() -> getDockerClient()
        .pullImageCmd(imageName));
    if (pullImageCmd.isFailure()) {
      throw new IllegalStateException("拉取镜像的时候发生错误", pullImageCmd.getCause());
    }
    var result = new CompletableFuture<Void>();
    var callback = new PullImageResultCallback() {
      @Override
      public void onComplete() {
        super.onComplete();
        result.complete(null);
      }

      @Override
      public void onError(Throwable throwable) {
        result.completeExceptionally(new RuntimeException("拉取镜像的时候发生错误", throwable));
      }
    };
    pullImageCmd.get().exec(callback);
    return result;
  }

  private void collectLogLines(List<String> logs, StringBuilder pending, String payload) {
    if (payload == null || payload.isEmpty()) {
      return;
    }
    String normalized = payload.replace("\r\n", "\n").replace('\r', '\n');
    int start = 0;
    for (int index = 0; index < normalized.length(); index++) {
      if (normalized.charAt(index) != '\n') {
        continue;
      }
      pending.append(normalized, start, index);
      logs.add(pending.toString());
      pending.setLength(0);
      start = index + 1;
    }
    if (start < normalized.length()) {
      pending.append(normalized.substring(start));
    }
  }

  /**
   * 启用事件监听器
   *
   * @return
   */
  private Try<ResultCallback.Adapter<Event>> enableEventListener() {
    Map<String, ContainerStatus> statusMap = new TreeMap<>(String::compareToIgnoreCase);
//    statusMap.put("restart", ContainerStatus.LAUNCHING);
    statusMap.put("start", ContainerStatus.RUNNING);
    statusMap.put("unpause", ContainerStatus.RUNNING);
    statusMap.put("pause", ContainerStatus.PAUSED);
    statusMap.put("die", ContainerStatus.STOPPED);
    statusMap.put("destroy", ContainerStatus.NOT_FOUND);

    var dockerEventHandler = new ResultCallback.Adapter<Event>() {
      @Override
      public void onNext(Event event) {
        if (!Objects.equals(event.getType(), EventType.CONTAINER)) {
          return;
        }
        if (event.getAction() == null) {
          return;
        }

        List<Tuple2<ContainerEventType, EventObject>> events = new ArrayList<>();
        Map<String, Object> attributes = new HashMap<>();

        String originAction = event.getAction().toLowerCase();
        if (Objects.equals(originAction, "create")) {
          var eventObject = new SimpleEventObject(event.getId(), ContainerStatus.LAUNCHING, attributes);
          events.add(Tuple.of(ContainerEventType.CONTAINER_CREATE, eventObject));
        } else if (Objects.equals(originAction, "destroy")) {
          var eventObject = new SimpleEventObject(event.getId(), ContainerStatus.NOT_FOUND, attributes);
          events.add(Tuple.of(ContainerEventType.CONTAINER_DELETE, eventObject));
        }

        var newStatus = statusMap.get(originAction);
        if (newStatus != null) {
          var eventObject = new SimpleEventObject(event.getId(), newStatus, attributes);
          events.add(Tuple.of(ContainerEventType.CONTAINER_STATUS_CHANGE, eventObject));
        }
        if (events.isEmpty()) {
          return;
        }

        if (event.getActor() != null && event.getActor().getAttributes() != null) {
          attributes.putAll(event.getActor().getAttributes());
        }

        dockerEventListeners.stream().forEach(listener ->
            events.forEach(tuple ->
                Try.run(() -> listener.onEvent(tuple._1, tuple._2))
            )
        );
      }
    };

    var listenDockerClient = Try.of(() -> getDockerClient().eventsCmd()
        .withEventTypeFilter(EventType.CONTAINER)
        .withLabelFilter("OUROBOROS_APP_NAME")
        .exec(dockerEventHandler)
        .awaitStarted());

    return listenDockerClient;
  }

  /**
   * 获取容器IP
   *
   * @param networks
   * @return
   */
  private String getContainerIp(Map<String, ContainerNetwork> networks) {
    if (networks == null || networks.isEmpty()) {
      return null;
    }

    return networks.values().iterator().next().getIpAddress();
  }

  private boolean isTaggedImageName(String imageName) {
    if (StringUtils.isBlank(imageName) || imageName.contains("@")) {
      return false;
    }
    if (imageName.startsWith("<none>") || imageName.endsWith(":<none>")) {
      return false;
    }

    int tagSeparator = imageName.lastIndexOf(':');
    int pathSeparator = imageName.lastIndexOf('/');
    return tagSeparator > pathSeparator && tagSeparator < imageName.length() - 1;
  }

  /**
   * 获取DockerClient
   *
   * @return
   */
  private DockerClient getDockerClient() {
    if (dockerClient == null) {
      try {
        dockerClient = initDockerClient();
      } catch (Exception e) {
        throw new IllegalStateException("docker initialization failed.", e);
      }
    }

    return dockerClient;
  }

  private static Exception toException(Throwable throwable) {
    if (throwable instanceof Exception exception) {
      return exception;
    }
    return new RuntimeException(throwable);
  }

  /**
   * 初始化DockerClient
   *
   * @return
   */
  private DockerClient initDockerClient() {
    DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
        .withDockerHost(dockerConfiguration.getHost())
        .withRegistryUsername(dockerConfiguration.getUsername())
        .withRegistryPassword(dockerConfiguration.getPassword())
        .build();

    var dockerHttpClient = new ApacheDockerHttpClient.Builder()
        .dockerHost(config.getDockerHost())
        .sslConfig(config.getSSLConfig())
        .connectionTimeout(Duration.ofSeconds(30))
        .build();
    DockerClient dockerClient = DockerClientBuilder.getInstance(config)
        .withDockerHttpClient(dockerHttpClient)
        .build();
    Info info = dockerClient.infoCmd().exec();

    logger.debug("docker initialization success.");

    return dockerClient;
  }

  /**
   * 状态转换
   *
   * @param status
   * @return
   */
  private ContainerStatus statusConvert(String status) {
    if (StringUtils.isEmpty(status)) {
      return ContainerStatus.UNKNOWN;
    }

    var lowerStatus = status.toLowerCase();

    switch (lowerStatus) {
      case "created", "exited" -> {
        return ContainerStatus.STOPPED;
      }

      case "running" -> {
        return ContainerStatus.RUNNING;
      }

      case "paused" -> {
        return ContainerStatus.PAUSED;
      }

      default -> {
        if (lowerStatus.startsWith("up")) {
          return ContainerStatus.RUNNING;
        }
        if (lowerStatus.startsWith("exited")) {
          return ContainerStatus.STOPPED;
        }
        if (lowerStatus.startsWith("restarting")) {
          return ContainerStatus.LAUNCHING;
        }
        return ContainerStatus.UNKNOWN;
      }
    }
  }
}
