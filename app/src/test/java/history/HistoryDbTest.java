package history;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class HistoryDbTest {

    @TempDir
    Path tempDir;

    private HistoryDb db;
    private Path dbPath;

    @BeforeEach
    void setUp() {
        dbPath = tempDir.resolve("test_history.db");
        db = new HistoryDb(dbPath);
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (db != null) {
            db.close();
        }
    }

    @Test
    void testInitSchemaAndWalMode() throws SQLException {
        try (Statement stmt = db.getConnection().createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA journal_mode;")) {
            assertTrue(rs.next());
            assertEquals("wal", rs.getString(1).toLowerCase());
        }
    }

    @Test
    void testRecordSingleAndFindByBatchId() throws SQLException {
        String batchId = UUID.randomUUID().toString();
        OperationRecord rec = OperationRecord.create(
            batchId,
            OperationType.MOVE,
            "/path/to/src.txt",
            "/path/to/dest.txt",
            null,
            1024L,
            false
        );

        long id = db.record(rec);
        assertTrue(id > 0);

        List<OperationRecord> records = db.findByBatchId(batchId);
        assertEquals(1, records.size());

        OperationRecord fetched = records.get(0);
        assertEquals(id, fetched.id());
        assertEquals(batchId, fetched.batchID());
        assertEquals(OperationType.MOVE, fetched.operationType());
        assertEquals("/path/to/src.txt", fetched.sourcePath());
        assertEquals("/path/to/dest.txt", fetched.destinationPath());
        assertNull(fetched.trashUuid());
        assertEquals(1024L, fetched.fileSize());
        assertFalse(fetched.isDirectory());
        assertEquals(OperationStatus.ACTIVE, fetched.status());
        assertNotNull(fetched.timestamp());
    }

    @Test
    void testRecordBatch() throws SQLException {
        String batchId = UUID.randomUUID().toString();
        OperationRecord r1 = OperationRecord.create(batchId, OperationType.MOVE, "/src1", "/dest1", null, 10, false);
        OperationRecord r2 = OperationRecord.create(batchId, OperationType.REMOVE, "/src2", null, "uuid-1234", 20, false);

        List<Long> ids = db.recordBatch(List.of(r1, r2));
        assertEquals(2, ids.size());

        List<OperationRecord> results = db.findByBatchId(batchId);
        assertEquals(2, results.size());
        assertEquals(OperationType.MOVE, results.get(0).operationType());
        assertEquals(OperationType.REMOVE, results.get(1).operationType());
        assertEquals("uuid-1234", results.get(1).trashUuid());
    }

    @Test
    void testFindLatestBatchIdAndStatusUpdate() throws SQLException {
        String batch1 = "batch-1";
        String batch2 = "batch-2";

        db.record(OperationRecord.create(batch1, OperationType.MOVE, "/s1", "/d1", null, 1, false));
        db.record(OperationRecord.create(batch2, OperationType.MOVE, "/s2", "/d2", null, 2, false));

        Optional<String> latest = db.findLatestBatchId();
        assertTrue(latest.isPresent());
        assertEquals(batch2, latest.get());

        // batch2 を RESTORED に更新
        db.updateBatchStatus(batch2, OperationStatus.RESTORED);

        // 次の最新 ACTIVE は batch1 になる
        Optional<String> afterUpdate = db.findLatestBatchId();
        assertTrue(afterUpdate.isPresent());
        assertEquals(batch1, afterUpdate.get());
    }

    @Test
    void testFindRecent() throws SQLException {
        for (int i = 1; i <= 5; i++) {
            db.record(OperationRecord.create("batch-" + i, OperationType.MOVE, "/s" + i, "/d" + i, null, i, false));
        }

        List<OperationRecord> recent3 = db.findRecent(3);
        assertEquals(3, recent3.size());
        assertEquals("batch-5", recent3.get(0).batchID());
        assertEquals("batch-4", recent3.get(1).batchID());
        assertEquals("batch-3", recent3.get(2).batchID());
    }

    @Test
    void testFindOlderThan() throws SQLException {
        Instant now = Instant.now();
        Instant oldTime = now.minus(35, ChronoUnit.DAYS);

        OperationRecord oldRecord = new OperationRecord(
            0,
            "old-batch",
            OperationType.REMOVE,
            "/old/file.txt",
            null,
            "old-uuid",
            100L,
            false,
            OperationStatus.ACTIVE,
            oldTime
        );
        OperationRecord newRecord = OperationRecord.create(
            "new-batch",
            OperationType.REMOVE,
            "/new/file.txt",
            null,
            "new-uuid",
            200L,
            false
        );

        db.recordBatch(List.of(oldRecord, newRecord));

        Instant cutoff30Days = now.minus(30, ChronoUnit.DAYS);
        List<OperationRecord> expired = db.findOlderThan(cutoff30Days);

        assertEquals(1, expired.size());
        assertEquals("old-uuid", expired.get(0).trashUuid());
    }

    @Test
    void testFindLatestRestoredBatchId() throws SQLException {
        db.record(OperationRecord.create("batch-1", OperationType.MOVE, "/s1", "/d1", null, 1, false));
        db.record(OperationRecord.create("batch-2", OperationType.MOVE, "/s2", "/d2", null, 2, false));

        // 初期状態では RESTORED は存在しない
        assertTrue(db.findLatestRestoredBatchId().isEmpty());

        // batch-1 を RESTORED に更新
        db.updateBatchStatus("batch-1", OperationStatus.RESTORED);
        assertEquals("batch-1", db.findLatestRestoredBatchId().orElse(null));

        // batch-2 も RESTORED に更新（最新は batch-2 になる）
        db.updateBatchStatus("batch-2", OperationStatus.RESTORED);
        assertEquals("batch-2", db.findLatestRestoredBatchId().orElse(null));
    }

    @Test
    void testFindByBatchIdReverse() throws SQLException {
        String batch = "batch-rev";
        db.record(OperationRecord.create(batch, OperationType.MOVE, "/file1", "/dest1", null, 1, false));
        db.record(OperationRecord.create(batch, OperationType.MOVE, "/file2", "/dest2", null, 2, false));

        List<OperationRecord> asc = db.findByBatchId(batch, false);
        assertEquals("/file1", asc.get(0).sourcePath());
        assertEquals("/file2", asc.get(1).sourcePath());

        List<OperationRecord> desc = db.findByBatchId(batch, true);
        assertEquals("/file2", desc.get(0).sourcePath());
        assertEquals("/file1", desc.get(1).sourcePath());
    }

    @Test
    void testFindByIdentifierAndRecordStatus() throws SQLException {
        String batch = "batch-xyz";
        String trashUuid = "trash-uuid-999";
        long id = db.record(OperationRecord.create(batch, OperationType.REMOVE, "/del.txt", null, trashUuid, 50, false));

        // 1. レコードID（数値文字列）で検索
        List<OperationRecord> byId = db.findByIdentifier(String.valueOf(id));
        assertEquals(1, byId.size());
        assertEquals(id, byId.get(0).id());

        // 2. batch_id で検索
        List<OperationRecord> byBatch = db.findByIdentifier(batch);
        assertEquals(1, byBatch.size());
        assertEquals(id, byBatch.get(0).id());

        // 3. trash_uuid で検索
        List<OperationRecord> byTrash = db.findByIdentifier(trashUuid);
        assertEquals(1, byTrash.size());
        assertEquals(id, byTrash.get(0).id());

        // 単一レコードのステータス更新 & findById
        db.updateRecordStatus(id, OperationStatus.RESTORED);
        Optional<OperationRecord> updated = db.findById(id);
        assertTrue(updated.isPresent());
        assertEquals(OperationStatus.RESTORED, updated.get().status());
    }
}
