package com.ouroboros.vcs.versioning.git;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GitManager增强功能测试
 */
public class GitManagerEnhancedTest {

    private GitManager gitManager;
    private Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        // 创建临时目录
        tempDir = Files.createTempDirectory("git-test");
        gitManager = new GitManager(tempDir.toString(), "main");

        // 创建测试文件
        var testFile = tempDir.resolve("test.txt");
        Files.write(testFile, "Hello World".getBytes(StandardCharsets.UTF_8));

        // 初始提交
        gitManager.commit("test.txt", "Initial commit").get();
    }

    @AfterEach
    void tearDown() throws IOException {
        gitManager.destroy();
        // 清理临时目录
        Files.walk(tempDir)
            .sorted(Comparator.reverseOrder())
            .map(Path::toFile)
            .forEach(File::delete);
    }

    @Test
    void rootDirectoryQueriesIncludeDeletedAndNestedUncommittedFiles() throws IOException {
        Files.delete(tempDir.resolve("test.txt"));
        Files.createDirectories(tempDir.resolve("models"));
        Files.writeString(tempDir.resolve("models/new.json"), "{}");
        for (String root : new String[] {"", ".", "./"}) {
            assertEquals(Set.of("test.txt", "models/new.json"), Set.copyOf(gitManager.getUncommittedFiles(root)));
        }
        assertEquals(Set.of("models/new.json"), Set.copyOf(gitManager.getUncommittedFiles("models/")));
    }

    @Test
    void testRepositoryUsesRequestedDefaultBranch() {
        var currentBranch = gitManager.getCurrentBranch();
        assertTrue(currentBranch.isSuccess());
        assertEquals("main", currentBranch.get());
    }

    @Test
    void testBranchOperations() {
        // 创建分支
        var createResult = gitManager.createBranch("feature-branch");
        if (createResult.isFailure()) {
            System.out.println("Create branch failed: " + createResult.getCause().getMessage());
        }
        assertTrue(createResult.isSuccess());

        // 列出分支
        var branches = gitManager.getBranchList();
        if (branches.isFailure()) {
            System.out.println("List branches failed: " + branches.getCause().getMessage());
        }
        assertTrue(branches.isSuccess());
        assertTrue(branches.get().contains("feature-branch"));

        // 切换分支
        var switchResult = gitManager.switchBranch("feature-branch");
        if (switchResult.isFailure()) {
            System.out.println("Switch branch failed: " + switchResult.getCause().getMessage());
        }
        assertTrue(switchResult.isSuccess());

        // 获取当前分支
        var currentBranch = gitManager.getCurrentBranch();
        if (currentBranch.isFailure()) {
            System.out.println("Get current branch failed: " + currentBranch.getCause().getMessage());
        }
        assertTrue(currentBranch.isSuccess());
        assertEquals("feature-branch", currentBranch.get());

        // 删除分支（先切回main）
        var switchBackResult = gitManager.switchBranch("main");
        if (switchBackResult.isFailure()) {
            System.out.println("Switch branch back failed: " + switchBackResult.getCause().getMessage());
        }
        assertTrue(switchBackResult.isSuccess());

        var switchedBranch = gitManager.getCurrentBranch();
        assertTrue(switchedBranch.isSuccess());
        assertEquals("main", switchedBranch.get());

        var deleteResult = gitManager.deleteBranch("feature-branch");
        if (deleteResult.isFailure()) {
            System.out.println("Delete branch failed: " + deleteResult.getCause().getMessage());
        }
        assertTrue(deleteResult.isSuccess());
    }

    @Test
    void testTagOperations() {
        // 创建标签
        var createResult = gitManager.createTag("v1.0.0", "Release version 1.0.0");
        assertTrue(createResult.isSuccess());

        // 列出标签
        var tags = gitManager.getTagList();
        assertTrue(tags.isSuccess());
        assertTrue(tags.get().stream()
            .map(com.ouroboros.vcs.versioning.Tag::getName)
            .collect(Collectors.toList())
            .contains("v1.0.0"));

        // 删除标签
        var deleteResult = gitManager.deleteTag("v1.0.0");
        assertTrue(deleteResult.isSuccess());
    }

    @Test
    void testFileListOperations() throws IOException {
        // 添加更多测试文件
        Files.write(tempDir.resolve("file1.txt"), "Content 1".getBytes(StandardCharsets.UTF_8));
        Files.write(tempDir.resolve("file2.txt"), "Content 2".getBytes(StandardCharsets.UTF_8));
        gitManager.commit("file1.txt", "Add file1").get();
        gitManager.commit("file2.txt", "Add file2").get();

        // 获取当前文件列表
        var fileList = gitManager.getFileList();
        assertTrue(fileList.isSuccess());
        assertTrue(fileList.get().contains("test.txt"));
        assertTrue(fileList.get().contains("file1.txt"));
        assertTrue(fileList.get().contains("file2.txt"));

        // 创建分支并获取分支文件列表
        gitManager.createBranch("test-branch");
        var branchFileList = gitManager.getFileListFromBranch("test-branch");
        assertTrue(branchFileList.isSuccess());
        assertEquals(fileList.get().size(), branchFileList.get().size());
    }

    @Test
    void testHistoricalFileContent() throws IOException {
        // 修改文件内容
        Files.write(tempDir.resolve("test.txt"), "Modified content".getBytes(StandardCharsets.UTF_8));
        var commitResult = gitManager.commit("test.txt", "Modify test file");
        assertTrue(commitResult.isSuccess());

        // 获取修改后的内容
        var currentContent = gitManager.getFileContent("test.txt", "HEAD");
        assertTrue(currentContent.isSuccess());
        assertEquals("Modified content", currentContent.get());

        // 创建标签
        gitManager.createTag("v1.0", "Version 1.0");

        // 从标签获取文件内容
        var tagContent = gitManager.getFileContentFromTag("test.txt", "v1.0");
        assertTrue(tagContent.isSuccess());
        assertEquals("Modified content", tagContent.get());
    }

    @Test
    void testFileBytes() throws IOException {
        // 测试获取文件字节内容
        var fileBytes = gitManager.getFileBytes("test.txt", "HEAD");
        assertTrue(fileBytes.isSuccess());
        assertEquals("Hello World", new String(fileBytes.get()));
    }
}
