package com.ouroboros.dev.app.service.vcs;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import com.ouroboros.dev.app.model.AppBranchDTO;
import com.ouroboros.dev.app.model.AppManagedBranchCreateRequest;
import com.ouroboros.exception.BusinessException;

public final class AppVcsBranchModel {
  public static final String RELEASE_TRACK_MASTER = "master";
  public static final String RELEASE_TRACK_CUSTOM = "custom";
  public static final String RELEASE_TRACK_UNMANAGED = "unmanaged";

  private static final String DEFAULT_TRUNK_BRANCH = "master";
  private static final Pattern BRANCH_SEGMENT_PATTERN = Pattern.compile("^[A-Za-z0-9._-]+$");
  private static final Pattern CUSTOM_BRANCH_PATTERN = Pattern.compile("^custom/([A-Za-z0-9._-]+)$");
  private static final Pattern CUSTOM_TAG_PATTERN = Pattern.compile("^v(.+?)-ce\\.([A-Za-z0-9._-]+)$");
  private static final Pattern TRUNK_TAG_PATTERN = Pattern.compile("^v(.+)$");

  private AppVcsBranchModel() {
  }

  public static String normalizeTrunkBranch(String trunkBranch) {
    return StringUtils.defaultIfBlank(StringUtils.trimToNull(trunkBranch), DEFAULT_TRUNK_BRANCH);
  }

  public static BranchInfo resolveBranch(String branchName, String trunkBranch) {
    String normalizedBranch = StringUtils.trimToEmpty(branchName);
    String normalizedTrunk = normalizeTrunkBranch(trunkBranch);
    Matcher customMatcher = CUSTOM_BRANCH_PATTERN.matcher(normalizedBranch);
    if (customMatcher.matches()) {
      return BranchInfo.managed(normalizedBranch,
          RELEASE_TRACK_CUSTOM,
          customMatcher.group(1));
    }

    if (StringUtils.equals(normalizedBranch, normalizedTrunk)) {
      return BranchInfo.managed(normalizedBranch, RELEASE_TRACK_MASTER, null);
    }

    return BranchInfo.unmanaged(normalizedBranch);
  }

  public static BranchInfo createManagedBranch(AppManagedBranchCreateRequest request) {
    if (request == null) {
      throw new BusinessException("受管分支参数不能为空");
    }

    String editionCode = normalizeBranchSegment(request.getEditionCode(), "editionCode");
    return BranchInfo.managed("custom/" + editionCode,
        RELEASE_TRACK_CUSTOM,
        editionCode);
  }

  public static String resolveCreateFromRevision(AppManagedBranchCreateRequest request,
                                                 BranchInfo branchInfo,
                                                 List<String> existingBranches,
                                                 String trunkBranch) {
    if (StringUtils.isNotBlank(request == null ? null : request.getFromRevision())) {
      throw new BusinessException("当前分支契约下，定制分支只能从 master 派生");
    }

    return normalizeTrunkBranch(trunkBranch);
  }

  public static ReleaseInfo resolveTag(String gitTag, String trunkBranch) {
    String normalizedTag = StringUtils.trimToEmpty(gitTag);
    Matcher customMatcher = CUSTOM_TAG_PATTERN.matcher(normalizedTag);
    if (customMatcher.matches()) {
      String logicalVersion = customMatcher.group(1);
      String editionCode = customMatcher.group(2);
      return new ReleaseInfo(logicalVersion,
          normalizedTag,
          RELEASE_TRACK_CUSTOM,
          "custom/" + editionCode,
          editionCode,
          true);
    }

    Matcher trunkMatcher = TRUNK_TAG_PATTERN.matcher(normalizedTag);
    if (trunkMatcher.matches()) {
      return new ReleaseInfo(trunkMatcher.group(1),
          normalizedTag,
          RELEASE_TRACK_MASTER,
          normalizeTrunkBranch(trunkBranch),
          null,
          true);
    }

    String logicalVersion = StringUtils.removeStartIgnoreCase(normalizedTag, "v");
    return new ReleaseInfo(logicalVersion, normalizedTag, RELEASE_TRACK_UNMANAGED, null, null, false);
  }

  public static String normalizeLogicalVersion(String requestedVersion) {
    String normalized = StringUtils.trimToNull(requestedVersion);
    if (normalized == null) {
      throw new BusinessException("version 不能为空");
    }

    ReleaseInfo parsedFromSupportedTag = resolveTag(normalized, DEFAULT_TRUNK_BRANCH);
    if (parsedFromSupportedTag.isManaged()) {
      return parsedFromSupportedTag.getLogicalVersion();
    }

    String versionWithoutPrefix = StringUtils.removeStartIgnoreCase(normalized, "v");
    if (StringUtils.isBlank(versionWithoutPrefix)) {
      throw new BusinessException("version 不能为空");
    }
    return versionWithoutPrefix;
  }

  public static String buildGitTag(String logicalVersion, BranchInfo branchInfo) {
    String normalizedVersion = normalizeLogicalVersion(logicalVersion);
    if (branchInfo == null || !branchInfo.isManaged()) {
      throw new BusinessException("当前分支不符合受管分支模型，无法发版");
    }

    if (RELEASE_TRACK_MASTER.equals(branchInfo.getReleaseTrack())) {
      return "v" + normalizedVersion;
    }
    if (RELEASE_TRACK_CUSTOM.equals(branchInfo.getReleaseTrack())) {
      return "v" + normalizedVersion + "-ce." + branchInfo.getEditionCode();
    }
    throw new BusinessException("当前分支不符合受管分支模型，无法发版");
  }

  public static String normalizeReleaseTrack(String releaseTrack) {
    String normalized = StringUtils.trimToNull(releaseTrack);
    if (normalized == null) {
      throw new BusinessException("releaseTrack 不能为空");
    }
    String compact = normalized.replace("-", "")
        .replace("_", "")
        .replace(" ", "")
        .toLowerCase(Locale.ROOT);
    if ("master".equals(compact) || "trunk".equals(compact)) {
      return RELEASE_TRACK_MASTER;
    }
    if ("customedition".equals(compact) || "custombranch".equals(compact) || "custom".equals(compact)) {
      return RELEASE_TRACK_CUSTOM;
    }
    if ("unmanaged".equals(compact)) {
      return RELEASE_TRACK_UNMANAGED;
    }
    throw new BusinessException("不支持的 releaseTrack: " + releaseTrack);
  }

  private static String normalizeBranchSegment(String value, String fieldName) {
    String normalized = StringUtils.trimToNull(value);
    if (normalized == null) {
      throw new BusinessException(fieldName + " 不能为空");
    }
    if (!BRANCH_SEGMENT_PATTERN.matcher(normalized).matches()) {
      throw new BusinessException(fieldName + " 仅支持字母、数字、点、下划线和中划线");
    }
    return normalized;
  }

  public static final class BranchInfo {
    private final String branchName;
    private final String releaseTrack;
    private final String editionCode;
    private final boolean managed;

    private BranchInfo(String branchName,
                       String releaseTrack,
                       String editionCode,
                       boolean managed) {
      this.branchName = branchName;
      this.releaseTrack = releaseTrack;
      this.editionCode = editionCode;
      this.managed = managed;
    }

    static BranchInfo managed(String branchName, String releaseTrack, String editionCode) {
      return new BranchInfo(branchName, releaseTrack, editionCode, true);
    }

    static BranchInfo unmanaged(String branchName) {
      return new BranchInfo(branchName, RELEASE_TRACK_UNMANAGED, null, false);
    }

    AppBranchDTO toDto(boolean current) {
      return new AppBranchDTO()
          .setBranchName(branchName)
          .setDisplayName(resolveDisplayName())
          .setReleaseTrack(releaseTrack)
          .setEditionCode(editionCode)
          .setCurrent(current)
          .setManaged(managed);
    }

    public String getBranchName() {
      return branchName;
    }

    public String getReleaseTrack() {
      return releaseTrack;
    }

    public String getEditionCode() {
      return editionCode;
    }

    public boolean isManaged() {
      return managed;
    }

    private String resolveDisplayName() {
      if (RELEASE_TRACK_MASTER.equals(releaseTrack)) {
        return DEFAULT_TRUNK_BRANCH;
      }
      if (RELEASE_TRACK_CUSTOM.equals(releaseTrack)) {
        return "custom/" + editionCode;
      }
      return branchName;
    }
  }

  public static final class ReleaseInfo {
    private final String logicalVersion;
    private final String gitTag;
    private final String releaseTrack;
    private final String sourceBranch;
    private final String editionCode;
    private final boolean managed;

    private ReleaseInfo(String logicalVersion,
                        String gitTag,
                        String releaseTrack,
                        String sourceBranch,
                        String editionCode,
                        boolean managed) {
      this.logicalVersion = logicalVersion;
      this.gitTag = gitTag;
      this.releaseTrack = releaseTrack;
      this.sourceBranch = sourceBranch;
      this.editionCode = editionCode;
      this.managed = managed;
    }

    public String getLogicalVersion() {
      return logicalVersion;
    }

    public String getGitTag() {
      return gitTag;
    }

    public String getReleaseTrack() {
      return releaseTrack;
    }

    public String getSourceBranch() {
      return sourceBranch;
    }

    public String getEditionCode() {
      return editionCode;
    }

    public boolean isManaged() {
      return managed;
    }
  }
}
