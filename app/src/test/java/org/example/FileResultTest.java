package org.example;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FileResultTest {

    @Test
    void testFormattedTotalSize() {
        // バイト単位
        FileResult rBytes = new FileResult(Path.of("."), 1, 0, 500, Map.of());
        assertEquals("500 B", rBytes.formattedTotalSize());

        // キロバイト単位 (1024 bytes -> 1.0 KB)
        FileResult rKb = new FileResult(Path.of("."), 1, 0, 1024, Map.of());
        assertEquals("1.0 KB", rKb.formattedTotalSize());

        // 1.5 KB (1536 bytes)
        FileResult rKb15 = new FileResult(Path.of("."), 1, 0, 1536, Map.of());
        assertEquals("1.5 KB", rKb15.formattedTotalSize());

        // メガバイト単位 (1 MB = 1048576 bytes)
        FileResult rMb = new FileResult(Path.of("."), 1, 0, 1048576 * 5, Map.of());
        assertEquals("5.0 MB", rMb.formattedTotalSize());

        // ギガバイト単位 (84.2 GB = 84.2 * 1024^3 bytes)
        long gb84 = (long) (84.2 * 1024 * 1024 * 1024);
        FileResult rGb = new FileResult(Path.of("."), 1, 0, gb84, Map.of());
        assertEquals("84.2 GB", rGb.formattedTotalSize());
    }
}
