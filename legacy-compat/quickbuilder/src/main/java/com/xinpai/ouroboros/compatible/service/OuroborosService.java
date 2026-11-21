package com.xinpai.ouroboros.compatible.service;

import com.xinpai.common.model.AccessToken;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;

/**
 * @author liansz
 * @createTime 2023/9/28 10:06
 */
public interface OuroborosService {

  /**
   * 获取WEB应用信息
   *
   * @return
   */
  Map<String, Object> getWebAppInfo();

  /**
   * 获取当前用户的 AccessToken
   *
   * @param request
   * @param response
   * @return
   */
  AccessToken getAccessToken(HttpServletRequest request, HttpServletResponse response) throws Exception;

  /**
   * 按用户id查询授权的菜单树
   *
   * @param userId
   * @return
   */
  Map<String, List<Map<String, Object>>> getAuthoredMenuTreeByUserId(String userId);

  /**
   * 按用户id查询通知未读数
   *
   * @param userId
   * @return
   */
  Map<String, Object> getNotificationUnreadCountByUserId(String userId);
}
