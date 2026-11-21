package com.ouroboros.notification.wechat.pojo.vo;

import java.util.Map;

/**
 * 微信模板消息请求body
 *
 * @author: sjh
 */
public class MessageBody {

  /**
   * 必填
   * 接收者openid
   */
  private String touser;

  /**
   * 必填
   * 模板ID
   */
  private String template_id;

  /**
   * 非必填
   * 模板跳转链接（海外帐号没有跳转能力）
   */
  private String url;

  /**
   * 非必填
   * 跳小程序所需数据，不需跳小程序可不用传该数据
   */
  private Miniprogram miniprogram;

  /**
   * 必填
   * 模板数据
   */
  private Map<String, Map<String, Object>> data;

  /**
   * 非必填
   * 防重入id。对于同一个openid + client_msg_id, 只发送一条消息,10分钟有效,超过10分钟不保证效果。若无防重入需求，可不填
   */
  private String client_msg_id;

  public String getTouser() {
    return touser;
  }

  public void setTouser(String touser) {
    this.touser = touser;
  }

  public String getTemplate_id() {
    return template_id;
  }

  public void setTemplate_id(String template_id) {
    this.template_id = template_id;
  }

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

  public Map<String, Map<String, Object>> getData() {
    return data;
  }

  public void setData(Map<String, Map<String, Object>> data) {
    this.data = data;
  }

  public String getClient_msg_id() {
    return client_msg_id;
  }

  public void setClient_msg_id(String client_msg_id) {
    this.client_msg_id = client_msg_id;
  }

}
