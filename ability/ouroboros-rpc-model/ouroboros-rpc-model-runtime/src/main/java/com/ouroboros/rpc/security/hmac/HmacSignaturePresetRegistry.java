package com.ouroboros.rpc.security.hmac;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import com.ouroboros.rpc.security.hmac.preset.AliyunAcs3HmacSignaturePreset;
import com.ouroboros.rpc.security.hmac.preset.AliyunRpcV1HmacSignaturePreset;
import com.ouroboros.rpc.security.hmac.preset.AwsSigV4HmacSignaturePreset;
import com.ouroboros.rpc.security.hmac.preset.HuaweiSdkHmacSignaturePreset;
import com.ouroboros.rpc.security.hmac.preset.TencentTc3HmacSignaturePreset;
import com.ouroboros.rpc.security.hmac.preset.VolcengineHmacSignaturePreset;

public class HmacSignaturePresetRegistry {
  private final Map<String, HmacSignaturePreset> presets;

  public HmacSignaturePresetRegistry() {
    this.presets = buildPresets();
  }

  public HmacSignaturePreset requireSupported(HmacSignatureConfig config) {
    if (config == null || StringUtils.isBlank(config.getPresetName())) {
      throw new com.ouroboros.rpc.exception.AuthenticateException("HMAC签名 preset 配置缺失");
    }

    HmacSignaturePreset preset = presets.get(config.getPresetName().toLowerCase());
    if (preset == null) {
      throw config.invalid("preset", "预设不受支持");
    }

    preset.validate(config);
    if (preset.isHeaderSignature()) {
      for (String required : preset.getRequiredSignedHeaders(config)) {
        if (config.getSignedHeadersExclude().contains(required)) {
          throw config.invalid("signedHeaders.exclude", "不允许排除预设要求的请求头[" + required + "]");
        }
      }
    }
    return preset;
  }

  private Map<String, HmacSignaturePreset> buildPresets() {
    Map<String, HmacSignaturePreset> presets = new LinkedHashMap<String, HmacSignaturePreset>();
    register(presets, new AwsSigV4HmacSignaturePreset());
    register(presets, new TencentTc3HmacSignaturePreset());
    register(presets, new HuaweiSdkHmacSignaturePreset());
    register(presets, new VolcengineHmacSignaturePreset());
    register(presets, new AliyunAcs3HmacSignaturePreset());
    register(presets, new AliyunRpcV1HmacSignaturePreset());
    return Collections.unmodifiableMap(presets);
  }

  private void register(Map<String, HmacSignaturePreset> container, HmacSignaturePreset preset) {
    container.put(preset.getName().toLowerCase(), preset);
  }
}
