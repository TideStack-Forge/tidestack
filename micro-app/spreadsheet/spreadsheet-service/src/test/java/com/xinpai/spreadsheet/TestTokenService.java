/*
 * @Author: Song Mingxu
 * @Date: 2022-05-13 15:13:11
 * @LastEditors: Song Mingxu
 * @LastEditTime: 2022-05-14 23:28:29
 * @Description: file content
 */
package com.xinpai.spreadsheet;

import static org.junit.Assert.assertFalse;

import com.xinpai.spreadsheet.dto.AccessTokenPayload;
import com.xinpai.spreadsheet.service.TokenService;
import com.xinpai.spreadsheet.utils.StringUtil;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@SpringBootTest
@RunWith(SpringRunner.class)
public class TestTokenService {

  @Autowired
  TokenService tokenService;

  @Test
  public void testAddToken() {
    AccessTokenPayload req = new AccessTokenPayload();
    req.setSpreadsheetId("455a50a20ea1000");
    req.setAppId("TEST_APP_ID");
    req.setEditorName("张三");
    String token = tokenService.generateAccessToken(req, 1000);
    assertFalse("生成Edit Token不能为空", StringUtil.isNullOrEmpty(token));
    System.out.println("Edit Token: " + token);
  }

  @Test
  public void testGetToken() {
    AccessTokenPayload req = new AccessTokenPayload();
    req.setSpreadsheetId("455a50a20ea1000");
    req.setAppId("TEST_APP_ID");
    req.setEditorName("张三");
    String token = tokenService.generateAccessToken(req, 1000);

    Object payload = tokenService.getPayload(token);
    Assert.assertNotNull("payload反序列化结果不能为空", payload);
  }
}