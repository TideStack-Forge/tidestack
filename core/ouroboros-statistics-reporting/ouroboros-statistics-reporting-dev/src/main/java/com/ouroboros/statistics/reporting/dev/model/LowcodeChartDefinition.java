package com.ouroboros.statistics.reporting.dev.model;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.ouroboros.statistics.reporting.definition.view.ChartDefinition;

@JsonTypeInfo(use = JsonTypeInfo.Id.NONE)
public class LowcodeChartDefinition extends ChartDefinition {
}
