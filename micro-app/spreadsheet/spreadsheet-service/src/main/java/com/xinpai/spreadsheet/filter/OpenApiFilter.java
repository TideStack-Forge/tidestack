package com.xinpai.spreadsheet.filter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;

import com.xinpai.spreadsheet.service.AppService;
import com.xinpai.spreadsheet.utils.JsonUtil;
import com.xinpai.spreadsheet.utils.MyHttpServletRequestWrapper;
import com.xinpai.spreadsheet.utils.StringUtil;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.multipart.commons.CommonsMultipartResolver;

@WebFilter(urlPatterns = "/api/open/*")
public class OpenApiFilter extends OncePerRequestFilter {

  CommonsMultipartResolver multipartResolver = new CommonsMultipartResolver();

  @Autowired
  private AppService appService;

  @Override
  @SuppressWarnings("unchecked")
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    String appId = null;
    String appSecret = null;

//    HttpServletRequestWrapper requestWrapper = new HttpServletRequestWrapper(request);
    MyHttpServletRequestWrapper requestWrapper = new MyHttpServletRequestWrapper(request);

    if (requestWrapper.getContentType().contains("multipart/form-data")) {
      MultipartHttpServletRequest req = multipartResolver.resolveMultipart(requestWrapper);
      appId = req.getParameter("appId");
      appSecret = req.getParameter("appSecret");
    } else {
      // 从body中获取appId和appSecret
      BufferedReader reader = requestWrapper.getReader();
      StringBuilder sb = new StringBuilder();
      String line = null;
      while ((line = reader.readLine()) != null) {
        sb.append(line);
      }
      String body = sb.toString();
      if (StringUtil.isNullOrEmpty(body)) {
        Unauthorized(response, "body is empty");
        return;
      }
      HashMap<String, Object> bodyMap = JsonUtil.fromJson(body, HashMap.class);
      if (bodyMap == null) {
        Unauthorized(response, "body is invalid");
        return;
      }
      appId = (String) bodyMap.get("appId");
      appSecret = (String) bodyMap.get("appSecret");
    }

    if (StringUtil.isNullOrEmpty(appId) || StringUtil.isNullOrEmpty(appSecret)) {
      Unauthorized(response, "appId or appSecret is null");
      return;
    }

    if (!appService.validateSecret(appId, appSecret)) {
      Unauthorized(response, "app secret is invalid");
      return;
    }

    filterChain.doFilter(requestWrapper, response);
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
