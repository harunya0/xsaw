package rm;

import history.*;
import java.io.IOException;
import java.nio.file.*;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class rm {
    private final TrashVault vault;
    private final HistoryDb db;

    public rm() {
        this(new TrashVault(), new HistoryDb());
    }

    public rm(TrashVault vault, HistoryDb db) {
        this.vault = vault;
        this.db = db;
    }

    public List<RemoveResult> removeAll(
        List<Path> targets,
        boolean recursive
    ) throws IOException, SQLException {
        List<RemoveResult> results = new ArrayList<>();
        List<OperationRecord> records = new ArrayList<>();
        String batchId = UUID.randomUUID().toString();

        for (Path target : targets) {
            Path absPath = target.toAbsolutePath().normalize();
            if (!Files.exists(absPath)) {
                throw new NoSuchFileException(absPath.toString());
            }

            boolean isDir = Files.isDirectory(absPath);
            if (isDir && !recursive) {
                throw new IOException("Cannot remove directory without -r / --recursive: " + target);
            }

            long size = isDir ? 0 : Files.size(absPath);
            UUID trashUuid = UUID.randomUUID();

            vault.moveToTrash(absPath, trashUuid);

            OperationRecord rec = OperationRecord.create(
                batchId,
                OperationType.REMOVE,
                absPath.toString(),
                null,
                trashUuid.toString(),
                size,
                isDir
            );
            records.add(rec);
            results.add(new RemoveResult(absPath, size, isDir, trashUuid));
        }

        if (!records.isEmpty()) {
            db.recordBatch(records);
        }

        try {
            vault.purgeExpired(Duration.ofDays(30), db);
        } catch (IOException | SQLException ignored) {
            // パージ処理の例外は削除処理本体には影響させない
        }
        return results;
    }
}
