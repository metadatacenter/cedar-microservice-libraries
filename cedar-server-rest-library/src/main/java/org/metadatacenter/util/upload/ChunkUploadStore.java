package org.metadatacenter.util.upload;

import jakarta.ws.rs.BadRequestException;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Process-local Flow.js assembly. A chunk is counted only after its complete payload is on disk. */
public final class ChunkUploadStore {
  public static final long MAX_UPLOAD_BYTES = 5L * 1024 * 1024 * 1024;
  public record Chunk(String fileId, String filename, long number, long chunkSize, long currentSize,
                      long totalSize, long totalChunks, boolean metadata) { }
  public record FileStatus(long totalChunks, long uploadedChunks, String path, boolean metadata) { }
  public record Status(long totalFiles, long uploadedFiles, String folder, Map<String, FileStatus> files) {
    public boolean complete() { return totalFiles == uploadedFiles; }
  }
  private record Key(String owner, String id) { }
  private record FileSpec(String filename, long chunkSize, long totalSize, long totalChunks, boolean metadata) { }
  private static final class FileState {
    final FileSpec spec;
    final Map<Long, byte[]> chunks = new HashMap<>();
    FileState(FileSpec spec) { this.spec = spec; }
  }
  private static final class Upload {
    final long totalFiles;
    final Path root;
    final Map<String, FileState> files = new LinkedHashMap<>();
    boolean claimed;
    boolean retired;
    Upload(long totalFiles, Path root) { this.totalFiles = totalFiles; this.root = root; }
  }
  private final Map<Key, Upload> uploads = new ConcurrentHashMap<>();

  /** Owns and closes input, including on validation failure. Requests for different uploads can proceed concurrently. */
  public String accept(String owner, String id, long totalFiles, Path folder, Chunk chunk, InputStream input)
      throws IOException {
    try (input) {
      require(owner != null && !owner.isBlank() && id != null && !id.isBlank(), "Missing upload identity");
      require(totalFiles > 0, "Invalid number of files");
      require(input != null, "Missing file chunk");
      validate(chunk);
      Path root = folder.toAbsolutePath().normalize();
      Upload upload = uploads.computeIfAbsent(new Key(owner, id), k -> new Upload(totalFiles, root));
      synchronized (upload) {
        require(!upload.retired, "Upload has already been processed");
        require(upload.totalFiles == totalFiles && upload.root.equals(root), "Upload metadata changed");
        String filename = basename(chunk.filename());
        FileSpec spec = new FileSpec(filename, chunk.chunkSize(), chunk.totalSize(), chunk.totalChunks(), chunk.metadata());
        FileState file = upload.files.get(chunk.fileId());
        if (file == null) {
          require(!upload.claimed && upload.files.size() < totalFiles, "Too many files");
          require(upload.files.values().stream().noneMatch(f -> f.spec.filename().equals(filename)), "File name collision");
        } else {
          require(file.spec.equals(spec), "File metadata changed");
        }
        Files.createDirectories(root);
        Path target = root.resolve(filename);
        require(!Files.isSymbolicLink(target), "Upload target is a symbolic link");
        Path staged = Files.createTempFile(root, ".chunk-", ".tmp");
        try {
          byte[] digest = stage(input, staged, chunk.currentSize());
          if (file != null && file.chunks.containsKey(chunk.number())) {
            require(Arrays.equals(file.chunks.get(chunk.number()), digest), "Conflicting chunk retry");
            return target.toString();
          }
          require(!upload.claimed, "Upload is already being processed");
          // No write to the assembled file occurs until length and metadata checks have passed.
          try (var output = java.nio.channels.FileChannel.open(target, StandardOpenOption.CREATE,
              StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
               InputStream bytes = Files.newInputStream(staged)) {
            if (file == null) output.truncate(0); // A restarted process must not reuse stale trailing bytes.
            output.position((chunk.number() - 1) * chunk.chunkSize());
            byte[] buffer = new byte[64 * 1024];
            for (int n; (n = bytes.read(buffer)) != -1;) {
              var block = java.nio.ByteBuffer.wrap(buffer, 0, n);
              while (block.hasRemaining()) output.write(block);
            }
          }
          if (file == null) { file = new FileState(spec); upload.files.put(chunk.fileId(), file); }
          file.chunks.put(chunk.number(), digest);
          return target.toString();
        } finally {
          Files.deleteIfExists(staged);
        }
      }
    }
  }

  private static byte[] stage(InputStream input, Path staged, long expected) throws IOException {
    MessageDigest digest;
    try { digest = MessageDigest.getInstance("SHA-256"); }
    catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    try (OutputStream output = Files.newOutputStream(staged)) {
      byte[] buffer = new byte[64 * 1024];
      long remaining = expected;
      while (remaining > 0) {
        int n = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
        require(n != -1, "File chunk is shorter than declared");
        output.write(buffer, 0, n); digest.update(buffer, 0, n); remaining -= n;
      }
      require(input.read() == -1, "File chunk is longer than declared");
    }
    return digest.digest();
  }

  private static void validate(Chunk c) {
    require(c != null && c.fileId() != null && !c.fileId().isBlank(), "Missing file identifier");
    basename(c.filename());
    require(c.chunkSize() > 0 && c.chunkSize() <= MAX_UPLOAD_BYTES, "Invalid chunk size");
    require(c.totalSize() >= 0 && c.totalSize() <= MAX_UPLOAD_BYTES, "Invalid total file size");
    // Flow.js supports either fixed chunks or a final chunk containing the small remainder.
    long floor = Math.max(c.totalSize() / c.chunkSize(), 1);
    long ceil = Math.max((c.totalSize() + c.chunkSize() - 1) / c.chunkSize(), 1);
    require(c.totalChunks() == floor || c.totalChunks() == ceil, "Invalid total chunk count");
    require(c.number() >= 1 && c.number() <= c.totalChunks(), "Invalid chunk number");
    long offset = (c.number() - 1) * c.chunkSize(); // Bounded by totalSize after the checks above.
    long expected = c.number() == c.totalChunks() ? c.totalSize() - offset : c.chunkSize();
    require(c.currentSize() == expected, "Invalid current chunk size");
  }

  public static String basename(String raw) {
    require(raw != null, "Missing file name");
    String name = raw.replace('\\', '/');
    name = name.substring(name.lastIndexOf('/') + 1);
    require(!name.isEmpty() && !name.equals(".") && !name.equals("..") && name.indexOf('\0') < 0,
        "Invalid file name");
    return name;
  }

  public Status status(String owner, String id) {
    Upload upload = uploads.get(new Key(owner, id));
    if (upload == null) return null;
    synchronized (upload) { return upload.retired ? null : snapshot(upload); }
  }
  private static Status snapshot(Upload upload) {
    Map<String, FileStatus> files = new LinkedHashMap<>();
    long complete = 0;
    for (var entry : upload.files.entrySet()) {
      FileState f = entry.getValue();
      if (f.chunks.size() == f.spec.totalChunks()) complete++;
      files.put(entry.getKey(), new FileStatus(f.spec.totalChunks(), f.chunks.size(),
          upload.root.resolve(f.spec.filename()).toString(), f.spec.metadata()));
    }
    return new Status(upload.totalFiles, complete, upload.root.toString(), Map.copyOf(files));
  }
  /** Atomically grants a single caller permission to start processing a complete upload. */
  public boolean claimComplete(String owner, String id) {
    Upload upload = uploads.get(new Key(owner, id));
    if (upload == null) return false;
    synchronized (upload) {
      if (upload.retired || upload.claimed || !snapshot(upload).complete()) return false;
      upload.claimed = true;
      return true;
    }
  }
  public void releaseClaim(String owner, String id) {
    Upload upload = uploads.get(new Key(owner, id));
    if (upload != null) synchronized (upload) { if (!upload.retired) upload.claimed = false; }
  }
  /** Retain the consumed identity until restart so a late retry cannot submit a one-chunk file twice. */
  public void retire(String owner, String id) {
    Upload upload = uploads.get(new Key(owner, id));
    if (upload != null) synchronized (upload) { upload.retired = true; upload.files.clear(); }
  }
  private static void require(boolean valid, String message) {
    if (!valid) throw new BadRequestException(message);
  }
}
