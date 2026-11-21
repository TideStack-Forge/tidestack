package com.ouroboros.businessmodel.dev.vo;

import java.util.Map;

/** Complete editor draft plus the explicit new implementation; no server-side pending plan. */
public record BusinessModelScriptActionRequest(Map<String, Object> businessModel, String functionName,
    String actionName, String scriptLanguage, String script, Boolean readOnly) { }
