package undo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import history.HistoryDb;
import history.OperationRecord;
import history.OperationStatus;
import history.OperationType;
import history.TrashVault;

public class UndoEngine {
    private final HistoryDb db;
    private final TrashVault vault;

    public UndoEngine(HistoryDb db, TrashVault vault) {
        this.db = db;
        this.vault = vault;
    }

    public int undoBatch(
        String identifier,
        boolean force   
    ) throws SQLException, IOException {
        List<OperationRecord> records = db.findByIdentifier(identifier);
        if (records.isEmpty()) {
            throw new IllegalArgumentException("No records found for identifier: " + identifier);
        }

        int count = 0;
        for (OperationRecord rec : records) {
            if (rec.status() == OperationStatus.PURGED) {
                throw new IllegalStateException("Cannot undo: item was permanently purged from trash (ID: " + rec.id() + ")");
            }
            if (rec.status() == OperationStatus.RESTORED) {
                continue; // Skip already restored records
            }

            undoRecord(rec, force);
            db.updateRecordStatus(rec.id(), OperationStatus.RESTORED);
            count++;
        }
        return count;
    }

    private void undoRecord(
        OperationRecord record,
        boolean force
    ) throws IOException {
        Path src = Path.of(record.sourcePath());
        if (src.getParent() != null) {
            Files.createDirectories(src.getParent());
        }

        if (record.operationType() == OperationType.REMOVE) {
            if (Files.exists(src)) {
                if (!force) {
                    throw new IOException("Restore target already exists: " + src);
                }
                Files.deleteIfExists(src);
            }
            vault.restoreFromTrash(UUID.fromString(record.trashUuid()), src);
        } else if (record.operationType() == OperationType.MOVE) {
            Path dest = Path.of(record.destinationPath());
            if (!Files.exists(dest)) {
                throw new IOException("Source file to undo not found: " + dest);
            }
            if (Files.exists(src)) {
                if (!force) {
                    throw new IOException("Restore target already exists: " + src);
                }
                Files.move(dest, src, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.move(dest, src);
            }

            if (record.trashUuid() != null) {
                vault.restoreFromTrash(UUID.fromString(record.trashUuid()), dest);
            }
        }
    }

    public int redoBatch(
        String identifier,
        boolean force
    ) throws SQLException, IOException {
        List<OperationRecord> records = db.findByIdentifier(identifier);
        if (records.isEmpty()) {
            throw new IllegalArgumentException("No records found for identifier: " + identifier);
        }

        int count = 0;
        for (OperationRecord rec : records) {
            if (rec.status() != OperationStatus.RESTORED) {
                continue; // Only redo records that were restored
            }

            redoRecord(rec, force);
            db.updateRecordStatus(rec.id(), OperationStatus.ACTIVE);
            count++;
        }
        return count;
    }

    private void redoRecord(
        OperationRecord record,
        boolean force
    ) throws IOException {
        Path src = Path.of(record.sourcePath());
        if (record.operationType() == OperationType.REMOVE) {
            if (!Files.exists(src)) {
                throw new IOException("Source file to redo not found: " + src);
            }
            vault.moveToTrash(src, UUID.fromString(record.trashUuid()));
        } else if (record.operationType() == OperationType.MOVE) {
            Path dest = Path.of(record.destinationPath());
            if (!Files.exists(src)) {
                throw new IOException("Source file to redo not found: " + src);
            }
            if (dest.getParent() != null) {
                Files.createDirectories(dest.getParent());
            }

            if (record.trashUuid() != null && Files.exists(dest)) {
                vault.moveToTrash(dest, UUID.fromString(record.trashUuid()));
            }

            if (Files.exists(dest)) {
                if (!force) {
                    throw new IOException("Destination already exists: " + dest);
                }
                Files.move(src, dest, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.move(src, dest);
            }
        }
    }
}
