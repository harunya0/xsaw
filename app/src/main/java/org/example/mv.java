package org.example;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;

public class mv {
    public MoveResult move(Path source, Path target) throws IOException {
        Path src = source.toAbsolutePath();
        Path dest = target.toAbsolutePath();

        if (!Files.exists(src)) {
            throw new FileNotFoundException("Source file does not exist: " + src);
        }

        Path finalDest = dest;
        if (Files.isDirectory(dest)) {
            finalDest = dest.resolve(src.getFileName());
        }

        Path parent = finalDest.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }

        if (Files.exists(finalDest)) {
            throw new FileAlreadyExistsException("Target file already exists: " + finalDest);
        }

        boolean isDir = Files.isDirectory(src);
        long size = isDir ? 0 : Files.size(src);
        Instant now = Instant.now();

        try {
            Files.move(src, finalDest, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(src, finalDest);
        }

        return new MoveResult(src, finalDest, size, now, isDir);
    }
}
