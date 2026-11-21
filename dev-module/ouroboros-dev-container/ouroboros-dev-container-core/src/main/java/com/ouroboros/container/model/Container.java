package com.ouroboros.container.model;

import java.util.Map;

import com.ouroboros.container.ContainerStatus;

/**
 * @author liansz
 */
public class Container {

  /**
   * 容器 ID
   */
  private String id;

  /**
   * 容器的 IP 地址
   */
  private String ip;

  /**
   * 容器的名称
   */
  private String name;

  /**
   * 镜像名称
   */
  private String image;

  /**
   * 容器端口映射信息
   * </p>
   * Map<协议, Map<容器端口, 宿主机端口>>
   */
  private Map<String, Map<Integer, Integer>> portBindings;

  /**
   * 容器宿主机目录映射信息
   * </p>
   * Map<容器目录, 宿主机目录>
   */
  private Map<String, String> volumeBindings;

  /**
   * 环境变量
   */
  private Map<String, String> environmentalVariables;

  /**
   * 容器标签信息
   */
  private Map<String, String> labels;

  private boolean autoRemove;
  public boolean isAutoRemove() { return autoRemove; }
  public void setAutoRemove(boolean value) { autoRemove = value; }

  private String portBindAddress;
  public String getPortBindAddress() { return portBindAddress; }
  public void setPortBindAddress(String value) { portBindAddress = value; }
  private Long memoryLimitBytes;
  private Long nanoCpus;
  public Long getMemoryLimitBytes() { return memoryLimitBytes; }
  public void setMemoryLimitBytes(Long value) { memoryLimitBytes = value; }
  public Long getNanoCpus() { return nanoCpus; }
  public void setNanoCpus(Long value) { nanoCpus = value; }

  private String network = "";

  /**
   * 容器状态
   */
  private ContainerStatus status;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getIp() {
    return ip;
  }

  public void setIp(String ip) {
    this.ip = ip;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getImage() {
    return image;
  }

  public void setImage(String image) {
    this.image = image;
  }

  public Map<String, Map<Integer, Integer>> getPortBindings() {
    return portBindings;
  }

  public void setPortBindings(Map<String, Map<Integer, Integer>> portBindings) {
    this.portBindings = portBindings;
  }

  public Map<String, String> getVolumeBindings() {
    return volumeBindings;
  }

  public void setVolumeBindings(Map<String, String> volumeBindings) {
    this.volumeBindings = volumeBindings;
  }

  public Map<String, String> getEnvironmentalVariables() {
    return environmentalVariables;
  }

  public void setEnvironmentalVariables(Map<String, String> environmentalVariables) {
    this.environmentalVariables = environmentalVariables;
  }

  public Map<String, String> getLabels() {
    return labels;
  }

  public void setLabels(Map<String, String> labels) {
    this.labels = labels;
  }

  public ContainerStatus getStatus() {
    return status;
  }

  public void setStatus(ContainerStatus status) {
    this.status = status;
  }

  public String getNetwork() {
    return network;
  }

  public void setNetwork(String network) {
    this.network = network;
  }
}
