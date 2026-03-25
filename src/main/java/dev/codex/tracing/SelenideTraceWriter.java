package dev.codex.tracing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class SelenideTraceWriter {
  private static final ObjectMapper MAPPER = new ObjectMapper()
      .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  private final Path directory;
  private final Path traceFile;

  SelenideTraceWriter(Path directory) {
    this.directory = directory;
    this.traceFile = directory.resolve("trace.trace");
  }

  Path directory() {
    return directory;
  }

  void initialize() throws IOException {
    Files.createDirectories(directory);
    Files.createDirectories(directory.resolve("resources"));
    Files.deleteIfExists(traceFile);
    Files.createFile(traceFile);
  }

  synchronized void writeEvent(SelenideTraceEvent event) {
    try (BufferedWriter writer = Files.newBufferedWriter(
        traceFile,
        StandardCharsets.UTF_8,
        StandardOpenOption.CREATE,
        StandardOpenOption.APPEND)) {
      writer.write(MAPPER.writeValueAsString(event));
      writer.newLine();
    } catch (IOException exception) {
      throw new IllegalStateException("Failed to write trace event", exception);
    }
  }

  Path writeResource(String relativePath, byte[] content) {
    Path path = directory.resolve(relativePath);
    try {
      Files.createDirectories(path.getParent());
      Files.write(path, content);
      return path;
    } catch (IOException exception) {
      throw new IllegalStateException("Failed to write trace resource " + relativePath, exception);
    }
  }

  Path writeTextResource(String relativePath, String content) {
    return writeResource(relativePath, content.getBytes(StandardCharsets.UTF_8));
  }

  Path zipTo(Path zipPath) {
    try {
      Files.createDirectories(zipPath.getParent());
      try (OutputStream outputStream = Files.newOutputStream(zipPath);
           ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream);
           Stream<Path> paths = Files.walk(directory)) {
        paths
            .filter(Files::isRegularFile)
            .forEach(path -> {
              String entryName = directory.relativize(path).toString().replace('\\', '/');
              try {
                zipOutputStream.putNextEntry(new ZipEntry(entryName));
                Files.copy(path, zipOutputStream);
                zipOutputStream.closeEntry();
              } catch (IOException exception) {
                throw new IllegalStateException("Failed to zip trace artifact", exception);
              }
            });
      }
      return zipPath;
    } catch (IOException exception) {
      throw new IllegalStateException("Failed to build trace archive", exception);
    }
  }
}
