package org.example;

import java.nio.file.Path;
import java.time.Instant;

public record MoveResult(
    Path source,
    Path destination,
    long sizeBytes,
    Instant timestamp,
    boolean isDirectory,
    MoveStatus status
) {
}
