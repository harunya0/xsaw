package org.example;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MvTest {
    @TempDir 
    Path tempDir;

    @Test
    void testMoveFile() throws IOException {
        Path src = Files.writeString(tempDir.resolve("foo.txt"), "Hello, World!");
        Path dest = tempDir.resolve("bar.txt");

        mv mover = new mv();
        MoveResult result = mover.move(src, dest);

        assertFalse(Files.exists(src), "Source file should not exist after move");
        assertTrue(Files.exists(dest), "Destination file should exist after move");
        assertEquals("Hello, World!", Files.readString(dest));
        assertEquals(13, result.sizeBytes());
    }

    @Test 
    void testMoveDirectory() throws IOException {
        Path src = Files.writeString(tempDir.resolve("foo.txt"), "Hello, World!");
        Path targetDir = Files.createDirectory(tempDir.resolve("sub"));

        mv mover = new mv();
        MoveResult result = mover.move(src, targetDir);

        assertFalse(Files.exists(src));
        assertTrue(Files.exists(targetDir.resolve("foo.txt")));
        assertEquals(targetDir.resolve("foo.txt"), result.destination());
        assertEquals(13, result.sizeBytes());
    }

    @Test
    void testMoveWithDryRun() throws IOException {
        Path src = Files.writeString(tempDir.resolve("dry.txt"), "content");
        Path dest = tempDir.resolve("dry_dest.txt");

        mv mover = new mv();
        MoveOptions options = new MoveOptions(true, false, false, false);
        MoveResult result = mover.move(src, dest, options);

        assertEquals(MoveStatus.DRY_RUN, result.status());
        assertTrue(Files.exists(src), "dry-run では移動元が残るべき");
        assertFalse(Files.exists(dest), "dry-run では移動先が作られないべき");
    }

    @Test
    void testMoveWithForceOverwrite() throws IOException {
        Path src = Files.writeString(tempDir.resolve("overwrite_src.txt"), "new content");
        Path dest = Files.writeString(tempDir.resolve("overwrite_dest.txt"), "old content");

        mv mover = new mv();
        MoveOptions options = new MoveOptions(false, true, false, false);
        MoveResult result = mover.move(src, dest, options);

        assertEquals(MoveStatus.OVERWRITTEN, result.status());
        assertFalse(Files.exists(src));
        assertTrue(Files.exists(dest));
        assertEquals("new content", Files.readString(dest));
    }

    @Test
    void testMoveWithNoClobber() throws IOException {
        Path src = Files.writeString(tempDir.resolve("skip_src.txt"), "new content");
        Path dest = Files.writeString(tempDir.resolve("skip_dest.txt"), "original");

        mv mover = new mv();
        MoveOptions options = new MoveOptions(false, false, true, false);
        MoveResult result = mover.move(src, dest, options);

        assertEquals(MoveStatus.SKIPPED, result.status());
        assertTrue(Files.exists(src), "スキップ時は移動元が残る");
        assertEquals("original", Files.readString(dest));
    }

    @Test
    void testMoveAllMultipleFiles() throws IOException {
        Path f1 = Files.writeString(tempDir.resolve("f1.txt"), "1");
        Path f2 = Files.writeString(tempDir.resolve("f2.txt"), "2");
        Path dir = Files.createDirectory(tempDir.resolve("target_dir"));

        mv mover = new mv();
        var results = mover.moveAll(java.util.List.of(f1, f2), dir, MoveOptions.DEFAULT);

        assertEquals(2, results.size());
        assertFalse(Files.exists(f1));
        assertFalse(Files.exists(f2));
        assertTrue(Files.exists(dir.resolve("f1.txt")));
        assertTrue(Files.exists(dir.resolve("f2.txt")));
    }
}
