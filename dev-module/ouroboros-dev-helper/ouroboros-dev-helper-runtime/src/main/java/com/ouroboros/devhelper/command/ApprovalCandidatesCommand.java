package com.ouroboros.devhelper.command;

import java.util.*;
import com.ouroboros.workflow.approval.CandidateProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/** Queries the application's registered candidate providers, including application-specific ones. */
@Component
@ConditionalOnProperty(name = "ouroboros.dev-helper.test-app", havingValue = "false", matchIfMissing = true)
public final class ApprovalCandidatesCommand extends AbstractAsyncCommand {
  private final ObjectProvider<CandidateProvider> candidates;
  public ApprovalCandidatesCommand(ObjectProvider<CandidateProvider> candidates) {
    super(Commands.APPROVAL_CANDIDATES.getCommand()); this.candidates = candidates;
  }
  @Override protected void doExecute(CommandRequest request) {
    var params = request.getParams();
    if ("types".equals(params.get("operation"))) {
      success(request, candidates.orderedStream().map(candidate -> {
        // Export the provider contract, without serializing its implementation or injected services.
        var descriptor = new LinkedHashMap<String, Object>();
        descriptor.put("name", candidate.getName());
        descriptor.put("label", candidate.getLabel());
        descriptor.put("configMode", candidate.getConfigMode());
        descriptor.put("schema", candidate.getSchema());
        descriptor.put("valueKey", candidate.getValueKey());
        descriptor.put("disabledIn", candidate.getDisabledIn());
        descriptor.put("categoryLabel", candidate.getCategoryLabel());
        descriptor.put("max", candidate.getMax());
        descriptor.put("min", candidate.getMin());
        descriptor.put("items", candidate.getItems());
        return descriptor;
      }).toList());
      return;
    }
    var provider = candidates.orderedStream().filter(candidate -> candidate.getName().equals(params.get("type"))).findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unknown approval candidate provider"));
    if ("category".equals(params.get("operation"))) { success(request, provider.getCategories()); return; }
    if (!"items".equals(params.get("operation"))) { failure(request, "不支持的候选人查询"); return; }
    if (params.get("values") instanceof List<?> values) {
      success(request, provider.search(values.stream().map(String::valueOf).toList()));
    } else success(request, provider.search((String) params.get("category"), (String) params.get("keyword")));
  }
}
