package du;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

public final class FileResult {
    private final Path rootDir;
    private final long fileCount;
    private final long dirCount;
    private final long totalBytes;
    private final Map<String, ExtensionStat> extensions;
    private final long elapsedMillis;

    public FileResult(
        Path rootDir,
        long fileCount,
        long dirCount,
        long totalBytes,
        Map<String, ExtensionStat> extensions,
        long elapsedMillis
    ) {
        this.rootDir = rootDir;
        this.fileCount = fileCount;
        this.dirCount = dirCount;
        this.totalBytes = totalBytes;
        this.extensions = extensions;
        this.elapsedMillis = elapsedMillis;
    }

    public Path rootDir() { return rootDir; }
    public long fileCount() { return fileCount; }
    public long dirCount() { return dirCount; }
    public long totalBytes() { return totalBytes; }
    public long elapsedMillis() { return elapsedMillis; }
    public Map<String, ExtensionStat> extensions() { return extensions; }

    public String formattedTotalSize() {
        if (totalBytes < 1024) return totalBytes + " B";
        int exp = (int) (Math.log(totalBytes) / Math.log(1024));
        char unit = "KMGTPE".charAt(exp - 1);
        return String.format("%.1f %cB", totalBytes / Math.pow(1024, exp), unit);
    }

    public String formattedElapsed() {
        if (elapsedMillis < 1000) {
            return elapsedMillis + " ms";
        } else if (elapsedMillis < 60_000) {
            return String.format("%.2f s", elapsedMillis / 1000.0);
        } else {
            long minutes = elapsedMillis / 60_000;
            long seconds = (elapsedMillis % 60_000) / 1000;
            return String.format("%dm %02ds", minutes, seconds);
        }
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
