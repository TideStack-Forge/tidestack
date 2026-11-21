package com.ouroboros.appmodule.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.ouroboros.appmodule.BaseAppModule;

@JsonAutoDetect(
    fieldVisibility = JsonAutoDetect.Visibility.NONE,
    getterVisibility = JsonAutoDetect.Visibility.PUBLIC_ONLY,
    setterVisibility = JsonAutoDetect.Visibility.PUBLIC_ONLY)
@JsonDeserialize(as = JarAppModule.class)
public class JarAppModule extends BaseAppModule {
}
