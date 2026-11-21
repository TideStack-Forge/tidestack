package com.ouroboros.storage;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CoreStorageContractTest {
  @Test void keyPolicyPreservesLogicalNamesAndRejectsTraversalAndTechnicalNamespace() {
    assertEquals("报告/空 格.A%20", ObjectKeys.validate("报告/空 格.A%20"));
    assertEquals("", ObjectKeys.validatePrefix(""));
    assertEquals("reports/20", ObjectKeys.validatePrefix("reports/20"));
    for (String key : new String[]{"", "a/", "/a", "../a", "a/./b", "a//b", "a\\b", "C:/a", "a\n", ".ouroboros-storage/session"}) {
      assertThrows(RuntimeException.class, () -> ObjectKeys.validate(key), key);
    }
  }
  @Test void declaredLengthIsCheckedWithoutClosingCallerInput() throws Exception {
    var source = new ByteArrayInputStream("content".getBytes()) {
      @Override public void close() { fail("Caller owns the input stream"); }
    };
    var output = new ByteArrayOutputStream();
    assertEquals(7, ContentTransfer.copy(source, output, OptionalLong.of(7)));
    assertEquals("content", output.toString());
    assertThrows(RuntimeException.class, () -> ContentTransfer.copy(new ByteArrayInputStream(new byte[2]), output, OptionalLong.of(1)));
    assertThrows(RuntimeException.class, () -> ContentTransfer.copy(new ByteArrayInputStream(new byte[1]), output, OptionalLong.of(2)));
    assertThrows(RuntimeException.class, () -> ContentTransfer.copy(source, output, OptionalLong.of(-1)));
  }
  @Test void rangesTruncateAtEndAndRejectEmptyOrUnsatisfiableSelections() {
    assertEquals(3, new ByteRange(2, 99).length(5));
    assertEquals(1, new ByteRange(4, 4).length(5));
    assertThrows(RuntimeException.class, () -> new ByteRange(-1, 0));
    assertThrows(RuntimeException.class, () -> new ByteRange(2, 1));
    assertThrows(RuntimeException.class, () -> new ByteRange(0, 0).length(0));
    assertThrows(RuntimeException.class, () -> new ByteRange(5, 9).length(5));
  }
  @Test void readOwnsItsStreamAndPageSnapshotCannotBeMutatedByItsProducer() throws Exception {
    var info = new ObjectInfo("key", 3, Optional.empty(), Optional.empty(), Optional.empty());
    var source = new ByteArrayInputStream(new byte[3]) {
      boolean closed;
      @Override public void close() { closed = true; }
      @Override public int available() { return closed ? 0 : super.available(); }
    };
    try (var read = new ObjectRead(info, source, 3)) { assertEquals(3, read.content().available()); }
    assertEquals(0, source.available());
    var mutable = new ArrayList<>(List.of(info)); var page = new ObjectPage(mutable, Optional.empty());
    mutable.clear(); assertEquals(1, page.objects().size());
    assertThrows(UnsupportedOperationException.class, () -> page.objects().clear());
    assertThrows(RuntimeException.class, () -> new ObjectInfo("key", -1, Optional.empty(), Optional.empty(), Optional.empty()));
  }
}
