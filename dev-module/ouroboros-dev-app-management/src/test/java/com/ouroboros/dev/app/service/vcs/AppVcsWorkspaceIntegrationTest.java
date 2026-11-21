package com.ouroboros.dev.app.service.vcs;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import com.ouroboros.dev.app.model.AppVcsFileRequest;
import com.ouroboros.dev.app.service.AppEditAccessEvaluator;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.identity.SecurityIdentity;
import com.ouroboros.modelmanage.DefaultVCSUserNameResolver;
import com.ouroboros.security.rbac.UserCenter;
import com.ouroboros.vcs.LocalGitVCSManager;
import com.ouroboros.vcs.VCSConfig;
import com.ouroboros.vcs.VCSManagerCenter;
import com.ouroboros.vcs.lock.LocalFileLockManager;

class AppVcsWorkspaceIntegrationTest {
  @TempDir Path temp;

  @Test
  void selectedOwnedChangesAndDeletionCommitTogetherWithoutIncludingOtherEditorsChanges() throws Exception {
    var identity = mock(SecurityIdentity.class);
    when(identity.getUserIdentity()).thenReturn("alice");
    Object previousResolver = ReflectionTestUtils.getField(DefaultVCSUserNameResolver.Proxy.class, "instance");
    new DefaultVCSUserNameResolver(mock(UserCenter.class));
    try {
      CurrentSecurityContextHolder.withIdentityThrowing(identity, () -> {
        var config = new VCSConfig(temp.resolve("repo").toString());
        config.setDefaultBranch("master");
        var locks = new LocalFileLockManager(temp.resolve("locks.json").toString());
        var manager = new LocalGitVCSManager(config, locks);
        try {
          manager.createFile("mine.json").content("old").commit("seed").get();
          manager.createFile("remove.json").content("delete").commit("seed delete").get();
          manager.createFile("other.json").content("old other").commit("seed other").get();
          manager.lock(List.of("mine.json", "remove.json"), false).get();
          manager.getFile("mine.json").write("new").get();
          manager.getFile("remove.json").remove().get();
          locks.lock("other.json", "bob").get();
          Files.writeString(temp.resolve("repo/other.json"), "uncommitted other");
          var center = mock(VCSManagerCenter.class);
          when(center.getVcsManager("crm")).thenReturn(manager);
          var access = mock(AppEditAccessEvaluator.class);
          when(access.canEdit(eq("crm"), nullable(String.class))).thenReturn(true);
          var service = new AppVcsWorkspaceService(center, access);
          assertTrue(service.getFiles("crm", true).get().stream().anyMatch(file -> file.path().equals("remove.json") && !file.exists()));
          assertTrue(service.commit("crm", new AppVcsFileRequest(List.of("mine.json", "remove.json"), "my changes", false)).get());
          try (var git = Git.open(temp.resolve("repo").toFile())) {
            var head = git.log().setMaxCount(1).call().iterator().next();
            assertEquals("my changes 操作人:alice", head.getFullMessage());
            assertEquals("new", manager.getFile("mine.json", "HEAD").getContent());
            assertFalse(manager.getFile("remove.json", "HEAD").exists());
            assertEquals("old other", manager.getFile("other.json", "HEAD").getContent());
            assertTrue(git.status().call().getModified().contains("other.json"));
          }
          assertEquals("alice", locks.getLockOwner("mine.json"));
          assertEquals("bob", locks.getLockOwner("other.json"));
        } finally { manager.destroy(); }
        return null;
      });
    } finally {
      ReflectionTestUtils.setField(DefaultVCSUserNameResolver.Proxy.class, "instance", previousResolver);
    }
  }
}
