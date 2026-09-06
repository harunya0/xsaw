package du;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FileResultTest {

    @Test
    void testFormattedTotalSize() {
        // バイト単位
        FileResult rBytes = new FileResult(Path.of("."), 1, 0, 500, Map.of(), 50);
        assertEquals("500 B", rBytes.formattedTotalSize());

        // キロバイト単位 (1024 bytes -> 1.0 KB)
        FileResult rKb = new FileResult(Path.of("."), 1, 0, 1024, Map.of(), 100);
        assertEquals("1.0 KB", rKb.formattedTotalSize());

        // 1.5 KB (1536 bytes)
        FileResult rKb15 = new FileResult(Path.of("."), 1, 0, 1536, Map.of(), 150);
        assertEquals("1.5 KB", rKb15.formattedTotalSize());

        // メガバイト単位 (1 MB = 1048576 bytes)
        FileResult rMb = new FileResult(Path.of("."), 1, 0, 1048576 * 5, Map.of(), 500);
        assertEquals("5.0 MB", rMb.formattedTotalSize());

        // ギガバイト単位 (84.2 GB = 84.2 * 1024^3 bytes)
        long gb84 = (long) (84.2 * 1024 * 1024 * 1024);
        FileResult rGb = new FileResult(Path.of("."), 1, 0, gb84, Map.of(), 1000);
        assertEquals("84.2 GB", rGb.formattedTotalSize());
    }

    @Test
    void testFormattedElapsed() {
        // ミリ秒単位 (< 1000 ms)
        FileResult rMs = new FileResult(Path.of("."), 1, 0, 100, Map.of(), 450);
        assertEquals("450 ms", rMs.formattedElapsed());
        assertEquals(450, rMs.elapsedMillis());

        // 秒単位 (1000 ms <= t < 60,000 ms)
        FileResult rSec = new FileResult(Path.of("."), 1, 0, 100, Map.of(), 1520);
        assertEquals("1.52 s", rSec.formattedElapsed());

        FileResult rSec12 = new FileResult(Path.of("."), 1, 0, 100, Map.of(), 12340);
        assertEquals("12.34 s", rSec12.formattedElapsed());

        // 分・秒単位 (>= 60,000 ms)
        FileResult rMin = new FileResult(Path.of("."), 1, 0, 100, Map.of(), 125000); // 2分5秒
        assertEquals("2m 05s", rMin.formattedElapsed());

        FileResult rMin4 = new FileResult(Path.of("."), 1, 0, 100, Map.of(), 258000); // 4分18秒
        assertEquals("4m 18s", rMin4.formattedElapsed());
    }
}
