package com.ouroboros.notification.aliyun.call.service;

import java.util.Map;

import com.ouroboros.notification.aliyun.call.adapter.AliyunCallNotificationChannelOptions;
import com.ouroboros.notification.aliyun.call.service.model.AliyunCallResult;

/**
 * @author liansz
 */
public interface AliyunCallService {

  /**
   * 发送语音通知
   *
   * @param mobile
   * @param templateCode
   * @param payload
   * @param channelOptions
   * @return 阿里云 callId
   */
  String sendCall(String mobile, String templateCode,
                  Map<String, Object> payload, AliyunCallNotificationChannelOptions channelOptions);

  /**
   * 查询呼叫详情
   *
   * @param callId    阿里云 callId
   * @param prodId    语音通知产品 ID
   * @param queryDate 查询日期
   * @return 呼叫详情
   */
  AliyunCallResult queryState(String callId, long prodId, long queryDate);
}
