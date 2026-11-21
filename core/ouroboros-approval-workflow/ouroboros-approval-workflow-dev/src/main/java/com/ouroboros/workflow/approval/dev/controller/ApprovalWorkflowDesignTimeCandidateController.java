package com.ouroboros.workflow.approval.dev.controller;

import java.util.List;

import org.springframework.web.bind.annotation.*;

import com.ouroboros.web.ItemsWebApiResult;
import com.ouroboros.workflow.approval.CandidateProvider;
import com.ouroboros.workflow.approval.dev.candidate.ApprovalWorkflowDesignTimeCandidateRegistry;
import com.ouroboros.workflow.approval.dev.candidate.DesignTimeCandidateSource;

@RestController
@RequestMapping("/api/dev/{appName}/approval-workflow/candidate")
public class ApprovalWorkflowDesignTimeCandidateController {
  private final ApprovalWorkflowDesignTimeCandidateRegistry candidateRegistry;

  public ApprovalWorkflowDesignTimeCandidateController(ApprovalWorkflowDesignTimeCandidateRegistry candidateRegistry) {
    this.candidateRegistry = candidateRegistry;
  }

  @GetMapping("")
  public ItemsWebApiResult<DesignTimeCandidateSource> getCandidateTypes(@PathVariable("appName") String appName) {
    return ItemsWebApiResult.ok(candidateRegistry.getCandidateTypes(appName));
  }

  @GetMapping("/{type}/items")
  public ItemsWebApiResult<CandidateProvider.CandidateItem> searchItems(
      @PathVariable("appName") String appName,
      @PathVariable String type,
      @RequestParam(required = false) String category,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false, name = "value[]") List<String> values,
      @RequestParam(required = false, name = "value") List<String> value) {
    List<String> selectedValues = values == null ? value : values;
    return ItemsWebApiResult.ok(candidateRegistry.searchItems(appName, type, category, keyword, selectedValues));
  }

  @GetMapping("/{type}/category")
  public ItemsWebApiResult<CandidateProvider.CandidateCategory> getCategories(
      @PathVariable("appName") String appName,
      @PathVariable String type) {
    return ItemsWebApiResult.ok(candidateRegistry.getCategories(appName, type));
  }
}
