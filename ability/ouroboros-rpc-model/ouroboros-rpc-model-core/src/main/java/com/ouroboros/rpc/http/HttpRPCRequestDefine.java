package com.ouroboros.rpc.http;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author liansz
 **/
public class HttpRPCRequestDefine {

  /**
   * 地址
   */
  private String url;

  /**
   * 方法
   */
  private String method;

  /**
   * 参数列表
   */
  private List<HttpRPCRequestParameterDefine> parameters = new ArrayList<>();

  public HttpRPCRequestDefine() {
  }

  public HttpRPCRequestDefine(HttpRPCRequestDefine request) {
    this.url = request.getUrl();
    this.method = request.getMethod();
    this.parameters = request.getParameters().stream()
        .map(HttpRPCRequestParameterDefine::new)
        .collect(Collectors.toList());
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public String getMethod() {
    return method;
  }

  public void setMethod(String method) {
    this.method = method;
  }

  public List<HttpRPCRequestParameterDefine> getParameters() {
    return parameters != null ? parameters : Collections.emptyList();
  }

  public void setParameters(List<HttpRPCRequestParameterDefine> parameters) {
    this.parameters = parameters;
  }
}
