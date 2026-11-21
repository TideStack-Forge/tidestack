package com.ouroboros.dev.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.vavr.control.Try;

import com.ouroboros.dev.app.model.AppBranchDTO;
import com.ouroboros.dev.app.model.AppManagedBranchCreateRequest;
import com.ouroboros.dev.app.model.AppVersionDTO;
import com.ouroboros.dev.app.service.impl.AppVersionServiceImpl;
import com.ouroboros.dev.app.service.vcs.AppVcsBranchModel;
import com.ouroboros.dev.app.service.vcs.AppVcsFacade;
import com.ouroboros.dev.app.service.vcs.AppVersionTagMetadata;
import com.ouroboros.util.Json;
import com.ouroboros.vcs.VCSManager;
import com.ouroboros.vcs.VCSManagerCenter;
import com.ouroboros.vcs.versioning.Tag;

@ExtendWith(MockitoExtension.class)
class AppVersionBranchAwareReleaseTest {

  @Mock
  private VCSManagerCenter vcsManagerCenter;
  @Mock
  private AppVcsFacade appVcsFacade;
  @Mock
  private VCSManager vcsManager;

  private AppVersionServiceImpl service;

  @BeforeEach
  void setUp() {
    service = new AppVersionServiceImpl(vcsManagerCenter, appVcsFacade);
    lenient().when(vcsManagerCenter.getVcsManager("crm")).thenReturn(vcsManager);
  }

  @Test
  void createOnMasterBranchShouldGeneratePlainTagAndPersistDescriptionMetadata() {
    when(appVcsFacade.ensureWorkspaceClean("crm", "发版")).thenReturn(Try.success((Void) null));
    when(appVcsFacade.getCurrentBranch("crm")).thenReturn(Try.success(branch("master", "master", true, null)));
    when(appVcsFacade.getTrunkBranch("crm")).thenReturn("master");
    when(vcsManager.createTag(anyString(), anyString())).thenReturn(Try.success("refs/tags/v1.2.3"));

    Try<Void> result = service.create("crm", new AppVersionDTO().setVersion("v1.2.3").setDescription("主干发布说明"));

    assertTrue(result.isSuccess());
    ArgumentCaptor<String> tagNameCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
    verify(vcsManager).createTag(tagNameCaptor.capture(), messageCaptor.capture());
    assertEquals("v1.2.3", tagNameCaptor.getValue());

    AppVersionTagMetadata metadata = Json.toBean(AppVersionTagMetadata.class, messageCaptor.getValue());
    assertNotNull(metadata);
    assertEquals("1.2.3", metadata.getLogicalVersion());
    assertEquals("v1.2.3", metadata.getGitTag());
    assertEquals("master", metadata.getReleaseTrack());
    assertEquals("master", metadata.getSourceBranch());
    assertEquals("主干发布说明", metadata.getDescription());
  }

  @Test
  void createOnCustomBranchShouldGenerateCustomTag() {
    when(appVcsFacade.ensureWorkspaceClean("crm", "发版")).thenReturn(Try.success((Void) null));
    when(appVcsFacade.getCurrentBranch("crm")).thenReturn(Try.success(branch("custom/premium", "custom", true, "premium")));
    when(appVcsFacade.getTrunkBranch("crm")).thenReturn("master");
    when(vcsManager.createTag(anyString(), anyString())).thenReturn(Try.success("refs/tags/v3.1.0-ce.premium"));

    Try<Void> result = service.create("crm", new AppVersionDTO().setVersion("v3.1.0"));

    assertTrue(result.isSuccess());
    ArgumentCaptor<String> tagNameCaptor = ArgumentCaptor.forClass(String.class);
    verify(vcsManager).createTag(tagNameCaptor.capture(), anyString());
    assertEquals("v3.1.0-ce.premium", tagNameCaptor.getValue());
  }

  @Test
  void createOnCustomBranchShouldRejectExplicitFromRevisionOverride() {
    AppManagedBranchCreateRequest request = new AppManagedBranchCreateRequest();
    request.setEditionCode("premium");
    request.setFromRevision("release/x");

    AppManagedBranchCreateRequest createRequest = new AppManagedBranchCreateRequest();
    createRequest.setEditionCode("premium");

    Try<String> fromRevision = Try.of(() -> AppVcsBranchModel.resolveCreateFromRevision(
        request,
        AppVcsBranchModel.createManagedBranch(createRequest),
        java.util.Collections.emptyList(),
        "master"
    ));

    assertTrue(fromRevision.isFailure());
  }

  @Test
  void listShouldParseLegacyTrunkTagsAndNewBranchAwareMetadata() {
    when(appVcsFacade.getTrunkBranch("crm")).thenReturn("master");
    Tag customTag = tag("v2.0.0-ce.premium",
        Json.toPrettyJsonString(metadata("2.0.0", "v2.0.0-ce.premium", "custom", "custom/premium", "premium", "定制版发布")),
        LocalDateTime.of(2026, 4, 9, 12, 0, 0));
    Tag legacyTag = tag("v1.0.0", "兼容旧版主干标签", LocalDateTime.of(2026, 4, 9, 10, 0, 0));
    when(vcsManager.getTagList()).thenReturn(Try.success(Arrays.asList(customTag, legacyTag)));

    List<AppVersionDTO> versions = service.getAll("crm");

    assertEquals(2, versions.size());
    AppVersionDTO customVersion = versions.get(0);
    assertEquals("v2.0.0-ce.premium", customVersion.getVersion());
    assertEquals("2.0.0", customVersion.getLogicalVersion());
    assertEquals("custom", customVersion.getReleaseTrack());
    assertEquals("custom/premium", customVersion.getSourceBranch());
    assertEquals("premium", customVersion.getEditionCode());
    assertEquals("定制版发布", customVersion.getDescription());

    AppVersionDTO legacyTrunkVersion = versions.get(1);
    assertEquals("v1.0.0", legacyTrunkVersion.getVersion());
    assertEquals("1.0.0", legacyTrunkVersion.getLogicalVersion());
    assertEquals("master", legacyTrunkVersion.getReleaseTrack());
    assertEquals("master", legacyTrunkVersion.getSourceBranch());
    assertEquals("兼容旧版主干标签", legacyTrunkVersion.getDescription());
  }

  private static AppBranchDTO branch(String branchName,
                                     String releaseTrack,
                                     boolean managed,
                                     String editionCode) {
    return new AppBranchDTO()
        .setBranchName(branchName)
        .setReleaseTrack(releaseTrack)
        .setManaged(managed)
        .setEditionCode(editionCode)
        .setCurrent(true);
  }

  private static AppVersionTagMetadata metadata(String logicalVersion,
                                                String gitTag,
                                                String releaseTrack,
                                                String sourceBranch,
                                                String editionCode,
                                                String description) {
    AppVersionTagMetadata metadata = new AppVersionTagMetadata();
    metadata.setLogicalVersion(logicalVersion);
    metadata.setGitTag(gitTag);
    metadata.setReleaseTrack(releaseTrack);
    metadata.setSourceBranch(sourceBranch);
    metadata.setEditionCode(editionCode);
    metadata.setDescription(description);
    return metadata;
  }

  private static Tag tag(String name, String message, LocalDateTime time) {
    Tag tag = mock(Tag.class);
    when(tag.getName()).thenReturn(name);
    when(tag.getMessage()).thenReturn(message);
    when(tag.getAuthorName()).thenReturn("tester");
    when(tag.getTime()).thenReturn(time);
    return tag;
  }
}
