package undo;

import history.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import rm.rm;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class UndoEngineTest {

    @TempDir
    Path tempDir;

    private HistoryDb db;
    private TrashVault vault;
    private UndoEngine engine;

    @BeforeEach
    void setUp() {
        Path dbPath = tempDir.resolve("test_history.db");
        Path trashDir = tempDir.resolve(".xsaw/trash");
        db = new HistoryDb(dbPath);
        vault = new TrashVault(trashDir);
        engine = new UndoEngine(db, vault);
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (db != null) {
            db.close();
        }
    }

    @Test
    void testUndoRemove() throws Exception {
        Path file = Files.writeString(tempDir.resolve("delete_me.txt"), "hello undo");
        rm remover = new rm(vault, db);
        remover.removeAll(List.of(file), false);

        assertFalse(Files.exists(file));

        String batchId = db.findLatestActiveBatchId().orElseThrow();
        int undone = engine.undoBatch(batchId, false);
        assertEquals(1, undone);

        assertTrue(Files.exists(file));
        assertEquals("hello undo", Files.readString(file));

        List<OperationRecord> records = db.findByBatchId(batchId);
        assertEquals(OperationStatus.RESTORED, records.get(0).status());
    }

    @Test
    void testUndoMove() throws Exception {
        Path src = Files.writeString(tempDir.resolve("src.txt"), "move content");
        Path dest = tempDir.resolve("dest.txt");
        Files.move(src, dest);

        String batchId = UUID.randomUUID().toString();
        OperationRecord rec = OperationRecord.create(
            batchId, OperationType.MOVE, src.toString(), dest.toString(), null, Files.size(dest), false
        );
        db.record(rec);

        assertTrue(Files.exists(dest));
        assertFalse(Files.exists(src));

        int undone = engine.undoBatch(batchId, false);
        assertEquals(1, undone);

        assertTrue(Files.exists(src));
        assertFalse(Files.exists(dest));
        assertEquals("move content", Files.readString(src));
    }

    @Test
    void testUndoMoveWithOverwrite() throws Exception {
        Path src = Files.writeString(tempDir.resolve("new_version.txt"), "new data");
        Path dest = Files.writeString(tempDir.resolve("existing.txt"), "old data");

        // dest をゴミ箱へ退避して src を dest に移動
        UUID trashUuid = UUID.randomUUID();
        vault.moveToTrash(dest, trashUuid);
        Files.move(src, dest);

        String batchId = UUID.randomUUID().toString();
        OperationRecord rec = OperationRecord.create(
            batchId, OperationType.MOVE, src.toString(), dest.toString(), trashUuid.toString(), 100L, false
        );
        db.record(rec);

        int undone = engine.undoBatch(batchId, false);
        assertEquals(1, undone);

        // src と dest の両方が元の内容で復元されていること
        assertTrue(Files.exists(src));
        assertEquals("new data", Files.readString(src));
        assertTrue(Files.exists(dest));
        assertEquals("old data", Files.readString(dest));
    }

    @Test
    void testRedoBatch() throws Exception {
        Path file = Files.writeString(tempDir.resolve("redo_test.txt"), "redo data");
        rm remover = new rm(vault, db);
        remover.removeAll(List.of(file), false);

        String batchId = db.findLatestActiveBatchId().orElseThrow();
        engine.undoBatch(batchId, false);
        assertTrue(Files.exists(file));

        // 巻き戻しを進める (redo)
        int redone = engine.redoBatch(batchId, false);
        assertEquals(1, redone);
        assertFalse(Files.exists(file));

        List<OperationRecord> records = db.findByBatchId(batchId);
        assertEquals(OperationStatus.ACTIVE, records.get(0).status());
    }

    @Test
    void testUndoPurgedThrows() throws Exception {
        String batchId = UUID.randomUUID().toString();
        OperationRecord rec = new OperationRecord(
            0, batchId, OperationType.REMOVE, "/path/purged.txt", null, "uuid-xxx", 100L, false, OperationStatus.PURGED, java.time.Instant.now()
        );
        db.record(rec);

        assertThrows(IllegalStateException.class, () -> engine.undoBatch(batchId, false));
    }
}
