package com.ouroboros.dev.projectgeneration.artifact;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;

import com.ouroboros.util.ZipLimits;

public class TemplateArchiveReader {

  private static final int UNIX_FILE_TYPE_MASK = 0170000;
  private static final int UNIX_REGULAR_FILE = 0100000;
  private static final int UNIX_DIRECTORY = 0040000;

  private final ZipLimits limits;

  public TemplateArchiveReader(ZipLimits limits) {
    this.limits = limits == null ? ZipLimits.strict() : limits;
  }

  public Map<String, byte[]> read(InputStream inputStream) {
    if (inputStream == null) {
      throw new IllegalArgumentException("Template archive input stream is required");
    }
    Map<String, byte[]> entries = new LinkedHashMap<>();
    Set<String> caseFoldedNames = new HashSet<>();
    long totalSize = 0;
    int count = 0;
    try (ZipFile zip = new ZipFile(new SeekableInMemoryByteChannel(inputStream.readAllBytes()))) {
      Enumeration<ZipArchiveEntry> archiveEntries = zip.getEntriesInPhysicalOrder();
      while (archiveEntries.hasMoreElements()) {
        ZipArchiveEntry entry = archiveEntries.nextElement();
        String name = canonicalName(entry);
        if (entry.isDirectory()) {
          continue;
        }
        rejectSpecialFile(entry);
        if (!zip.canReadEntryData(entry)) {
          throw new IllegalArgumentException("Template archive entry cannot be read safely: " + name);
        }
        String folded = name.toLowerCase(Locale.ROOT);
        if (!caseFoldedNames.add(folded)) {
          throw new IllegalArgumentException("Template archive contains duplicate path: " + name);
        }
        count++;
        limits.validateEntryCount(count);
        byte[] bytes = readEntry(zip, entry, name);
        limits.validateEntrySize(bytes.length, name);
        totalSize += bytes.length;
        limits.validateTotalSize(totalSize);
        entries.put(name, bytes);
      }
      return Map.copyOf(entries);
    } catch (IOException e) {
      throw new IllegalArgumentException("Template archive cannot be read safely", e);
    }
  }

  private static String canonicalName(ZipArchiveEntry entry) {
    String name = entry.getName();
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("Template archive entry name is required");
    }
    if (!Normalizer.isNormalized(name, Normalizer.Form.NFC)) {
      throw new IllegalArgumentException("Template archive entry name must be NFC");
    }
    Path path = Path.of(name);
    if (path.isAbsolute() || name.startsWith("/") || name.contains("\\") || name.contains("\u0000")) {
      throw new IllegalArgumentException("Template archive entry path is not relative: " + name);
    }
    for (String segment : name.split("/")) {
      if (segment.isBlank() || ".".equals(segment) || "..".equals(segment)) {
        throw new IllegalArgumentException("Template archive entry path contains unsafe segment: " + name);
      }
    }
    return name;
  }

  private static void rejectSpecialFile(ZipArchiveEntry entry) {
    if (entry.isUnixSymlink()) {
      throw new IllegalArgumentException("Template archive symlink entries are not allowed: " + entry.getName());
    }
    int type = entry.getUnixMode() & UNIX_FILE_TYPE_MASK;
    if (type != 0 && type != UNIX_REGULAR_FILE && type != UNIX_DIRECTORY) {
      throw new IllegalArgumentException("Template archive special file entries are not allowed: " + entry.getName());
    }
  }

  private static byte[] readEntry(ZipFile zip, ZipArchiveEntry entry, String name) throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    byte[] buffer = new byte[8192];
    int read;
    try (InputStream input = zip.getInputStream(entry)) {
      while ((read = input.read(buffer)) != -1) {
        output.write(buffer, 0, read);
      }
    }
    if (output.size() == 0 && !name.equals("template.json")) {
      return new byte[0];
    }
    return output.toByteArray();
  }
}
