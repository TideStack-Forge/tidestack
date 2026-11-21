package com.ouroboros.businessmodel.action;

import java.util.List;
import java.util.Map;

import com.ouroboros.web.WebApiResult;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@org.springframework.boot.autoconfigure.condition.ConditionalOnBean(BusinessActionRuntime.class)
@RequestMapping("/api/business-model-behaviors/{modelFullName}/actions")
public class BusinessActionDescriptionController {
  private final BusinessActionRuntime runtime;

  public BusinessActionDescriptionController(BusinessActionRuntime runtime) {
    this.runtime = runtime;
  }

  public record ActionInput(Map<String, Object> input, String expectedContractFingerprint) {
    BusinessActionInvocation invocation(String modelFullName, String actionName) {
      return new BusinessActionInvocation(modelFullName, actionName,
          BusinessActionInputs.fromPartitions(input == null ? Map.of() : input), expectedContractFingerprint);
    }
  }

  @PostMapping("/{actionName}/invoke")
  public Object invoke(@PathVariable("modelFullName") String modelFullName,
      @PathVariable("actionName") String actionName, @RequestBody ActionInput request,
      jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
    Object result = runtime.invoke(request.invocation(modelFullName, actionName));
    if (result instanceof com.ouroboros.util.FileResult file) {
      com.ouroboros.web.FileResponses.write(file, response);
      return null;
    }
    return com.ouroboros.web.WebApiResult.ok(result);
  }

  @GetMapping
  public WebApiResult<List<BusinessActionDescription>> describe(@PathVariable("modelFullName") String modelFullName) {
    return WebApiResult.ok(runtime.describe(modelFullName));
  }

  @PostMapping("/{actionName}/availability")
  public WebApiResult<BusinessActionAvailability> availability(@PathVariable("modelFullName") String modelFullName,
      @PathVariable("actionName") String actionName, @RequestBody ActionInput request) {
    return WebApiResult.ok(runtime.checkAvailability(request.invocation(modelFullName, actionName)));
  }
}
