package com.ouroboros.vcs.versioning.git;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 简单的Git功能测试
 */
public class SimpleGitTest {

    private GitManager gitManager;
    private Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        // 创建临时目录
        tempDir = Files.createTempDirectory("git-test");
        gitManager = new GitManager(tempDir.toString(), "master");

        // 创建测试文件
        var testFile = tempDir.resolve("test.txt");
        FileUtils.write(testFile.toFile(), "Hello World", "UTF-8");

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
    void testBasicFileOperations() {
        // 获取文件列表
        var fileList = gitManager.getFileList();
        assertTrue(fileList.isSuccess());
        assertFalse(fileList.get().isEmpty());
        assertTrue(fileList.get().contains("test.txt"));

        System.out.println("Files in repo: " + fileList.get());
    }

    @Test
    void testGetFileContent() {
        // 获取文件内容
        var content = gitManager.getFileContent("test.txt", "HEAD");
        assertTrue(content.isSuccess());
        assertEquals("Hello World", content.get());

        System.out.println("File content: " + content.get());
    }

    @Test
    void testBranchCreation() {
        // 首先检查当前分支
        var currentBranch = gitManager.getCurrentBranch();
        System.out.println("Current branch: " + currentBranch);

        // 列出现有分支
        var branches = gitManager.getBranchList();
        System.out.println("Existing branches: " + branches);

        // 创建分支
        var createResult = gitManager.createBranch("test-branch");
        System.out.println("Create branch result: " + createResult);

        if (createResult.isSuccess()) {
            // 列出分支验证
            var newBranches = gitManager.getBranchList();
            System.out.println("Branches after creation: " + newBranches);
        }
    }
}