package rm;

import history.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RmTest {

    @TempDir
    Path tempDir;

    private TrashVault vault;
    private HistoryDb db;
    private rm remover;

    @BeforeEach
    void setUp() {
        Path trashDir = tempDir.resolve("trash");
        vault = new TrashVault(trashDir);
        db = new HistoryDb(tempDir.resolve("history.db"));
        remover = new rm(vault, db);
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (db != null) {
            db.close();
        }
    }

    @Test
    void testRemoveSingleFile() throws IOException, SQLException {
        Path file = Files.writeString(tempDir.resolve("sample.txt"), "hello xsaw rm");

        List<RemoveResult> results = remover.removeAll(List.of(file), false);
        assertEquals(1, results.size());

        RemoveResult res = results.get(0);
        assertFalse(Files.exists(file));
        assertFalse(res.isDirectory());
        assertEquals("hello xsaw rm".length(), res.sizeByte());
        assertNotNull(res.trashUuid());

        // ゴミ箱にファイルが存在することを検証
        Path inTrash = vault.getTrashDir().resolve(res.trashUuid().toString());
        assertTrue(Files.exists(inTrash));
        assertEquals("hello xsaw rm", Files.readString(inTrash));

        // DBに記録されていることを検証
        List<OperationRecord> recent = db.findRecent(1);
        assertEquals(1, recent.size());
        OperationRecord rec = recent.get(0);
        assertEquals(OperationType.REMOVE, rec.operationType());
        assertEquals(res.trashUuid().toString(), rec.trashUuid());
        assertEquals(OperationStatus.ACTIVE, rec.status());
    }

    @Test
    void testRemoveDirectoryRecursive() throws IOException, SQLException {
        Path dir = Files.createDirectory(tempDir.resolve("my_folder"));
        Files.writeString(dir.resolve("inside.txt"), "nested");

        List<RemoveResult> results = remover.removeAll(List.of(dir), true);
        assertEquals(1, results.size());

        RemoveResult res = results.get(0);
        assertTrue(res.isDirectory());
        assertFalse(Files.exists(dir));

        Path inTrash = vault.getTrashDir().resolve(res.trashUuid().toString());
        assertTrue(Files.exists(inTrash));
        assertTrue(Files.exists(inTrash.resolve("inside.txt")));
    }

    @Test
    void testRemoveDirectoryWithoutRecursiveThrows() throws IOException {
        Path dir = Files.createDirectory(tempDir.resolve("my_folder"));
        assertThrows(IOException.class, () -> remover.removeAll(List.of(dir), false));
        assertTrue(Files.exists(dir));
    }

    @Test
    void testRemoveNonExistentThrows() {
        Path missing = tempDir.resolve("does_not_exist.txt");
        assertThrows(NoSuchFileException.class, () -> remover.removeAll(List.of(missing), false));
    }

    @Test
    void testRemoveMultipleFilesSameBatchId() throws IOException, SQLException {
        Path f1 = Files.writeString(tempDir.resolve("f1.txt"), "111");
        Path f2 = Files.writeString(tempDir.resolve("f2.txt"), "2222");

        List<RemoveResult> results = remover.removeAll(List.of(f1, f2), false);
        assertEquals(2, results.size());
        assertFalse(Files.exists(f1));
        assertFalse(Files.exists(f2));

        // 2つのレコードが同じ batchId を共有していることを検証
        List<OperationRecord> records = db.findRecent(2);
        assertEquals(2, records.size());
        assertEquals(records.get(0).batchID(), records.get(1).batchID());
    }
}
