package org.metadatacenter.util.upload;

import jakarta.ws.rs.BadRequestException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

class ChunkUploadMatrixTest {
  @TempDir Path root;
  ChunkUploadStore store = new ChunkUploadStore();
  static ChunkUploadStore.Chunk chunk(long number, long size, long current, long total, long count) {
    return new ChunkUploadStore.Chunk("file", "data.xml", number, size, current, total, count, false);
  }
  String accept(ChunkUploadStore.Chunk chunk, byte[] bytes) throws IOException {
    return store.accept("owner", "upload", 1, root, chunk, new ByteArrayInputStream(bytes));
  }
  static Stream<Arguments> layouts() {
    return Stream.of(0, 1, 3, 4, 5, 7, 8, 9).flatMap(size -> Stream.of(false, true)
        .flatMap(fixed -> Stream.of(false, true).map(reverse -> Arguments.of(size, fixed, reverse))));
  }
  @ParameterizedTest(name="bytes={0}, fixed={1}, reverse={2}") @MethodSource("layouts")
  void assemblesBothFlowLayoutsAndIgnoresIdenticalRetries(int total, boolean fixed, boolean reverse) throws Exception {
    byte[] bytes = new byte[total]; for (int i = 0; i < total; i++) bytes[i] = (byte) (i + 1);
    int count = Math.max(fixed ? (total + 3) / 4 : total / 4, 1);
    List<Integer> order = new ArrayList<>(); for (int n = 1; n <= count; n++) order.add(n);
    if (reverse) Collections.reverse(order);
    int received = 0;
    for (int n : order) {
      int from = (n - 1) * 4, to = n == count ? total : from + 4;
      var chunk = chunk(n, 4, to - from, total, count);
      byte[] payload = Arrays.copyOfRange(bytes, from, to);
      accept(chunk, payload); accept(chunk, payload); received++;
      assertEquals(received, store.status("owner", "upload").files().get("file").uploadedChunks());
      assertEquals(received == count, store.status("owner", "upload").complete());
    }
    assertArrayEquals(bytes, Files.readAllBytes(root.resolve("data.xml")));
    assertTrue(store.claimComplete("owner", "upload"));
    assertFalse(store.claimComplete("owner", "upload"));
    store.releaseClaim("owner", "upload");
    assertTrue(store.claimComplete("owner", "upload"));
    store.retire("owner", "upload");
    assertNull(store.status("owner", "upload"));
    assertThrows(BadRequestException.class, () -> accept(chunk(1, 4, total, total, 1), bytes));
  }
  static Stream<Arguments> invalid() {
    return Stream.of(
        Arguments.of("zero number", chunk(0,4,4,8,2), new byte[4]),
        Arguments.of("number beyond end", chunk(3,4,4,8,2), new byte[4]),
        Arguments.of("zero chunk size", chunk(1,0,4,8,2), new byte[4]),
        Arguments.of("negative chunk size", chunk(1,-1,4,8,2), new byte[4]),
        Arguments.of("offset overflow", chunk(Long.MAX_VALUE,Long.MAX_VALUE,4,8,2), new byte[4]),
        Arguments.of("negative total", chunk(1,4,4,-1,2), new byte[4]),
        Arguments.of("oversize total", chunk(1,4,4,ChunkUploadStore.MAX_UPLOAD_BYTES+1,2), new byte[4]),
        Arguments.of("zero chunk count", chunk(1,4,4,8,0), new byte[4]),
        Arguments.of("wrong chunk count", chunk(1,4,4,8,3), new byte[4]),
        Arguments.of("wrong current size", chunk(1,4,5,8,2), new byte[5]),
        Arguments.of("short payload", chunk(1,4,4,8,2), new byte[3]),
        Arguments.of("long payload", chunk(1,4,4,8,2), new byte[5]),
        Arguments.of("missing payload", chunk(1,4,4,8,2), null));
  }
  @ParameterizedTest(name="{0}") @MethodSource("invalid")
  void rejectsInvalidChunksWithoutCountingOrWriting(String name, ChunkUploadStore.Chunk chunk, byte[] bytes) throws Exception {
    assertThrows(BadRequestException.class, () -> store.accept("owner", "upload", 1, root, chunk,
        bytes == null ? null : new ByteArrayInputStream(bytes)));
    var status = store.status("owner", "upload");
    assertTrue(status == null || status.files().isEmpty());
    assertFalse(Files.exists(root.resolve("data.xml")));
    try (var paths = Files.list(root)) { assertEquals(0, paths.count()); }
  }
  @Test void missingChunkCannotBeReplacedByADuplicateOrConflictingMetadata() throws Exception {
    accept(chunk(1,4,4,8,2), new byte[]{1,2,3,4});
    accept(chunk(1,4,4,8,2), new byte[]{1,2,3,4});
    assertFalse(store.claimComplete("owner", "upload"));
    assertThrows(BadRequestException.class, () -> accept(chunk(1,4,4,8,2), new byte[]{9,9,9,9}));
    assertThrows(BadRequestException.class, () -> accept(chunk(2,4,3,7,2), new byte[3]));
    assertThrows(BadRequestException.class, () -> store.accept("owner", "upload", 2, root,
        chunk(2,4,4,8,2), new ByteArrayInputStream(new byte[4])));
    assertArrayEquals(new byte[]{1,2,3,4}, Files.readAllBytes(root.resolve("data.xml")));
    assertEquals(1, store.status("owner", "upload").files().get("file").uploadedChunks());
  }
  @Test void ownersAndFilesHaveIndependentCompletionAndCannotAliasNames() throws Exception {
    var first = chunk(1,4,4,4,1);
    store.accept("a","same",2,root.resolve("a"),first,new ByteArrayInputStream(new byte[4]));
    store.accept("b","same",1,root.resolve("b"),first,new ByteArrayInputStream(new byte[]{2,2,2,2}));
    assertFalse(store.status("a","same").complete()); assertTrue(store.status("b","same").complete());
    var collision = new ChunkUploadStore.Chunk("second", "path/data.xml",1,4,4,4,1,true);
    assertThrows(BadRequestException.class, () -> store.accept("a","same",2,root.resolve("a"),collision,new ByteArrayInputStream(new byte[4])));
    var second = new ChunkUploadStore.Chunk("second", "metadata.xml",1,4,4,4,1,true);
    store.accept("a","same",2,root.resolve("a"),second,new ByteArrayInputStream(new byte[4]));
    assertTrue(store.status("a","same").complete());
    assertTrue(store.status("a","same").files().get("second").metadata());
    assertThrows(UnsupportedOperationException.class, () -> store.status("a","same").files().clear());
  }
  @Test void simultaneousFinalChunksGrantOneCompletion() throws Exception {
    ExecutorService workers = Executors.newFixedThreadPool(4);
    try {
      List<Callable<Boolean>> requests = new ArrayList<>();
      for (int i=0;i<16;i++) requests.add(() -> { accept(chunk(1,4,4,4,1),new byte[]{1,2,3,4}); return store.claimComplete("owner","upload"); });
      int claims = 0;
      for (var answer : workers.invokeAll(requests)) if (answer.get()) claims++;
      assertEquals(1,claims);
      assertArrayEquals(new byte[]{1,2,3,4}, Files.readAllBytes(root.resolve("data.xml")));
    } finally { workers.shutdownNow(); }
  }
  @Test void invalidInputClosesAndOldFilesAreTruncated() throws Exception {
    var closed = new java.util.concurrent.atomic.AtomicBoolean();
    var input = new ByteArrayInputStream(new byte[4]) { @Override public void close() { closed.set(true); } };
    assertThrows(BadRequestException.class, () -> store.accept("owner","upload",1,root,chunk(0,4,4,4,1),input));
    assertTrue(closed.get());
    Files.write(root.resolve("data.xml"),new byte[100]);
    accept(chunk(1,4,4,4,1),new byte[]{1,2,3,4});
    assertEquals(4,Files.size(root.resolve("data.xml")));
  }
  @Test void symbolicLinkTargetCannotBeOverwritten() throws Exception {
    Path elsewhere = root.resolve("elsewhere"); Files.writeString(elsewhere,"keep");
    Files.createSymbolicLink(root.resolve("data.xml"),elsewhere);
    assertThrows(BadRequestException.class, () -> accept(chunk(1,4,4,4,1),new byte[4]));
    assertEquals("keep",Files.readString(elsewhere));
  }
}
