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
}
