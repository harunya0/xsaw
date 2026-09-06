package fi;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class FindResultTest {

    @Test
    void testCountAndProperties() {
        Path root = Path.of("/test/root");
        Path match1 = root.resolve("a.txt");
        Path match2 = root.resolve("b.txt");

        FindResult result = new FindResult(root, List.of(match1, match2), 42);

        assertEquals(root, result.rootDir());
        assertEquals(2, result.count());
        assertEquals(42, result.elapsedMillis());
        assertEquals(List.of(match1, match2), result.matches());
    }

    @Test
    void testRelativeMatches() {
        Path root = Path.of("root").toAbsolutePath();
        Path file1 = root.resolve("sub/file1.txt");
        Path file2 = root.resolve("file2.txt");

        FindResult result = new FindResult(root, List.of(file1, file2), 10);
        List<Path> rel = result.relativeMatches();

        assertEquals(2, rel.size());
        assertEquals(Path.of("sub/file1.txt"), rel.get(0));
        assertEquals(Path.of("file2.txt"), rel.get(1));
    }

    @Test
    void testImmutabilityOfMatches() {
        Path root = Path.of("root");
        FindResult result = new FindResult(root, List.of(root.resolve("a.txt")), 5);

        // 返されるリストが不変であることの検証
        assertThrows(UnsupportedOperationException.class, () -> {
            result.matches().add(root.resolve("b.txt"));
        });
    }

    @Test
    void testEqualsAndHashCode() {
        Path root = Path.of("root");
        FindResult r1 = new FindResult(root, List.of(root.resolve("a.txt")), 10);
        FindResult r2 = new FindResult(root, List.of(root.resolve("a.txt")), 10);

        assertEquals(r1, r2);
        assertEquals(r1.hashCode(), r2.hashCode());
        assertTrue(r1.toString().contains("FindResult"));
    }
}
