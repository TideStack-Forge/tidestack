package com.ouroboros.dev.app.util;

import java.io.*;
import java.nio.file.Files;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 自动删除临时文件的输入流
 *
 * 这个输入流包装器在流关闭时会自动删除关联的临时文件，
 * 用于处理需要通过临时文件传输数据但又不希望留下临时文件的场景。
 *
 * @author Song Mingxu
 */
public class AutoDeletingFileInputStream extends InputStream {
    private static final Logger logger = LoggerFactory.getLogger(AutoDeletingFileInputStream.class);

    private final File tempFile;
    private final FileInputStream fileInputStream;

    /**
     * 构造自动删除文件输入流
     *
     * @param tempFile 临时文件，流关闭时会被自动删除
     * @throws IOException 如果文件不存在或无法读取
     */
    public AutoDeletingFileInputStream(File tempFile) throws IOException {
        this.tempFile = tempFile;
        this.fileInputStream = new FileInputStream(tempFile);
    }

    @Override
    public int read() throws IOException {
        return fileInputStream.read();
    }

    @Override
    public int read(byte[] b) throws IOException {
        return fileInputStream.read(b);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        return fileInputStream.read(b, off, len);
    }

    @Override
    public long skip(long n) throws IOException {
        return fileInputStream.skip(n);
    }

    @Override
    public int available() throws IOException {
        return fileInputStream.available();
    }

    @Override
    public void close() throws IOException {
        try {
            fileInputStream.close();
        } finally {
            // 删除临时文件
            try {
                Files.deleteIfExists(tempFile.toPath());
            } catch (IOException e) {
                logger.warn("删除临时文件失败: {}", tempFile, e);
            }
        }
    }

    @Override
    public synchronized void mark(int readlimit) {
        fileInputStream.mark(readlimit);
    }

    @Override
    public synchronized void reset() throws IOException {
        fileInputStream.reset();
    }

    @Override
    public boolean markSupported() {
        return fileInputStream.markSupported();
    }

    /**
     * 获取关联的临时文件
     *
     * @return 临时文件对象
     */
    public File getTempFile() {
        return tempFile;
    }
}
