package com.ouroboros.webapi;

import java.util.Map;

import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import com.ouroboros.businessmodel.action.BusinessActionInvocation;
import com.ouroboros.businessmodel.action.BusinessActionRequestProvider;
import com.ouroboros.businessmodel.action.BusinessActionRuntime;
import com.ouroboros.businessmodel.action.PreparedBusinessActionInput;
import com.ouroboros.businessmodel.behavior.BusinessBehaviorContracts;
import com.ouroboros.businessmodel.behavior.BusinessModelBehaviorProvider;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.logicflow.adapter.RequestInfoMap;
import com.ouroboros.web.WebApiResult;

/** Adapts an explicitly declared BusinessAction route to the unified runtime. */
final class BusinessActionWebApiAdapter {
  private BusinessActionWebApiAdapter() {
  }

  static HandlerFunction<ServerResponse> buildHandler(WebAPIModel model, BusinessActionRuntime runtime,
                                                     BusinessModelBehaviorProvider behaviors) {
    BusinessActionWebApiContract.validate(model);
    String businessModel = model.getExtraProp(String.class, "businessModel")
        .filter(value -> !value.isBlank()).orElseThrow(() -> new IllegalArgumentException("BusinessAction route requires businessModel"));
    String action = model.getExtraProp(String.class, "action")
        .filter(value -> !value.isBlank()).orElseThrow(() -> new IllegalArgumentException("BusinessAction route requires action"));
    BusinessActionHttpInputPlan plan = new BusinessActionHttpInputPlan(model);
    // Route compilation participates in publication; execution remains behind the runtime's publication guard.
    String fingerprint = behaviors.getBusinessModelBehavior(businessModel)
        .map(behavior -> behavior.getActions().get(action))
        .map(BusinessBehaviorContracts::actionFingerprint)
        .orElseThrow(() -> new IllegalArgumentException("BusinessAction route refers to an unavailable Action"));
    return request -> {
      try {
        HttpBusinessActionInput input = new HttpBusinessActionInput(request, plan);
        Object result = runtime.invoke(new BusinessActionInvocation(businessModel, action, input, fingerprint));
        if (result instanceof com.ouroboros.util.FileResult file) { return com.ouroboros.web.FileResponses.response(file); }
        return ServerResponse.ok().body(WebApiResult.ok(result));
      } catch (BusinessException error) {
        return ServerResponse.ok().body(WebApiResult.of(error));
      }
    };
  }

  static final class HttpBusinessActionInput extends PreparedBusinessActionInput implements BusinessActionRequestProvider {
    private final RequestInfoMap request;

    HttpBusinessActionInput(ServerRequest request, BusinessActionHttpInputPlan plan) {
      this(request, RequestInfoMap.forRequest(request), plan);
    }

    private HttpBusinessActionInput(ServerRequest request, RequestInfoMap info, BusinessActionHttpInputPlan plan) {
      super(() -> plan.read(request, info));
      this.request = info;
    }

    @Override
    public Map<String, Object> currentRequest() {
      return request;
    }
  }
}
