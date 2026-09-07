package mv;

import java.nio.file.Path;
import java.time.Instant;

public record MoveResult(
    Path source,
    Path destination,
    long sizeBytes,
    Instant timestamp,
    boolean isDirectory,
    MoveStatus status,
    String trashUuid
) {
    public MoveResult(Path source, Path destination, long sizeBytes, Instant timestamp, boolean isDirectory, MoveStatus status) {
        this(source, destination, sizeBytes, timestamp, isDirectory, status, null);
    }
}
