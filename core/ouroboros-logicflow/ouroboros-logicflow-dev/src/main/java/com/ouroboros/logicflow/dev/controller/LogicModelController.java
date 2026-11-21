package com.ouroboros.logicflow.dev.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ouroboros.logicflow.dev.service.impl.LogicModelDevelopServiceImpl;
import com.ouroboros.vcs.versioning.ChangeLog;
import com.ouroboros.web.ItemsWebApiResult;

/**
 * @author liansz
 */
@RestController
@RequestMapping("/api/dev/{appName}/logic-flow")
@SuppressWarnings("unused")
public class LogicModelController {

  @Autowired
  LogicModelDevelopServiceImpl service;

  @GetMapping("/{fullName}/change-log")
  public ItemsWebApiResult<ChangeLog> getChangeLog(@PathVariable String fullName) {
    var changeLogList = service.getAllChangeLogs(fullName);
    return ItemsWebApiResult.ok(changeLogList);
  }
}
