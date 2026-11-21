package com.ouroboros.dev.app.service.vcs;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import com.ouroboros.dev.app.model.AppVcsLocalBinding;
import com.ouroboros.dev.app.model.AppVcsPolicy;
import com.ouroboros.dev.app.model.AppVcsRemoteBinding;
import com.ouroboros.dev.app.model.AppVcsRemoteDefinition;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.vcs.VCSConfig;

final class AppVcsRemoteSupport {
  private static final String DEFAULT_REMOTE_NAME = "origin";
  private static final Pattern SAFE_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9._-]+$");
  private static final Pattern SCP_STYLE_REMOTE_PATTERN = Pattern.compile("^([^@\\s]+)@([^:/\\s]+):(.+)$");

  private AppVcsRemoteSupport() {
  }

  static AppVcsPolicy normalizePolicy(AppVcsPolicy policy) {
    AppVcsPolicy normalized = new AppVcsPolicy();
    List<AppVcsRemoteDefinition> remotes = normalizeRemotes(policy);
    String defaultRemote = normalizeDefaultRemote(policy, remotes);

    normalized.setDefaultRemote(defaultRemote);
    normalized.setRemotes(remotes);

    AppVcsRemoteDefinition effectiveRemote = resolveRemote(remotes, defaultRemote);
    if (effectiveRemote != null) {
      normalized.setRemoteName(effectiveRemote.getName());
      normalized.setRemoteUri(effectiveRemote.getUrl());
    }
    return normalized;
  }

  static AppVcsPolicy sanitizePolicyForApi(AppVcsPolicy policy) {
    AppVcsPolicy sanitized = new AppVcsPolicy();
    sanitized.setDefaultRemote(resolveDefaultRemoteName(policy));

    List<AppVcsRemoteDefinition> sanitizedRemotes = new ArrayList<>();
    for (AppVcsRemoteDefinition remote : resolveRemotes(policy)) {
      AppVcsRemoteDefinition current = new AppVcsRemoteDefinition();
      current.setName(remote.getName());
      current.setUrl(sanitizeRemoteUriForApi(remote.getUrl()));
      sanitizedRemotes.add(current);
    }
    sanitized.setRemotes(sanitizedRemotes);

    AppVcsRemoteDefinition effectiveRemote = resolveRemote(sanitizedRemotes, sanitized.getDefaultRemote());
    if (effectiveRemote != null) {
      sanitized.setRemoteName(effectiveRemote.getName());
      sanitized.setRemoteUri(effectiveRemote.getUrl());
    }
    return sanitized;
  }

  static AppVcsLocalBinding normalizeBinding(AppVcsLocalBinding binding) {
    AppVcsLocalBinding normalized = new AppVcsLocalBinding();
    List<AppVcsRemoteBinding> bindings = normalizeBindings(binding);
    normalized.setBindings(bindings);
    copyLegacyBindingFields(normalized, firstBinding(bindings));
    return normalized;
  }

  static AppVcsLocalBinding sanitizeBindingForApi(AppVcsLocalBinding binding, String preferredRemoteName) {
    AppVcsLocalBinding sanitized = new AppVcsLocalBinding();
    List<AppVcsRemoteBinding> sanitizedBindings = new ArrayList<>();
    for (AppVcsRemoteBinding bindingEntry : resolveBindings(binding)) {
      AppVcsRemoteBinding current = copyBinding(bindingEntry);
      current.setPassword(null);
      current.setSshPrivateKey(null);
      current.setSshKeyPath(null);
      sanitizedBindings.add(current);
    }
    sanitized.setBindings(sanitizedBindings);
    AppVcsRemoteBinding effectiveBinding = resolveBinding(sanitizedBindings, preferredRemoteName);
    copyLegacyBindingFields(sanitized, effectiveBinding);
    sanitized.setRemoteName(trimToNull(preferredRemoteName));
    return sanitized;
  }

  static List<AppVcsRemoteDefinition> resolveRemotes(AppVcsPolicy policy) {
    if (policy == null) {
      return Collections.emptyList();
    }
    if (policy.getRemotes() != null && !policy.getRemotes().isEmpty()) {
      return policy.getRemotes();
    }
    if (StringUtils.isAllBlank(policy.getRemoteName(), policy.getRemoteUri())) {
      return Collections.emptyList();
    }
    AppVcsRemoteDefinition legacyRemote = new AppVcsRemoteDefinition();
    legacyRemote.setName(StringUtils.defaultIfBlank(trimToNull(policy.getRemoteName()), DEFAULT_REMOTE_NAME));
    legacyRemote.setUrl(trimToNull(policy.getRemoteUri()));
    return Collections.singletonList(legacyRemote);
  }

  static String resolveDefaultRemoteName(AppVcsPolicy policy) {
    String configured = trimToNull(policy == null ? null : policy.getDefaultRemote());
    if (configured != null) {
      return configured;
    }
    configured = trimToNull(policy == null ? null : policy.getRemoteName());
    if (configured != null) {
      return configured;
    }
    return resolveRemotes(policy).stream()
        .map(AppVcsRemoteDefinition::getName)
        .map(AppVcsRemoteSupport::trimToNull)
        .filter(StringUtils::isNotBlank)
        .findFirst()
        .orElse(null);
  }

  static String resolveTrunkBranch(AppVcsPolicy policy) {
    return AppVcsBranchModel.normalizeTrunkBranch(null);
  }

  static AppVcsRemoteDefinition resolveEffectiveRemote(AppVcsPolicy policy) {
    List<AppVcsRemoteDefinition> remotes = resolveRemotes(policy);
    return resolveRemote(remotes, resolveDefaultRemoteName(policy));
  }

  static List<AppVcsRemoteBinding> resolveBindings(AppVcsLocalBinding binding) {
    if (binding == null) {
      return Collections.emptyList();
    }
    if (binding.getBindings() != null && !binding.getBindings().isEmpty()) {
      return binding.getBindings();
    }
    if (binding.getAuthType() == null || binding.getAuthType() == VCSConfig.AuthType.NONE) {
      return Collections.emptyList();
    }
    AppVcsRemoteBinding legacyBinding = new AppVcsRemoteBinding();
    legacyBinding.setRemoteName(StringUtils.defaultIfBlank(trimToNull(binding.getRemoteName()), DEFAULT_REMOTE_NAME));
    legacyBinding.setAuthType(binding.getAuthType());
    legacyBinding.setUsername(trimToNull(binding.getUsername()));
    legacyBinding.setPassword(trimToNull(binding.getPassword()));
    legacyBinding.setSshKeyName(trimToNull(binding.getSshKeyName()));
    legacyBinding.setSshPrivateKey(trimToNull(binding.getSshPrivateKey()));
    legacyBinding.setSshKeyPath(trimToNull(binding.getSshKeyPath()));
    return Collections.singletonList(legacyBinding);
  }

  static AppVcsRemoteBinding resolveBinding(AppVcsLocalBinding binding, String remoteName) {
    return resolveBinding(resolveBindings(binding), remoteName);
  }

  static AppVcsRemoteBinding resolveBinding(List<AppVcsRemoteBinding> bindings, String remoteName) {
    String normalizedRemoteName = trimToNull(remoteName);
    if (bindings == null || bindings.isEmpty()) {
      return null;
    }
    if (normalizedRemoteName != null) {
      for (AppVcsRemoteBinding binding : bindings) {
        if (StringUtils.equals(trimToNull(binding.getRemoteName()), normalizedRemoteName)) {
          return binding;
        }
      }
      return null;
    }
    return bindings.size() == 1 ? bindings.get(0) : null;
  }

  static void validateEffectiveBindingCompatibility(AppVcsPolicy policy, AppVcsLocalBinding binding) {
    AppVcsRemoteDefinition remote = resolveEffectiveRemote(policy);
    if (remote == null) {
      return;
    }
    validateBindingCompatibility(remote.getUrl(), resolveBinding(binding, remote.getName()));
  }

  static void validateBindingCompatibility(String remoteUri, AppVcsRemoteBinding binding) {
    if (binding == null || binding.getAuthType() == null || binding.getAuthType() == VCSConfig.AuthType.NONE) {
      return;
    }
    String normalizedRemoteUri = trimToNull(remoteUri);
    if (normalizedRemoteUri == null) {
      return;
    }
    if (binding.getAuthType() == VCSConfig.AuthType.HTTP_BASIC) {
      if (!isHttpsRemote(normalizedRemoteUri)) {
        throw new BusinessException("HTTP 基本认证仅支持 https 远程");
      }
      return;
    }
    if (binding.getAuthType() == VCSConfig.AuthType.SSH_KEY && !isSshRemote(normalizedRemoteUri)) {
      throw new BusinessException("SSH Key 仅支持 ssh 或 scp 风格远程");
    }
  }

  static String validateTrackedRemoteUri(String remoteUri) {
    String normalized = trimToNull(remoteUri);
    if (normalized == null) {
      return null;
    }
    if (isScpStyleRemote(normalized)) {
      validateScpRemote(normalized);
      return normalized;
    }

    URI uri = parseRemoteUri(normalized);
    String scheme = normalizeScheme(uri.getScheme());
    if ("https".equals(scheme)) {
      validateHttpRemote(uri);
      return uri.toString();
    }
    if ("ssh".equals(scheme)) {
      validateSshRemote(uri);
      return uri.toString();
    }
    throw new BusinessException("remoteUri 仅支持 https、ssh 或 scp 风格 Git 地址");
  }

  private static boolean isHttpsRemote(String remoteUri) {
    if (isScpStyleRemote(remoteUri)) {
      return false;
    }
    URI uri = parseRemoteUri(remoteUri);
    return "https".equals(normalizeScheme(uri.getScheme()));
  }

  private static boolean isSshRemote(String remoteUri) {
    if (isScpStyleRemote(remoteUri)) {
      return true;
    }
    URI uri = parseRemoteUri(remoteUri);
    return "ssh".equals(normalizeScheme(uri.getScheme()));
  }

  static String sanitizeRemoteUriForApi(String remoteUri) {
    String normalized = trimToNull(remoteUri);
    if (normalized == null) {
      return null;
    }
    Matcher scpMatcher = SCP_STYLE_REMOTE_PATTERN.matcher(normalized);
    if (scpMatcher.matches()) {
      if (scpMatcher.group(1).contains(":")
          || scpMatcher.group(3).contains("?")
          || scpMatcher.group(3).contains("#")) {
        return null;
      }
      if (!isAllowedRemoteHost(scpMatcher.group(2))) {
        return null;
      }
      return "ssh://" + scpMatcher.group(2) + "/" + trimLeadingSlash(scpMatcher.group(3));
    }
    URI uri;
    try {
      uri = parseRemoteUri(normalized);
    } catch (BusinessException exception) {
      return null;
    }
    String scheme = normalizeScheme(uri.getScheme());
    if (!"https".equals(scheme) && !"ssh".equals(scheme)) {
      return null;
    }
    if (StringUtils.isBlank(uri.getHost())) {
      return null;
    }
    if (!isAllowedRemoteHost(uri.getHost())) {
      return null;
    }
    if (StringUtils.isNotBlank(uri.getRawQuery()) || StringUtils.isNotBlank(uri.getRawFragment())) {
      return null;
    }
    String path = StringUtils.defaultString(uri.getRawPath());
    String query = uri.getRawQuery();
    String fragment = uri.getRawFragment();
    StringBuilder builder = new StringBuilder();
    builder.append(scheme).append("://");
    appendHost(builder, uri.getHost());
    if (uri.getPort() > 0) {
      builder.append(":").append(uri.getPort());
    }
    if (StringUtils.isNotEmpty(path)) {
      builder.append(path);
    }
    if (StringUtils.isNotEmpty(query)) {
      builder.append("?").append(query);
    }
    if (StringUtils.isNotEmpty(fragment)) {
      builder.append("#").append(fragment);
    }
    return builder.toString();
  }

  private static void appendHost(StringBuilder builder, String host) {
    if (host != null && host.contains(":")) {
      builder.append('[').append(host).append(']');
      return;
    }
    builder.append(host);
  }

  private static List<AppVcsRemoteDefinition> normalizeRemotes(AppVcsPolicy policy) {
    List<AppVcsRemoteDefinition> remotes = new ArrayList<>();
    Set<String> remoteNames = new HashSet<>();
    for (AppVcsRemoteDefinition remote : resolveRemotes(policy)) {
      AppVcsRemoteDefinition current = new AppVcsRemoteDefinition();
      current.setName(normalizeRemoteName(remote == null ? null : remote.getName()));
      if (!remoteNames.add(current.getName())) {
        throw new BusinessException("remoteName 重复: " + current.getName());
      }
      current.setUrl(validateTrackedRemoteUri(remote == null ? null : remote.getUrl()));
      remotes.add(current);
    }
    return remotes;
  }

  private static String normalizeDefaultRemote(AppVcsPolicy policy, List<AppVcsRemoteDefinition> remotes) {
    String configured = resolveDefaultRemoteName(policy);
    if (configured == null) {
      return remotes.isEmpty() ? null : remotes.get(0).getName();
    }
    boolean exists = remotes.stream().anyMatch(remote -> StringUtils.equals(remote.getName(), configured));
    if (!exists && !remotes.isEmpty()) {
      throw new BusinessException("defaultRemote 未在 remotes 中定义: " + configured);
    }
    return configured;
  }

  private static List<AppVcsRemoteBinding> normalizeBindings(AppVcsLocalBinding binding) {
    List<AppVcsRemoteBinding> bindings = new ArrayList<>();
    Set<String> remoteNames = new HashSet<>();
    for (AppVcsRemoteBinding currentBinding : resolveBindings(binding)) {
      AppVcsRemoteBinding current = copyBinding(currentBinding);
      current.setRemoteName(normalizeRemoteName(current.getRemoteName()));
      if (!remoteNames.add(current.getRemoteName())) {
        throw new BusinessException("remoteName 绑定重复: " + current.getRemoteName());
      }
      current.setAuthType(current.getAuthType());
      current.setUsername(trimToNull(current.getUsername()));
      current.setPassword(trimToNull(current.getPassword()));
      current.setSshKeyName(trimToNull(current.getSshKeyName()));
      current.setSshPrivateKey(trimToNull(current.getSshPrivateKey()));
      current.setSshKeyPath(trimToNull(current.getSshKeyPath()));
      bindings.add(current);
    }
    return bindings;
  }

  private static AppVcsRemoteBinding copyBinding(AppVcsRemoteBinding binding) {
    AppVcsRemoteBinding copy = new AppVcsRemoteBinding();
    if (binding == null) {
      return copy;
    }
    copy.setRemoteName(trimToNull(binding.getRemoteName()));
    copy.setAuthType(binding.getAuthType());
    copy.setUsername(trimToNull(binding.getUsername()));
    copy.setPassword(trimToNull(binding.getPassword()));
    copy.setSshKeyName(trimToNull(binding.getSshKeyName()));
    copy.setSshPrivateKey(trimToNull(binding.getSshPrivateKey()));
    copy.setSshKeyPath(trimToNull(binding.getSshKeyPath()));
    return copy;
  }

  private static void copyLegacyBindingFields(AppVcsLocalBinding target, AppVcsRemoteBinding source) {
    if (target == null) {
      return;
    }
    target.setAuthType(source == null ? VCSConfig.AuthType.NONE : source.getAuthType());
    target.setRemoteName(source == null ? null : source.getRemoteName());
    target.setUsername(source == null ? null : source.getUsername());
    target.setPassword(source == null ? null : source.getPassword());
    target.setSshKeyName(source == null ? null : source.getSshKeyName());
    target.setSshPrivateKey(source == null ? null : source.getSshPrivateKey());
    target.setSshKeyPath(source == null ? null : source.getSshKeyPath());
  }

  private static AppVcsRemoteBinding firstBinding(List<AppVcsRemoteBinding> bindings) {
    return bindings == null || bindings.isEmpty() ? null : bindings.get(0);
  }

  private static AppVcsRemoteDefinition resolveRemote(List<AppVcsRemoteDefinition> remotes, String remoteName) {
    if (remotes == null || remotes.isEmpty()) {
      return null;
    }
    String normalizedRemoteName = trimToNull(remoteName);
    if (normalizedRemoteName != null) {
      for (AppVcsRemoteDefinition remote : remotes) {
        if (StringUtils.equals(remote.getName(), normalizedRemoteName)) {
          return remote;
        }
      }
    }
    return remotes.get(0);
  }

  private static String normalizeRemoteName(String remoteName) {
    String normalized = StringUtils.defaultIfBlank(trimToNull(remoteName), DEFAULT_REMOTE_NAME);
    if (!SAFE_NAME_PATTERN.matcher(normalized).matches()) {
      throw new BusinessException("remoteName 非法");
    }
    return normalized;
  }

  private static boolean isScpStyleRemote(String remoteUri) {
    return SCP_STYLE_REMOTE_PATTERN.matcher(remoteUri).matches();
  }

  private static URI parseRemoteUri(String remoteUri) {
    try {
      return new URI(remoteUri);
    } catch (URISyntaxException exception) {
      throw new BusinessException("remoteUri 格式不合法");
    }
  }

  private static void validateHttpRemote(URI uri) {
    if (StringUtils.isNotBlank(uri.getUserInfo())) {
      throw new BusinessException("HTTP(S) remoteUri 不允许包含账号或密码，请改用本地凭证绑定");
    }
    validateNoQueryOrFragment(uri);
    if (StringUtils.isBlank(uri.getHost()) || StringUtils.isBlank(uri.getPath())) {
      throw new BusinessException("remoteUri 格式不合法");
    }
    validateRemoteHost(uri.getHost());
  }

  private static void validateSshRemote(URI uri) {
    validateNoQueryOrFragment(uri);
    if (StringUtils.isBlank(uri.getHost()) || StringUtils.isBlank(uri.getPath())) {
      throw new BusinessException("remoteUri 格式不合法");
    }
    validateRemoteHost(uri.getHost());
    String userInfo = trimToNull(uri.getUserInfo());
    if (userInfo != null && userInfo.contains(":")) {
      throw new BusinessException("SSH remoteUri 不允许包含密码");
    }
  }

  private static void validateGitRemote(URI uri) {
    validateNoQueryOrFragment(uri);
    if (StringUtils.isBlank(uri.getHost()) || StringUtils.isBlank(uri.getPath())) {
      throw new BusinessException("remoteUri 格式不合法");
    }
    if (StringUtils.isNotBlank(uri.getUserInfo())) {
      throw new BusinessException("git remoteUri 不允许包含账号或密码");
    }
  }

  private static void validateScpRemote(String remoteUri) {
    Matcher matcher = SCP_STYLE_REMOTE_PATTERN.matcher(remoteUri);
    if (!matcher.matches()) {
      throw new BusinessException("remoteUri 格式不合法");
    }
    validateRemoteHost(matcher.group(2));
    if (matcher.group(1).contains(":")) {
      throw new BusinessException("SCP 风格 remoteUri 不允许包含密码");
    }
    if (matcher.group(3).contains("?") || matcher.group(3).contains("#")) {
      throw new BusinessException("remoteUri 不允许包含 query 或 fragment");
    }
  }

  private static void validateNoQueryOrFragment(URI uri) {
    if (StringUtils.isNotBlank(uri.getRawQuery()) || StringUtils.isNotBlank(uri.getRawFragment())) {
      throw new BusinessException("remoteUri 不允许包含 query 或 fragment");
    }
  }

  private static void validateRemoteHost(String host) {
    if (!isAllowedRemoteHost(host)) {
      throw new BusinessException("remoteUri 不允许使用本机或内网地址");
    }
  }

  private static boolean isAllowedRemoteHost(String host) {
    String normalizedHost = normalizeRemoteHost(host);
    if (normalizedHost == null) {
      return false;
    }
    if ("localhost".equals(normalizedHost)
        || normalizedHost.endsWith(".localhost")
        || normalizedHost.endsWith(".localhost.localdomain")
        || normalizedHost.endsWith(".localdomain")
        || normalizedHost.endsWith(".local")
        || normalizedHost.endsWith(".internal")
        || normalizedHost.endsWith(".intranet")
        || normalizedHost.endsWith(".lan")
        || normalizedHost.endsWith(".home")
        || normalizedHost.endsWith(".home.arpa")
        || normalizedHost.endsWith(".corp")
        || normalizedHost.endsWith(".private")) {
      return false;
    }
    if (!normalizedHost.contains(":") && !normalizedHost.contains(".") && !normalizedHost.matches("^\\d+$")) {
      return false;
    }
    if (normalizedHost.matches("^\\d+$")) {
      return false;
    }
    if (normalizedHost.matches("^[0-9.]+$") && !isCanonicalIpv4(normalizedHost)) {
      return false;
    }
    if (isPrivateIpv4(normalizedHost) || isPrivateIpv6(normalizedHost)) {
      return false;
    }
    if (resolvesToProhibitedAddress(normalizedHost)) {
      return false;
    }
    return true;
  }

  private static String normalizeRemoteHost(String host) {
    String normalizedHost = StringUtils.lowerCase(trimToNull(host), Locale.ROOT);
    if (normalizedHost == null) {
      return null;
    }
    while (normalizedHost.endsWith(".")) {
      normalizedHost = normalizedHost.substring(0, normalizedHost.length() - 1);
    }
    return StringUtils.trimToNull(normalizedHost);
  }

  private static boolean isPrivateIpv4(String host) {
    if (!host.matches("^\\d{1,3}(\\.\\d{1,3}){3}$")) {
      return false;
    }
    String[] segments = host.split("\\.");
    int[] octets = new int[segments.length];
    for (int i = 0; i < segments.length; i++) {
      try {
        octets[i] = Integer.parseInt(segments[i]);
      } catch (NumberFormatException exception) {
        return false;
      }
      if (octets[i] < 0 || octets[i] > 255) {
        return false;
      }
    }
    return octets[0] == 0
        || octets[0] == 10
        || octets[0] == 127
        || (octets[0] == 169 && octets[1] == 254)
        || (octets[0] == 172 && octets[1] >= 16 && octets[1] <= 31)
        || (octets[0] == 192 && octets[1] == 168)
        || (octets[0] >= 224 && octets[0] <= 239);
  }

  private static boolean isCanonicalIpv4(String host) {
    if (!host.matches("^\\d{1,3}(\\.\\d{1,3}){3}$")) {
      return false;
    }
    String[] segments = host.split("\\.");
    for (String segment : segments) {
      try {
        int value = Integer.parseInt(segment);
        if (value < 0 || value > 255) {
          return false;
        }
        if (!String.valueOf(value).equals(segment)) {
          return false;
        }
      } catch (NumberFormatException exception) {
        return false;
      }
    }
    return true;
  }

  private static boolean resolvesToProhibitedAddress(String host) {
    try {
      InetAddress[] resolved = InetAddress.getAllByName(host);
      for (InetAddress address : resolved) {
        if (isProhibitedResolvedAddress(address)) {
          return true;
        }
      }
      return false;
    } catch (UnknownHostException exception) {
      return false;
    }
  }

  private static boolean isProhibitedResolvedAddress(InetAddress address) {
    if (address == null) {
      return false;
    }
    if (address.isAnyLocalAddress()
        || address.isLoopbackAddress()
        || address.isLinkLocalAddress()
        || address.isSiteLocalAddress()
        || address.isMulticastAddress()) {
      return true;
    }

    byte[] raw = address.getAddress();
    if (raw.length == 4) {
      return isPrivateIpv4(address.getHostAddress());
    }
    if (raw.length == 16) {
      int first = raw[0] & 0xff;
      if ((first & 0xfe) == 0xfc) {
        return true;
      }
      if (isIpv4MappedIpv6(raw)) {
        return isPrivateMappedIpv4(raw);
      }
    }
    return false;
  }

  private static boolean isIpv4MappedIpv6(byte[] raw) {
    if (raw.length != 16) {
      return false;
    }
    for (int i = 0; i < 10; i++) {
      if (raw[i] != 0) {
        return false;
      }
    }
    return raw[10] == (byte) 0xff && raw[11] == (byte) 0xff;
  }

  private static boolean isPrivateMappedIpv4(byte[] raw) {
    int first = raw[12] & 0xff;
    int second = raw[13] & 0xff;
    return first == 0
        || first == 10
        || first == 127
        || (first == 169 && second == 254)
        || (first == 172 && second >= 16 && second <= 31)
        || (first == 192 && second == 168)
        || (first >= 224 && first <= 239);
  }

  private static boolean isPrivateIpv6(String host) {
    String normalized = StringUtils.remove(host, '[');
    normalized = StringUtils.remove(normalized, ']');
    int zoneIndex = normalized.indexOf('%');
    if (zoneIndex >= 0) {
      normalized = normalized.substring(0, zoneIndex);
    }
    if (!normalized.contains(":")) {
      return false;
    }
    String compact = normalized.toLowerCase(Locale.ROOT);
    return "::".equals(compact)
        || "::1".equals(compact)
        || compact.startsWith("fe80:")
        || compact.startsWith("fc")
        || compact.startsWith("fd")
        || compact.startsWith("ff");
  }

  private static String normalizeScheme(String scheme) {
    return StringUtils.lowerCase(trimToNull(scheme), Locale.ROOT);
  }

  private static String trimLeadingSlash(String value) {
    String normalized = StringUtils.defaultString(value);
    while (normalized.startsWith("/")) {
      normalized = normalized.substring(1);
    }
    return normalized;
  }

  private static String trimToNull(String value) {
    return StringUtils.trimToNull(value);
  }
}
