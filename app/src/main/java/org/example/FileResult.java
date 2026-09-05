package org.example;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

public final class FileResult {
    private final Path rootDir;
    private final long fileCount;
    private final long dirCount;
    private final long totalBytes;
    private final Map<String, ExtensionStat> extensions;

    public FileResult(
        Path rootDir,
        long fileCount,
        long dirCount,
        long totalBytes,
        Map<String, ExtensionStat> extensions
    ) {
        this.rootDir = rootDir;
        this.fileCount = fileCount;
        this.dirCount = dirCount;
        this.totalBytes = totalBytes;
        this.extensions = extensions;
    }

    public Path rootDir() { return rootDir; }
    public long fileCount() { return fileCount; }
    public long dirCount() { return dirCount; }
    public long totalBytes() { return totalBytes; }
    public Map<String, ExtensionStat> extensions() { return extensions; }

    public String formattedTotalSize() {
        if (totalBytes < 1024) return totalBytes + " B";
        int exp = (int) (Math.log(totalBytes) / Math.log(1024));
        char unit = "KMGTPE".charAt(exp - 1);
        return String.format("%.1f %cB", totalBytes / Math.pow(1024, exp), unit);
    }

    public static final class ExtensionStat {
        private final long count;
        private final long totalBytes;

        public ExtensionStat(long count, long totalBytes) {
            this.count = count;
            this.totalBytes = totalBytes;
        }

        public long count() { return count; }
        public long totalBytes() { return totalBytes; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ExtensionStat that)) return false;
            return count == that.count && totalBytes == that.totalBytes;
        }

        @Override
        public int hashCode() {
            return Objects.hash(count, totalBytes);
        }
    }
}
