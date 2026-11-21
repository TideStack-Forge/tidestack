package com.ouroboros.workflow.approval.web.vo;

public class TaskHistoryResult {
  String time;
  String title;
  String detail;
  String color;

  public TaskHistoryResult() {
  }

  public TaskHistoryResult(String time, String title, String detail, String color) {
    this.time = time;
    this.title = title;
    this.detail = detail;
    this.color = color;
  }

  public String getTime() {
    return time;
  }

  public void setTime(String time) {
    this.time = time;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getDetail() {
    return detail;
  }

  public void setDetail(String detail) {
    this.detail = detail;
  }

  public String getColor() {
    return color;
  }

  public void setColor(String color) {
    this.color = color;
  }
}
