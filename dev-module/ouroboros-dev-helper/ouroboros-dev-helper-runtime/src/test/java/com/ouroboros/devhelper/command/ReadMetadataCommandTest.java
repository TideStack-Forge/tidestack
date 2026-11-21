package com.ouroboros.devhelper.command;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.stream.Stream;
import com.ouroboros.devhelper.metadata.*;
import com.ouroboros.devhelper.metadata.MetadataRefreshProtocol.HelperIdentity;
import org.junit.jupiter.api.Test;

class ReadMetadataCommandTest {
  @Test void readsLoadedMetadataAndSharesRevisionWithRefresh() {
    var identity = new HelperIdentity("helper", 10, "app");
    var snapshots = new HelperMetadataSnapshots(Stream::empty, identity);
    var read = new ReadMetadataCommand(snapshots);
    var request = new CommandRequest().setCommand("readMetadata").setAppName("crm").setSequenceId("a")
        .setParams(MetadataRefreshProtocol.refreshParameters(null, identity, "op"))
        .setHelperInstanceId("helper").setHelperStartedAtEpochMillis(10L).setAppIncarnationId("app").setRefreshOperationId("op");
    var result = read.execute(request).join();
    assertTrue(result.getSuccess());
    assertEquals(1, ((MetadataRefreshSnapshot)result.getData()).getRefreshRevision());
    assertEquals(2, snapshots.collect(request).getRefreshRevision());
    request.setHelperInstanceId("another");
    assertFalse(read.execute(request).join().getSuccess());
  }
}
