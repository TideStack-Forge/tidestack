package com.ouroboros.dev.app.service.runtime;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import com.ouroboros.dev.app.model.RuntimeImageViewDTO;
import com.ouroboros.dev.runtime.model.BackendType;

@Component
public class RuntimeImageViewService {
  private static final String INPUT_SELECT_LOCAL_IMAGE = "SELECT_LOCAL_IMAGE";
  private static final String INPUT_IMAGE_REFERENCE = "INPUT_IMAGE_REFERENCE";
  private static final Pattern HEX_TAG_PATTERN = Pattern.compile("[0-9a-f]{12,}");

  public RuntimeImageViewDTO describe(BackendType backendType, String imageReference) {
    return describe(backendType, imageReference, null);
  }

  public RuntimeImageViewDTO describe(BackendType backendType, String imageReference, List<String> dockerImageNames) {
    BackendType resolvedBackendType = backendType == null ? BackendType.DOCKER : backendType;
    String reference = trimToNull(imageReference);
    RuntimeImageViewDTO view = new RuntimeImageViewDTO();
    view.setReference(reference);
    view.setInputMode(inputMode(resolvedBackendType));
    view.setCapabilities(capabilities(resolvedBackendType));

    ParsedImageReference parsed = parse(reference);
    view.setRepository(parsed.repository);
    view.setTag(parsed.tag);
    view.setDigest(parsed.digest);
    view.setUpdateRecommended(Boolean.valueOf(updateRecommended(resolvedBackendType, reference, parsed, dockerImageNames)));
    return view;
  }

  private boolean updateRecommended(BackendType backendType,
                                    String reference,
                                    ParsedImageReference parsed,
                                    List<String> dockerImageNames) {
    if (backendType != BackendType.DOCKER) {
      return false;
    }
    if (StringUtils.isBlank(reference)) {
      return true;
    }
    if (StringUtils.isBlank(parsed.tag) && StringUtils.isBlank(parsed.digest)) {
      return true;
    }
    if (StringUtils.isNotBlank(parsed.digest) || looksLikeDigestTag(parsed.tag)) {
      return true;
    }
    return dockerImageNames != null && !dockerImageNames.contains(reference);
  }

  private boolean looksLikeDigestTag(String tag) {
    if (StringUtils.isBlank(tag)) {
      return false;
    }
    String normalized = tag.toLowerCase(Locale.ROOT);
    return normalized.startsWith("sha256:")
        || normalized.startsWith("sha256-")
        || normalized.startsWith("sha-")
        || HEX_TAG_PATTERN.matcher(normalized).matches();
  }

  private Map<String, Boolean> capabilities(BackendType backendType) {
    Map<String, Boolean> values = new LinkedHashMap<String, Boolean>();
    values.put("supportsLocalCatalog", Boolean.valueOf(backendType == BackendType.DOCKER));
    return values;
  }

  private String inputMode(BackendType backendType) {
    return backendType == BackendType.DOCKER ? INPUT_SELECT_LOCAL_IMAGE : INPUT_IMAGE_REFERENCE;
  }

  private ParsedImageReference parse(String reference) {
    ParsedImageReference parsed = new ParsedImageReference();
    if (StringUtils.isBlank(reference)) {
      return parsed;
    }
    String main = reference;
    int digestSeparator = reference.indexOf('@');
    if (digestSeparator >= 0) {
      main = reference.substring(0, digestSeparator);
      parsed.digest = reference.substring(digestSeparator + 1);
    }
    int lastSlash = main.lastIndexOf('/');
    int lastColon = main.lastIndexOf(':');
    if (lastColon > lastSlash) {
      parsed.repository = main.substring(0, lastColon);
      parsed.tag = main.substring(lastColon + 1);
      return parsed;
    }
    parsed.repository = main;
    return parsed;
  }

  private String trimToNull(String value) {
    String trimmed = value == null ? null : value.trim();
    return StringUtils.isBlank(trimmed) ? null : trimmed;
  }

  private static class ParsedImageReference {
    private String repository;
    private String tag;
    private String digest;
  }
}
