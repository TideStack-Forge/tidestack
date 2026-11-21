package com.ouroboros.vcs.file;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * VCSFile历史功能测试
 */
public class VCSFileHistoricalTest {

    @Test
    void testFileOperationValidation() {
        // 测试读操作
        assertTrue(FileOperation.READ.isReadOnly());
        assertFalse(FileOperation.READ.isWriteOperation());

        // 测试写操作
        assertFalse(FileOperation.WRITE.isReadOnly());
        assertTrue(FileOperation.WRITE.isWriteOperation());

        // 测试其他操作
        assertFalse(FileOperation.DELETE.isReadOnly());
        assertFalse(FileOperation.LOCK.isReadOnly());
        assertFalse(FileOperation.UNLOCK.isReadOnly());
        assertFalse(FileOperation.COMMIT.isReadOnly());
        assertFalse(FileOperation.ROLLBACK.isReadOnly());
    }

    @Test
    void testFileRevisionTypes() {
        FileRevision revision = new FileRevision("abc123", "author",
            java.time.LocalDateTime.now(), "test commit", 100L,
            FileRevision.RevisionType.COMMIT);

        assertEquals("abc123", revision.getRevision());
        assertEquals("author", revision.getAuthor());
        assertEquals("test commit", revision.getCommitMessage());
        assertEquals(100L, revision.getFileSize());
        assertTrue(revision.isCommit());
        assertFalse(revision.isBranch());
        assertFalse(revision.isTag());

        FileRevision branchRevision = new FileRevision("branch-ref", "author",
            java.time.LocalDateTime.now(), "branch", 100L,
            FileRevision.RevisionType.BRANCH);
        assertTrue(branchRevision.isBranch());
        assertFalse(branchRevision.isCommit());

        FileRevision tagRevision = new FileRevision("tag-ref", "author",
            java.time.LocalDateTime.now(), "tag", 100L,
            FileRevision.RevisionType.TAG);
        assertTrue(tagRevision.isTag());
        assertFalse(tagRevision.isCommit());
    }

    @Test
    void testLineDiff() {
        LineDiff addedLine = new LineDiff(1, LineDiff.DiffAction.ADD, null, "new content");
        assertTrue(addedLine.isAdded());
        assertFalse(addedLine.isDeleted());
        assertFalse(addedLine.isModified());
        assertEquals(1, addedLine.getLineNumber());
        assertEquals("new content", addedLine.getNewContent());
        assertNull(addedLine.getOldContent());

        LineDiff deletedLine = new LineDiff(2, LineDiff.DiffAction.DELETE, "old content", null);
        assertTrue(deletedLine.isDeleted());
        assertFalse(deletedLine.isAdded());
        assertFalse(deletedLine.isModified());

        LineDiff modifiedLine = new LineDiff(3, LineDiff.DiffAction.MODIFY, "old", "new");
        assertTrue(modifiedLine.isModified());
        assertFalse(modifiedLine.isAdded());
        assertFalse(modifiedLine.isDeleted());
    }

    @Test
    void testFileDiff() {
        LineDiff line1 = new LineDiff(1, LineDiff.DiffAction.ADD, null, "added");
        LineDiff line2 = new LineDiff(2, LineDiff.DiffAction.DELETE, "deleted", null);
        LineDiff line3 = new LineDiff(3, LineDiff.DiffAction.MODIFY, "old", "new");

        FileDiff fileDiff = new FileDiff("HEAD~1", "HEAD", "test.txt",
            FileDiff.DiffType.MODIFIED, Arrays.asList(line1, line2, line3), 1, 1);

        assertEquals("test.txt", fileDiff.getFilePath());
        assertEquals("HEAD~1", fileDiff.getFromRevision());
        assertEquals("HEAD", fileDiff.getToRevision());
        assertEquals(FileDiff.DiffType.MODIFIED, fileDiff.getDiffType());
        assertEquals(3, fileDiff.getLineDiffs().size());

        assertTrue(fileDiff.hasChanges());
        assertEquals(1, fileDiff.getAddedLines());
        assertEquals(1, fileDiff.getDeletedLines());
        assertEquals(2, fileDiff.getChangedLines());

        FileDiff emptyDiff = new FileDiff("HEAD", "HEAD", "empty.txt",
            FileDiff.DiffType.IDENTICAL, Arrays.asList(), 0, 0);
        assertFalse(emptyDiff.hasChanges());
        assertEquals(0, emptyDiff.getAddedLines());
        assertEquals(0, emptyDiff.getDeletedLines());
        assertEquals(0, emptyDiff.getChangedLines());
    }

    @Test
    void testVCSFileInterfaceSignatures() {
        // 这个测试验证VCSFile接口的新方法签名是否正确定义
        // 通过反射检查方法是否存在
        try {
            Class<?> vcsFileClass = VCSFile.class;

            // 检查历史操作方法
            assertNotNull(vcsFileClass.getMethod("getRevision"));
            assertNotNull(vcsFileClass.getMethod("isHistorical"));
            assertNotNull(vcsFileClass.getMethod("atRevision", String.class));
            assertNotNull(vcsFileClass.getMethod("atBranch", String.class));
            assertNotNull(vcsFileClass.getMethod("atTag", String.class));

            // 检查差异比较方法
            assertNotNull(vcsFileClass.getMethod("diffWith", String.class));
            assertNotNull(vcsFileClass.getMethod("diffWithWorkingCopy"));

            // 检查历史版本方法
            assertNotNull(vcsFileClass.getMethod("getRevisions"));
            assertNotNull(vcsFileClass.getMethod("getRevisions", int.class, int.class));

            // 检查操作验证方法
            assertNotNull(vcsFileClass.getMethod("isOperationAllowed", FileOperation.class));

        } catch (NoSuchMethodException e) {
            fail("VCSFile interface method missing: " + e.getMessage());
        }
    }
}