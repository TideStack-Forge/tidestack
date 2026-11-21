package com.xinpai.spreadsheet.service;

import java.util.concurrent.TimeUnit;

import com.xinpai.spreadsheet.dto.AccessTokenPayload;
import com.xinpai.spreadsheet.utils.SnowFlake;
import com.xinpai.spreadsheet.utils.StringUtil;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TokenService {
  @Value("${spreadsheet-service.accessTokenExpire.editToken:3}")
  private Integer editTokenExpire;

  @Value("${spreadsheet-service.accessTokenExpire.readToken:15}")
  private Integer readTokenExpire;

  @Autowired
  RedisCacheService redisCacheService;

  @Autowired
  SnowFlake snowFlake;

  /**
   * 生成管理token
   * 
   * @param payload
   * @param expireTime
   * @return
   */
  public String generateManagementToken(Object payload, long expireTime) {
    String token = "mt_" + snowFlake.nextIdHex();
    redisCacheService.addCache(token, payload, expireTime);
    return token;
  }

  /**
   * 生成管理token 并设置过期时间为10分钟
   * 
   * @param payload
   * @return
   */
  public String generateManagementToken(Object payload) {
    return generateManagementToken(payload, 10);
  }

  /**
   * 延长token的有效期
   * 
   * @param token
   * @param expireTime
   * @param timeUnit
   */
  public void extendTokenExpireTime(String token, long expireTime, TimeUnit timeUnit) {
    if (getPayload(token) == null)
      return;
    redisCacheService.updateExpireTime(token, expireTime, timeUnit);
  }

  /**
   * 生成编辑token
   * 
   * @param payload
   * @param expireTime
   * @return
   */
  public String generateAccessToken(AccessTokenPayload payload, long expireTime) {
    String token = "et_" + snowFlake.nextIdHex();
    redisCacheService.addCache(token, payload, expireTime);
    return token;
  }

  /**
   * 生成编辑token 并设置过期时间为3分钟
   * 
   * @param payload
   * @return
   */
  public String generateAccessToken(AccessTokenPayload payload) {
    return generateAccessToken(payload, payload.getReadonly() ? readTokenExpire : editTokenExpire);
  }

  /**
   * 延长编辑token的有效期
   * 
   * @param token
   */
  public void extendEditTokenExpireTime(String token) {
    extendTokenExpireTime(token, editTokenExpire, TimeUnit.MINUTES);
  }

  /**
   * 延长只读token的有效期
   *
   * @param token
   */
  public void extendReadTokenExpireTime(String token) {
    extendTokenExpireTime(token, readTokenExpire, TimeUnit.MINUTES);
  }

  /**
   * 获取token的payload
   * 
   * @param token
   * @return
   */
  public Object getPayload(String token) {
    if (StringUtil.isNullOrEmpty(token)) {
      return null;
    }
    try {
      return redisCacheService.getCache(token);
    } catch (Exception e) {
      return null;
    }
  }

  /**
   * 检查token是否有效
   * 
   * @param token
   * @return true:有效 false:无效
   */
  public Boolean checkToken(String token) {
    return getPayload(token) != null;
  }

  /**
   * 删除token
   * 
   * @param token
   */
  public void destroyToken(String token) {
    redisCacheService.deleteCacheByKey(token);
  }
}
