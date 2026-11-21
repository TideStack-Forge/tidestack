package com.ouroboros.enterprise.runtime.management;

import java.math.BigDecimal;
import com.ouroboros.configuration.management.ConfigurationMutation;

/** Changes one installed provider without rebuilding other providers' budgets. */
record EnterpriseApiBudgetMutation(String provider, BigDecimal requestsPerSecond) implements ConfigurationMutation { }
