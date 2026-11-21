package com.ouroboros.notification.dingtalk.adapter;

import com.ouroboros.notification.enterprise.EnterpriseNotificationChannelOptions;

/**
 * @author liansz
 */
public class DingTalkNotificationChannelOptions extends EnterpriseNotificationChannelOptions {

  /**
   * 应用id
   */
  private String agentId;

  /**
   * 状态提示消息
   */
  private String statusContent;

  /**
   * App跳转url
   */
  private String appSkipUrl;

  /**
   * PC端跳转url
   */
  private String pcSkipUrl;

  /**
   * 发送方
   */
  private String author;

  public String getAgentId() {
    return agentId;
  }

  public void setAgentId(String agentId) {
    this.agentId = agentId;
  }

  public String getStatusContent() {
    return statusContent;
  }

  public void setStatusContent(String statusContent) {
    this.statusContent = statusContent;
  }

  public String getAppSkipUrl() {
    return appSkipUrl;
  }

  public void setAppSkipUrl(String appSkipUrl) {
    this.appSkipUrl = appSkipUrl;
  }

  public String getPcSkipUrl() {
    return pcSkipUrl;
  }

  public void setPcSkipUrl(String pcSkipUrl) {
    this.pcSkipUrl = pcSkipUrl;
  }

  public String getAuthor() {
    return author;
  }

  public void setAuthor(String author) {
    this.author = author;
  }
}
