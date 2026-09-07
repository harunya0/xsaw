package history;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

public class TrashVault {
    private final Path trashDir;

    public TrashVault() {
        this(XsawPaths.getTrashDir());
    }

    public TrashVault(Path trashDir) {
        this.trashDir = trashDir;
    }

    public Path getTrashDir() {
        return trashDir;
    }

    public synchronized Path moveToTrash(
        Path source,
        UUID uuid
    ) throws IOException {
        if (!Files.exists(source)) {
            throw new IOException("Source file does not exist: " + source);
        }

        Files.createDirectories(trashDir);
        Path dest = trashDir.resolve(uuid.toString());

        try {
            Files.move(source, dest, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, dest, StandardCopyOption.REPLACE_EXISTING);
        }

        return dest;
    }

    public synchronized Path restoreFromTrash(
        UUID uuid,
        Path destination
    ) throws IOException {
        Path trashPath = trashDir.resolve(uuid.toString());
        if (!Files.exists(trashPath)) {
            throw new IOException("Trash item not found for UUID: " + uuid);
        }

        Path parent = destination.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }

        try {
            Files.move(trashPath, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(trashPath, destination);
        }
        return destination;
    }

    public synchronized int purgeExpired(
        Duration retention,
        HistoryDb db
    ) throws SQLException, IOException {
        Instant cutoff = Instant.now().minus(retention);
        List<OperationRecord> expiredRecords = db.findOlderThan(cutoff);

        int count = 0;
        for (OperationRecord rec : expiredRecords) {
            if (rec.trashUuid() != null) {
                Path target = trashDir.resolve(rec.trashUuid());
                if (Files.exists(target)) {
                    deleteRecursively(target);
                    count++;
                }
                db.updateBatchStatus(rec.batchID(), OperationStatus.PURGED);
            }
        }
        return count;
    }

    public synchronized int purgeAll(HistoryDb db) throws SQLException, IOException {
        if (!Files.exists(trashDir)) {
            return 0;
        }

        int count = 0;
        try (Stream<Path> stream = Files.list(trashDir)) {
            List<Path> items = stream.toList();
            for (Path item : items) {
                deleteRecursively(item);
                count++;
            }
        }

        if (db != null) {
            db.updateAllActiveTrashToPurged();
        }

        return count;
    }

    private void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        if (Files.isDirectory(path)) {
            try (Stream<Path> walk = Files.walk(path)) {
                walk.sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {}
                    });
            }
        } else {
            Files.deleteIfExists(path);
        }
    }
}
