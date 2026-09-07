package history;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TrashVaultTest {

    @TempDir
    Path tempDir;

    private Path trashDir;
    private TrashVault vault;
    private HistoryDb db;

    @BeforeEach
    void setUp() {
        trashDir = tempDir.resolve("trash");
        vault = new TrashVault(trashDir);
        db = new HistoryDb(tempDir.resolve("test_history.db"));
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (db != null) {
            db.close();
        }
    }

    @Test
    void testMoveToTrashAndRestoreFile() throws IOException {
        Path src = Files.writeString(tempDir.resolve("doc.txt"), "hello vault");
        UUID uuid = UUID.randomUUID();

        Path trashPath = vault.moveToTrash(src, uuid);
        assertFalse(Files.exists(src));
        assertTrue(Files.exists(trashPath));
        assertEquals("hello vault", Files.readString(trashPath));

        Path restoredDest = tempDir.resolve("restored.txt");
        Path result = vault.restoreFromTrash(uuid, restoredDest);
        assertEquals(restoredDest, result);
        assertFalse(Files.exists(trashPath));
        assertTrue(Files.exists(restoredDest));
        assertEquals("hello vault", Files.readString(restoredDest));
    }

    @Test
    void testMoveToTrashAndRestoreDirectory() throws IOException {
        Path dir = Files.createDirectory(tempDir.resolve("my_folder"));
        Files.writeString(dir.resolve("inner1.txt"), "data1");
        Path sub = Files.createDirectory(dir.resolve("sub"));
        Files.writeString(sub.resolve("inner2.txt"), "data2");

        UUID uuid = UUID.randomUUID();
        Path trashPath = vault.moveToTrash(dir, uuid);

        assertFalse(Files.exists(dir));
        assertTrue(Files.exists(trashPath.resolve("inner1.txt")));
        assertTrue(Files.exists(trashPath.resolve("sub/inner2.txt")));

        Path restoredDir = tempDir.resolve("my_folder_restored");
        vault.restoreFromTrash(uuid, restoredDir);

        assertFalse(Files.exists(trashPath));
        assertTrue(Files.exists(restoredDir.resolve("inner1.txt")));
        assertTrue(Files.exists(restoredDir.resolve("sub/inner2.txt")));
    }

    @Test
    void testPurgeExpired() throws IOException, SQLException {
        Instant now = Instant.now();
        Instant oldTime = now.minus(35, ChronoUnit.DAYS);

        UUID oldUuid = UUID.randomUUID();
        UUID newUuid = UUID.randomUUID();

        // 35日前のレコードとファイル
        OperationRecord oldRecord = new OperationRecord(
            0, "old-batch", OperationType.REMOVE, "/old.txt", null,
            oldUuid.toString(), 100L, false, OperationStatus.ACTIVE, oldTime
        );
        // 今日のレコードとファイル
        OperationRecord newRecord = new OperationRecord(
            0, "new-batch", OperationType.REMOVE, "/new.txt", null,
            newUuid.toString(), 200L, false, OperationStatus.ACTIVE, now
        );

        db.recordBatch(List.of(oldRecord, newRecord));

        Files.createDirectories(trashDir);
        Path oldFile = Files.writeString(trashDir.resolve(oldUuid.toString()), "old data");
        Path newFile = Files.writeString(trashDir.resolve(newUuid.toString()), "new data");

        int purged = vault.purgeExpired(Duration.ofDays(30), db);
        assertEquals(1, purged);

        // 35日前のファイルは削除され、新規ファイルは残る
        assertFalse(Files.exists(oldFile));
        assertTrue(Files.exists(newFile));

        // DBステータスが PURGED になっていることを検証
        List<OperationRecord> oldRecords = db.findByBatchId("old-batch");
        assertEquals(OperationStatus.PURGED, oldRecords.get(0).status());
    }

    @Test
    void testPurgeAll() throws IOException, SQLException {
        Files.createDirectories(trashDir);
        Path f1 = Files.writeString(trashDir.resolve(UUID.randomUUID().toString()), "1");
        Path f2 = Files.writeString(trashDir.resolve(UUID.randomUUID().toString()), "2");

        int purged = vault.purgeAll(db);
        assertEquals(2, purged);
        assertFalse(Files.exists(f1));
        assertFalse(Files.exists(f2));
    }

    @Test
    void testMoveNonExistentThrows() {
        Path missing = tempDir.resolve("missing.txt");
        assertThrows(IOException.class, () -> vault.moveToTrash(missing, UUID.randomUUID()));
    }

    @Test
    void testRestoreNonExistentThrows() {
        assertThrows(IOException.class, () -> vault.restoreFromTrash(UUID.randomUUID(), tempDir.resolve("out.txt")));
    }
}
