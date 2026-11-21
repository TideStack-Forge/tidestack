package com.ouroboros.dev.app.model;

import java.util.List;
import java.util.Map;

import com.ouroboros.container.ContainerStatus;
import com.ouroboros.model.AppInfo;

/**
 * @author liansz
 */
@SuppressWarnings("unused")
public class AppContainersDTO {

  /**
   * 应用名称
   */
  public String appName;

  /**
   * 应用标题
   */
  private String appTitle;

  /**
   * 应用描述
   */
  private String appDescription;

  /**
   * 容器列表
   */
  private List<Container> children;

  public AppContainersDTO() {
  }

  public AppContainersDTO(AppInfo appInfo, List<Container> children) {
    this.appName = appInfo.getName();
    this.appTitle = appInfo.getTitle();
    this.children = children;
  }

  public String getAppName() {
    return appName;
  }

  public void setAppName(String appName) {
    this.appName = appName;
  }

  public String getAppTitle() {
    return appTitle;
  }

  public void setAppTitle(String appTitle) {
    this.appTitle = appTitle;
  }

  public String getAppDescription() {
    return appDescription;
  }

  public void setAppDescription(String appDescription) {
    this.appDescription = appDescription;
  }

  public List<Container> getChildren() {
    return children;
  }

  public void setChildren(List<Container> children) {
    this.children = children;
  }

  public static class Container {

    /**
     * 容器 ID
     */
    private String id;

    /**
     * 容器的名称
     */
    private String name;

    /**
     * 镜像名称
     */
    private String image;

    /**
     * 镜像状态
     */
    private RuntimeImageViewDTO imageState;

    /**
     * 容器端口映射信息
     * </p>
     * Map<端口标识, 宿主机端口>
     */
    private Map<String, Integer> portBindings;

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

    /**
     * 容器状态
     */
    private ContainerStatus status;

    /**
     * profile
     */
    private String profile;

    /**
     * 运行时类型
     */
    private String runtimeKind;

    /**
     * 运行时代次
     */
    private Long runtimeGeneration;

    /**
     * 运行时后端类型
     */
    private String backendType;

    /**
     * 暴露策略
     */
    private String exposureStrategy;

    /**
     * Web 访问地址
     */
    private String webEndpoint;

    /**
     * API 访问地址
     */
    private String apiEndpoint;

    /**
     * Debug 访问地址
     */
    private String debugEndpoint;

    /**
     * 统一运行时访问视图
     */
    private RuntimeAccessViewDTO runtimeAccess;

    /**
     * 调试能力视图
     */
    private RuntimeAccessViewDTO.DebugCapabilityView debugCapability;

    /**
     * 当前激活的调试会话 ID
     */
    private String activeDebugSessionId;

    /**
     * 运行时能力标记
     */
    private Map<String, Boolean> capabilities;

    /**
     * 后端摘要
     */
    private String backendSummary;

    /**
     * 后端详情
     */
    private Map<String, String> backendDetails;

    public String getId() {
      return id;
    }

    public void setId(String id) {
      this.id = id;
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

    public RuntimeImageViewDTO getImageState() {
      return imageState;
    }

    public void setImageState(RuntimeImageViewDTO imageState) {
      this.imageState = imageState;
    }

    public Map<String, Integer> getPortBindings() {
      return portBindings;
    }

    public void setPortBindings(Map<String, Integer> portBindings) {
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

    public String getProfile() {
      return profile;
    }

    public void setProfile(String profile) {
      this.profile = profile;
    }

    public String getRuntimeKind() {
      return runtimeKind;
    }

    public void setRuntimeKind(String runtimeKind) {
      this.runtimeKind = runtimeKind;
    }

    public Long getRuntimeGeneration() {
      return runtimeGeneration;
    }

    public void setRuntimeGeneration(Long runtimeGeneration) {
      this.runtimeGeneration = runtimeGeneration;
    }

    public String getBackendType() {
      return backendType;
    }

    public void setBackendType(String backendType) {
      this.backendType = backendType;
    }

    public String getExposureStrategy() {
      return exposureStrategy;
    }

    public void setExposureStrategy(String exposureStrategy) {
      this.exposureStrategy = exposureStrategy;
    }

    public String getWebEndpoint() {
      return webEndpoint;
    }

    public void setWebEndpoint(String webEndpoint) {
      this.webEndpoint = webEndpoint;
    }

    public String getApiEndpoint() {
      return apiEndpoint;
    }

    public void setApiEndpoint(String apiEndpoint) {
      this.apiEndpoint = apiEndpoint;
    }

    public String getDebugEndpoint() {
      return debugEndpoint;
    }

    public void setDebugEndpoint(String debugEndpoint) {
      this.debugEndpoint = debugEndpoint;
    }

    public RuntimeAccessViewDTO getRuntimeAccess() {
      return runtimeAccess;
    }

    public void setRuntimeAccess(RuntimeAccessViewDTO runtimeAccess) {
      this.runtimeAccess = runtimeAccess;
    }

    public RuntimeAccessViewDTO.DebugCapabilityView getDebugCapability() {
      return debugCapability;
    }

    public void setDebugCapability(RuntimeAccessViewDTO.DebugCapabilityView debugCapability) {
      this.debugCapability = debugCapability;
    }

    public String getActiveDebugSessionId() {
      return activeDebugSessionId;
    }

    public void setActiveDebugSessionId(String activeDebugSessionId) {
      this.activeDebugSessionId = activeDebugSessionId;
    }

    public Map<String, Boolean> getCapabilities() {
      return capabilities;
    }

    public void setCapabilities(Map<String, Boolean> capabilities) {
      this.capabilities = capabilities;
    }

    public String getBackendSummary() {
      return backendSummary;
    }

    public void setBackendSummary(String backendSummary) {
      this.backendSummary = backendSummary;
    }

    public Map<String, String> getBackendDetails() {
      return backendDetails;
    }

    public void setBackendDetails(Map<String, String> backendDetails) {
      this.backendDetails = backendDetails;
    }

    public String getImageVersion() {
      var img = getImage();
      if (img == null || img.startsWith("sha256:")) {
        return null;
      }
      String[] parts = img.split(":");
      if (parts.length < 2) {
        return null;
      }
      return parts[parts.length - 1];
    }
  }
}
