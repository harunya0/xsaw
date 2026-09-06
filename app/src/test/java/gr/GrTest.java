package gr;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class GrTest {

    @TempDir
    Path tempDir;

    @Test
    void testGrepBasicDirectory() throws IOException {
        Files.writeString(tempDir.resolve("file1.txt"), "Hello World\nSecond line\nHello again");
        Files.writeString(tempDir.resolve("file2.txt"), "No match here\nJust testing");
        Files.writeString(tempDir.resolve("file3.txt"), "hello lowercase");

        gr grep = new gr();
        GrepOptions options = new GrepOptions(false, false, null, false, false);
        GrepResult result = grep.grep(tempDir, "hello", options);

        // Case-insensitive: should match "Hello World", "Hello again", and "hello lowercase"
        assertEquals(3, result.matchCount());
        assertEquals(3, result.fileSearched());
        assertEquals(2, result.matchingFiles().size());
    }

    @Test
    void testGrepCaseSensitive() throws IOException {
        Files.writeString(tempDir.resolve("file1.txt"), "Hello World\nhello world\nHELLO WORLD");

        gr grep = new gr();
        GrepOptions optionsCaseSensitive = new GrepOptions(true, false, null, false, false);
        GrepResult resultCaseSensitive = grep.grep(tempDir, "Hello", optionsCaseSensitive);

        assertEquals(1, resultCaseSensitive.matchCount());
        assertEquals("Hello World", resultCaseSensitive.matches().get(0).lineContent());

        GrepOptions optionsCaseInsensitive = new GrepOptions(false, false, null, false, false);
        GrepResult resultCaseInsensitive = grep.grep(tempDir, "Hello", optionsCaseInsensitive);
        assertEquals(3, resultCaseInsensitive.matchCount());
    }

    @Test
    void testGrepRegex() throws IOException {
        Files.writeString(tempDir.resolve("file.txt"), "user_123: active\nuser_abc: inactive\nuser_456: active");

        gr grep = new gr();
        GrepOptions options = new GrepOptions(false, true, null, false, false);
        GrepResult result = grep.grep(tempDir, "user_\\d+", options);

        assertEquals(2, result.matchCount());
        assertTrue(result.matches().stream().anyMatch(m -> m.lineContent().contains("user_123")));
        assertTrue(result.matches().stream().anyMatch(m -> m.lineContent().contains("user_456")));
    }

    @Test
    void testGrepExtFilter() throws IOException {
        Files.writeString(tempDir.resolve("file.txt"), "match here");
        Files.writeString(tempDir.resolve("file.java"), "match here");
        Files.writeString(tempDir.resolve("file.log"), "match here");

        gr grep = new gr();
        GrepOptions options = new GrepOptions(false, false, Set.of("java", "log"), false, false);
        GrepResult result = grep.grep(tempDir, "match", options);

        assertEquals(2, result.matchCount());
        assertEquals(2, result.fileSearched());
        for (GrepMatch match : result.matches()) {
            String fileName = match.file().getFileName().toString();
            assertTrue(fileName.endsWith(".java") || fileName.endsWith(".log"));
        }
    }

    @Test
    void testGrepSingleFile() throws IOException {
        Path singleFile = Files.writeString(tempDir.resolve("single.txt"), "line one\nline two match\nline three");

        gr grep = new gr();
        GrepOptions options = new GrepOptions(false, false, null, false, false);
        GrepResult result = grep.grep(singleFile, "match", options);

        assertEquals(1, result.matchCount());
        assertEquals(1, result.fileSearched());
        assertEquals(2, result.matches().get(0).lineNumber());
        assertEquals("line two match", result.matches().get(0).lineContent());
    }

    @Test
    void testGrepFilesWithMatchesEarlyExit() throws IOException {
        // file with multiple matching lines
        Files.writeString(tempDir.resolve("multi_match.txt"), "test 1\ntest 2\ntest 3\ntest 4");

        gr grep = new gr();
        GrepOptions options = new GrepOptions(false, false, null, true, false);
        GrepResult result = grep.grep(tempDir, "test", options);

        // filesWithMatches should only record 1 match per file due to early break
        assertEquals(1, result.matchCount());
        assertEquals(1, result.matchingFiles().size());
    }

    @Test
    void testGrepFilesList() throws IOException {
        Path f1 = Files.writeString(tempDir.resolve("f1.txt"), "alpha");
        Files.writeString(tempDir.resolve("f2.txt"), "beta");
        Path f3 = Files.writeString(tempDir.resolve("f3.txt"), "alpha beta");

        gr grep = new gr();
        GrepOptions options = new GrepOptions(false, false, null, false, false);
        GrepResult result = grep.grepFiles(List.of(f1, f3), "alpha", options);

        assertEquals(2, result.matchCount());
        assertEquals(2, result.fileSearched());
        assertEquals(2, result.matchingFiles().size());
    }

    @Test
    void testGrepStream() throws IOException {
        String content = "first line\nsecond keyword line\nthird keyword line\nfourth line";
        BufferedReader reader = new BufferedReader(new StringReader(content));

        gr grep = new gr();
        GrepOptions options = new GrepOptions(false, false, null, false, true);
        GrepResult result = grep.grepStream(reader, "keyword", options);

        assertEquals(2, result.matchCount());
        assertNull(result.matches().get(0).file());
        assertEquals(2, result.matches().get(0).lineNumber());
        assertEquals(3, result.matches().get(1).lineNumber());
    }

    @Test
    void testGrepNoMatches() throws IOException {
        Files.writeString(tempDir.resolve("empty.txt"), "nothing to see here");

        gr grep = new gr();
        GrepOptions options = new GrepOptions(false, false, null, false, false);
        GrepResult result = grep.grep(tempDir, "nonexistent_term_12345", options);

        assertEquals(0, result.matchCount());
        assertTrue(result.matches().isEmpty());
        assertTrue(result.matchingFiles().isEmpty());
    }

    @Test
    void testGrepExcludesHiddenByDefault() throws IOException {
        Files.writeString(tempDir.resolve(".secret.txt"), "secret keyword");
        Files.writeString(tempDir.resolve("visible.txt"), "visible keyword");

        gr grep = new gr();
        GrepOptions options = new GrepOptions(false, false, null, false, false);
        GrepResult result = grep.grep(tempDir, "keyword", options);

        assertEquals(1, result.matchCount(), "隠しファイルはデフォルトでは grep されない");
        assertEquals("visible.txt", result.matches().get(0).file().getFileName().toString());
    }

    @Test
    void testGrepIncludesHiddenWhenRequested() throws IOException {
        Files.writeString(tempDir.resolve(".secret.txt"), "secret keyword");
        Files.writeString(tempDir.resolve("visible.txt"), "visible keyword");

        gr grep = new gr();
        GrepOptions options = new GrepOptions(false, false, null, false, false, java.util.Set.of(), true);
        GrepResult result = grep.grep(tempDir, "keyword", options);

        assertEquals(2, result.matchCount(), "hidden = true の場合は隠しファイルも grep される");
    }

    @Test
    void testGrepExcludeDirectories() throws IOException {
        Path buildDir = Files.createDirectory(tempDir.resolve("build"));
        Files.writeString(buildDir.resolve("out.txt"), "build keyword");
        Files.writeString(tempDir.resolve("src.txt"), "src keyword");

        gr grep = new gr();
        GrepOptions options = new GrepOptions(false, false, null, false, false, java.util.Set.of("build"), false);
        GrepResult result = grep.grep(tempDir, "keyword", options);

        assertEquals(1, result.matchCount(), "build ディレクトリ配下は除外される");
        assertEquals("src.txt", result.matches().get(0).file().getFileName().toString());
    }
}
