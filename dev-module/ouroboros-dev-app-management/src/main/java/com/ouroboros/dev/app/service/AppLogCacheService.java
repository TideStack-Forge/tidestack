package com.ouroboros.dev.app.service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.springframework.stereotype.Service;

import com.ouroboros.configuration.ConfigurationService;
import com.ouroboros.dev.app.constant.ConfigurationConstant;

@Service
public class AppLogCacheService {

  private final ConfigurationService configurationService;
  private final Map<String, CircularBuffer> buffers = new ConcurrentHashMap<>();

  public AppLogCacheService(ConfigurationService configurationService) {
    this.configurationService = configurationService;
  }

  public void addLog(String username, String appName, String logLine) {
    var key = buildUserKey(username, appName);
    buffers.computeIfAbsent(key, k -> new CircularBuffer(getCacheSize())).add(logLine);
  }

  public List<String> getCachedLogs(String username, String appName) {
    var key = buildUserKey(username, appName);
    var buffer = buffers.get(key);
    return buffer != null ? buffer.getAll() : Collections.emptyList();
  }

  public void addHelperLog(String appName, String logLine) {
    var key = buildHelperKey(appName);
    buffers.computeIfAbsent(key, k -> new CircularBuffer(getCacheSize())).add(logLine);
  }

  public List<String> getCachedHelperLogs(String appName) {
    var key = buildHelperKey(appName);
    var buffer = buffers.get(key);
    return buffer != null ? buffer.getAll() : Collections.emptyList();
  }

  public void clearCache(String username, String appName) {
    var key = buildUserKey(username, appName);
    buffers.remove(key);
  }

  public void clearHelperCache(String appName) {
    buffers.remove(buildHelperKey(appName));
  }

  private String buildUserKey(String username, String appName) {
    return "user:" + username + ":" + appName;
  }

  private String buildHelperKey(String appName) {
    return "helper:" + appName;
  }

  private int getCacheSize() {
    return configurationService.<Integer>get(ConfigurationConstant.TEST_APP_LOG_CACHE_LINES)
        .orElse(200);
  }

  private static class CircularBuffer {
    private final String[] buffer;
    private final int capacity;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private int writeIndex = 0;
    private int size = 0;

    CircularBuffer(int capacity) {
      this.capacity = capacity;
      this.buffer = new String[capacity];
    }

    void add(String item) {
      lock.writeLock().lock();
      try {
        buffer[writeIndex] = item;
        writeIndex = (writeIndex + 1) % capacity;
        if (size < capacity) {
          size++;
        }
      } finally {
        lock.writeLock().unlock();
      }
    }

    List<String> getAll() {
      lock.readLock().lock();
      try {
        if (size == 0) {
          return Collections.emptyList();
        }
        var result = new java.util.ArrayList<String>(size);
        var readIndex = size < capacity ? 0 : writeIndex;
        for (int i = 0; i < size; i++) {
          result.add(buffer[(readIndex + i) % capacity]);
        }
        return result;
      } finally {
        lock.readLock().unlock();
      }
    }
  }
}
