package com.ouroboros.ability.auth.config;

import java.util.Map;
import com.ouroboros.configuration.management.ConfigurationMutation;

/** Replaces one protocol list or the explicitly named common settings only. */
public record ExternalAuthConfigurationMutation(String group, Map<String, Object> changes) implements ConfigurationMutation { }
