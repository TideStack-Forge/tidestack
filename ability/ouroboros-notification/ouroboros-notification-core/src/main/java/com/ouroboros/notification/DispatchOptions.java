package com.ouroboros.notification;

import java.util.Collection;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.ouroboros.notification.adapter.NotificationChannelOptions;
import com.ouroboros.notification.serializer.ChannelOptionsDeserializer;

/**
 * @author liansz
 */
public class DispatchOptions {

  /**
   * 发送渠道(默认为模版定义全部渠道)
   */
  private Collection<String> channels;

  /**
   * 渠道参数
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonDeserialize(using = ChannelOptionsDeserializer.class)
  private Map<String, NotificationChannelOptions> channelOptions;

  /**
   * 发送人
   */
  private String sendByEmployee;

  /**
   * 调用方业务幂等键。渠道适配器必须将其透传给支持幂等的外部服务。
   */
  private String idempotencyKey;

  public Collection<String> getChannels() {
    return channels;
  }

  public void setChannels(Collection<String> channels) {
    this.channels = channels;
  }

  public Map<String, NotificationChannelOptions> getChannelOptions() {
    return channelOptions;
  }

  public void setChannelOptions(Map<String, NotificationChannelOptions> channelOptions) {
    this.channelOptions = channelOptions;
  }

  public String getSendByEmployee() {
    return sendByEmployee;
  }

  public void setSendByEmployee(String sendByEmployee) {
    this.sendByEmployee = sendByEmployee;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public void setIdempotencyKey(String idempotencyKey) {
    this.idempotencyKey = idempotencyKey;
  }
}
