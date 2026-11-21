package com.ouroboros.notification.aliyun.call.service.model;

public class AliyunCallResult {

  /**
   * 主叫号码
   */
  private String caller;

  /**
   * 通话开始时间
   */
  private String startDate;

  /**
   * 通话状态描述
   */
  private String stateDesc;

  /**
   * 通话时长，单位：秒
   */
  private Long duration;

  /**
   * 被叫显号
   */
  private String callerShowNumber;

  /**
   * 通话请求的接收时间
   */
  private String gmtCreate;

  /**
   * 通话状态
   */
  private String state;

  /**
   * 通话结束时间
   */
  private String endDate;

  /**
   * 被叫显号
   */
  private String calleeShowNumber;

  /**
   * a 路响铃开始时间
   */
  private String aRingTime;

  /**
   * a 路响铃结束时间
   */
  private String aEndTime;

  /**
   * b 路响铃开始时间
   */
  private String bRingTime;

  /**
   * b 路响铃结束时间
   */
  private String bEndTime;

  /**
   * 被叫号码
   */
  private String callee;

  public String getCaller() {
    return caller;
  }

  public void setCaller(String caller) {
    this.caller = caller;
  }

  public String getStartDate() {
    return startDate;
  }

  public void setStartDate(String startDate) {
    this.startDate = startDate;
  }

  public String getStateDesc() {
    return stateDesc;
  }

  public void setStateDesc(String stateDesc) {
    this.stateDesc = stateDesc;
  }

  public Long getDuration() {
    return duration;
  }

  public void setDuration(Long duration) {
    this.duration = duration;
  }

  public String getCallerShowNumber() {
    return callerShowNumber;
  }

  public void setCallerShowNumber(String callerShowNumber) {
    this.callerShowNumber = callerShowNumber;
  }

  public String getGmtCreate() {
    return gmtCreate;
  }

  public void setGmtCreate(String gmtCreate) {
    this.gmtCreate = gmtCreate;
  }

  public String getState() {
    return state;
  }

  public void setState(String state) {
    this.state = state;
  }

  public String getEndDate() {
    return endDate;
  }

  public void setEndDate(String endDate) {
    this.endDate = endDate;
  }

  public String getCalleeShowNumber() {
    return calleeShowNumber;
  }

  public void setCalleeShowNumber(String calleeShowNumber) {
    this.calleeShowNumber = calleeShowNumber;
  }

  public String getaRingTime() {
    return aRingTime;
  }

  public void setaRingTime(String aRingTime) {
    this.aRingTime = aRingTime;
  }

  public String getaEndTime() {
    return aEndTime;
  }

  public void setaEndTime(String aEndTime) {
    this.aEndTime = aEndTime;
  }

  public String getbRingTime() {
    return bRingTime;
  }

  public void setbRingTime(String bRingTime) {
    this.bRingTime = bRingTime;
  }

  public String getbEndTime() {
    return bEndTime;
  }

  public void setbEndTime(String bEndTime) {
    this.bEndTime = bEndTime;
  }

  public String getCallee() {
    return callee;
  }

  public void setCallee(String callee) {
    this.callee = callee;
  }
}
