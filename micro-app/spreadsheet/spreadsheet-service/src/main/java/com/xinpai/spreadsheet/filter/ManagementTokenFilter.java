/*
 * @Author: Song Mingxu
 * @Date: 2022-05-12 15:06:41
 * @LastEditors: Song Mingxu
 * @LastEditTime: 2022-05-15 01:30:55
 * @Description: file content
 */
package com.xinpai.spreadsheet.filter;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.xinpai.spreadsheet.service.TokenService;
import com.xinpai.spreadsheet.utils.JsonUtil;
import com.xinpai.spreadsheet.utils.StringUtil;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.filter.OncePerRequestFilter;

@WebFilter(urlPatterns = "/api/management/*")
public class ManagementTokenFilter extends OncePerRequestFilter {

  @Autowired
  TokenService tokenService;

  private void Unauthorized(HttpServletResponse response, String msg) {
    response.setStatus(401);
    try {
      OutputStream stream = response.getOutputStream();
      String body = JsonUtil.toJson(new HashMap<String, Object>() {
        {
          put("code", 401);
          put("msg", msg);
        }
      });
      stream.write(body.getBytes());
      stream.flush();
    } catch (IOException e) {
      return;
    }
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    String uri = request.getRequestURI();
    String token = request.getHeader("token");
    if (StringUtil.isNullOrEmpty(token)) {
      token = request.getParameter("token");
    }

    if (uri.contains("/api/management/login")) {
      filterChain.doFilter(request, response);
      return;
    }
    if (StringUtil.isNullOrEmpty(token)) {
      Unauthorized(response, "token must be provided");
      return;
    }

    if (!tokenService.checkToken(token)) {
      Unauthorized(response, "token is invalid");
      return;
    }
    tokenService.extendTokenExpireTime(token, 10, TimeUnit.MINUTES);
    request.setAttribute("token", token);
    request.setAttribute("tokenPayload", tokenService.getPayload(token));
    filterChain.doFilter(request, response);
  }

}
