package com.ouroboros.rpc.http;

public class HttpRPCRequestParameterDefine {

  public enum InScope {
    PATH("path"),
    QUERY("query"),
    HEADER("header"),
    COOKIE("cookie"),
    // 为了简化，现在先把body也放在这里
    BODY("body");
    private final String value;

    InScope(String value) {
      this.value = value;
    }

    public String getValue() {
      return value;
    }

    public static InScope fromValue(String value) {
      for (InScope inScope : InScope.values()) {
        if (inScope.getValue().equalsIgnoreCase(value)) {
          return inScope;
        }
      }
      return null;
    }
  }

  /**
   * 参数名称
   */
  private String name;

  /**
   * 参数位置
   */
  private InScope in;

  /**
   * 值
   */
  private String value;

  /**
   * 是否必填
   */
  private boolean required;

  public HttpRPCRequestParameterDefine() {
  }

  public HttpRPCRequestParameterDefine(HttpRPCRequestParameterDefine parameter) {
    this.name = parameter.getName();
    this.in = parameter.getIn();
    this.value = parameter.getValue();
    this.required = parameter.isRequired();
  }


  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public InScope getIn() {
    return in;
  }

  public void setIn(InScope in) {
    this.in = in;
  }

  public void setIn(String in) {
    this.in = InScope.fromValue(in);
  }

  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = value;
  }

  public boolean isRequired() {
    return required;
  }

  public void setRequired(boolean required) {
    this.required = required;
  }
}
