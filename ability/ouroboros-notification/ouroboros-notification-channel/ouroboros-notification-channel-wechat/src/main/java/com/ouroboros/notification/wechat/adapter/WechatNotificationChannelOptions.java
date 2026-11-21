package com.ouroboros.notification.wechat.adapter;

import java.util.HashMap;
import java.util.Map;

import com.ouroboros.notification.adapter.NotificationChannelOptions;
import com.ouroboros.notification.wechat.pojo.vo.Miniprogram;

/**
 * @author liansz
 */
public class WechatNotificationChannelOptions implements NotificationChannelOptions {

  /**
   * 模板跳转链接（海外帐号没有跳转能力）
   */
  private String url;

  /**
   * 跳小程序所需数据，不需跳小程序可不用传该数据
   */
  private Miniprogram miniprogram;

  /**
   * 模板数据颜色
   */
  private Map<String, String> payloadColor = new HashMap<>();

  /**
   * 防重入id。对于同一个openid + client_msg_id, 只发送一条消息,10分钟有效,超过10分钟不保证效果。若无防重入需求，可不填
   */
  private String clientMsgId;

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public Miniprogram getMiniprogram() {
    return miniprogram;
  }

  public void setMiniprogram(Miniprogram miniprogram) {
    this.miniprogram = miniprogram;
  }

  public Map<String, String> getPayloadColor() {
    return payloadColor;
  }

  public void setPayloadColor(Map<String, String> payloadColor) {
    this.payloadColor = payloadColor;
  }

  public String getClientMsgId() {
    return clientMsgId;
  }

  public void setClientMsgId(String clientMsgId) {
    this.clientMsgId = clientMsgId;
  }
}
