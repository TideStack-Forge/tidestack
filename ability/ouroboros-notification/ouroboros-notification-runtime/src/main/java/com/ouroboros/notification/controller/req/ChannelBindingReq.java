package com.ouroboros.notification.controller.req;

/**
 * @author liansz
 */
public class ChannelBindingReq {

  /**
   * 地址
   */
  private String address;

  /**
   * 备注
   */
  private String remarks;

  public String getAddress() {
    return address;
  }

  public void setAddress(String address) {
    this.address = address;
  }

  public String getRemarks() {
    return remarks;
  }

  public void setRemarks(String remarks) {
    this.remarks = remarks;
  }
}
