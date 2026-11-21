package com.xinpai.spreadsheet.filter;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.xinpai.spreadsheet.dto.AccessTokenPayload;
import com.xinpai.spreadsheet.service.TokenService;
import com.xinpai.spreadsheet.utils.JsonUtil;
import com.xinpai.spreadsheet.utils.StringUtil;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.filter.OncePerRequestFilter;

@WebFilter(urlPatterns = "/api/spreadsheet/*")
public class AccessTokenFilter extends OncePerRequestFilter {

  @Autowired
  private TokenService tokenService;

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String token = request.getParameter("token");
    if (StringUtil.isNullOrEmpty(token)) {
      token = request.getHeader("token");
    }

    if (StringUtil.isNullOrEmpty(token)) {
      Unauthorized(response, "token is required");
      return;
    }

    Object payload = tokenService.getPayload(token);
    if (payload == null || !(payload instanceof AccessTokenPayload)) {
      Unauthorized(response, "token is invalid");
      return;
    }

    AccessTokenPayload accessTokenPayload = (AccessTokenPayload) payload;
    request.setAttribute("token", token);
    request.setAttribute("spreadsheetId", accessTokenPayload.getSpreadsheetId());
    request.setAttribute("appId", accessTokenPayload.getAppId());
    request.setAttribute("editorName", accessTokenPayload.getEditorName());
    request.setAttribute("readonly", accessTokenPayload.getReadonly());

    if (accessTokenPayload.getReadonly() != null || accessTokenPayload.getReadonly() == true) {
      tokenService.extendReadTokenExpireTime(token);
    } else {
      tokenService.extendEditTokenExpireTime(token);
    }

    filterChain.doFilter(request, response);
  }

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
}