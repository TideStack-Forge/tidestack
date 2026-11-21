package com.ouroboros.workflow.approval.manage.entity;

import com.ouroboros.data.annotation.Field;
import com.ouroboros.data.annotation.Model;
import com.ouroboros.data.annotation.PrimaryKey;

@Model(rawName = "t_approval_workflow_category", label = "审批流程分类")
public class WorkflowCategory {

  @Field
  @PrimaryKey
  private String name;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }
}
