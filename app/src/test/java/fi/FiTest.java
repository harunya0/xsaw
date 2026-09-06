package fi;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FiTest {

    @TempDir
    Path tempDir;

    @Test
    void testFindEmptyDirectory() throws IOException {
        fi finder = new fi();
        fi.FindOptions options = new fi.FindOptions(false, false, false);
        FindResult result = finder.find(tempDir, "test", options);

        assertEquals(tempDir, result.rootDir());
        assertEquals(0, result.count());
        assertTrue(result.matches().isEmpty());
    }

    @Test
    void testFindAllWithEmptyQuery() throws IOException {
        Files.createFile(tempDir.resolve("file1.txt"));
        Files.createFile(tempDir.resolve("file2.jpg"));
        Files.createDirectory(tempDir.resolve("subDir"));

        fi finder = new fi();
        fi.FindOptions options = new fi.FindOptions(false, false, false);
        FindResult result = finder.find(tempDir, "", options);

        assertEquals(3, result.count(), "空クエリの場合は全ファイル・フォルダがマッチするべき");
    }

    @Test
    void testFindByNameSubstring() throws IOException {
        Files.createFile(tempDir.resolve("target_report.txt"));
        Files.createFile(tempDir.resolve("other_file.txt"));
        Files.createDirectory(tempDir.resolve("target_folder"));

        fi finder = new fi();
        fi.FindOptions options = new fi.FindOptions(false, false, false);
        FindResult result = finder.find(tempDir, "target", options);

        assertEquals(2, result.count());
        List<String> fileNames = result.matches().stream()
                .map(p -> p.getFileName().toString())
                .toList();
        assertTrue(fileNames.contains("target_report.txt"));
        assertTrue(fileNames.contains("target_folder"));
        assertFalse(fileNames.contains("other_file.txt"));
    }

    @Test
    void testFindCaseInsensitiveByDefault() throws IOException {
        Files.createFile(tempDir.resolve("MixedCaseFile.txt"));
        Files.createFile(tempDir.resolve("other_file.txt"));

        fi finder = new fi();
        fi.FindOptions options = new fi.FindOptions(false, false, false);
        FindResult result = finder.find(tempDir, "MIXEDCASE", options);

        assertEquals(1, result.count(), "デフォルトは大文字小文字を区別せず検索するべき");
        assertEquals("MixedCaseFile.txt", result.matches().get(0).getFileName().toString());
    }

    @Test
    void testFindCaseSensitive() throws IOException {
        Files.createFile(tempDir.resolve("CaseTarget.txt"));

        fi finder = new fi();
        // caseSensitive = true で小文字クエリを投げる（不一致になるはず）
        fi.FindOptions optMismatch = new fi.FindOptions(true, false, false);
        FindResult resMismatch = finder.find(tempDir, "casetarget", optMismatch);
        assertEquals(0, resMismatch.count(), "caseSensitive 有効時は大文字小文字が異なれば不一致");

        // caseSensitive = true で大文字小文字完全一致クエリを投げる（一致するはず）
        fi.FindOptions optMatch = new fi.FindOptions(true, false, false);
        FindResult resMatch = finder.find(tempDir, "CaseTarget", optMatch);
        assertEquals(1, resMatch.count(), "caseSensitive 有効時に完全一致ならヒットする");
        assertEquals("CaseTarget.txt", resMatch.matches().get(0).getFileName().toString());
    }

    @Test
    void testFindDirOnly() throws IOException {
        Files.createFile(tempDir.resolve("my_name_file.txt"));
        Files.createDirectory(tempDir.resolve("my_name_dir"));

        fi finder = new fi();
        // dirOnly = true
        fi.FindOptions options = new fi.FindOptions(false, true, false);
        FindResult result = finder.find(tempDir, "my_name", options);

        assertEquals(1, result.count());
        assertTrue(Files.isDirectory(result.matches().get(0)));
        assertEquals("my_name_dir", result.matches().get(0).getFileName().toString());
    }

    @Test
    void testFindFileOnly() throws IOException {
        Files.createFile(tempDir.resolve("item_file.txt"));
        Files.createDirectory(tempDir.resolve("item_dir"));

        fi finder = new fi();
        // fileOnly = true
        fi.FindOptions options = new fi.FindOptions(false, false, true);
        FindResult result = finder.find(tempDir, "item", options);

        assertEquals(1, result.count());
        assertTrue(Files.isRegularFile(result.matches().get(0)));
        assertEquals("item_file.txt", result.matches().get(0).getFileName().toString());
    }

    @Test
    void testFindByExtensionWithAndWithoutDot() throws IOException {
        Files.createFile(tempDir.resolve("doc.txt"));
        Files.createFile(tempDir.resolve("image.png"));
        Files.createFile(tempDir.resolve("notes.TXT"));

        fi finder = new fi();

        // 拡張子 "txt" (ドットなし)
        fi.FindOptions opt1 = new fi.FindOptions(false, false, false, java.util.Set.of("txt"));
        FindResult res1 = finder.find(tempDir, "", opt1);
        assertEquals(2, res1.count(), "doc.txt と notes.TXT の2件がマッチするべき");

        // 拡張子 ".png" (正規化されて png になる)
        fi.FindOptions opt2 = new fi.FindOptions(false, false, false, java.util.Set.of("png"));
        FindResult res2 = finder.find(tempDir, "", opt2);
        assertEquals(1, res2.count());
        assertEquals("image.png", res2.matches().get(0).getFileName().toString());
    }

    @Test
    void testFindByMultipleExtensions() throws IOException {
        Files.createFile(tempDir.resolve("app.java"));
        Files.createFile(tempDir.resolve("notes.txt"));
        Files.createFile(tempDir.resolve("photo.jpg"));

        fi finder = new fi();
        // 複数拡張子: java と txt を同時に指定
        fi.FindOptions options = new fi.FindOptions(false, false, false, java.util.Set.of("java", "txt"));
        FindResult result = finder.find(tempDir, "", options);

        assertEquals(2, result.count(), "java と txt の2件がヒットするべき");
    }

    @Test
    void testFindNestedDirectoryTree() throws IOException {
        Path dirA = Files.createDirectory(tempDir.resolve("a"));
        Path dirB = Files.createDirectory(tempDir.resolve("b"));
        Path subB = Files.createDirectory(dirB.resolve("sub"));

        Files.createFile(dirA.resolve("find_me.java"));
        Files.createFile(subB.resolve("find_me.txt"));

        fi finder = new fi();
        fi.FindOptions options = new fi.FindOptions(false, false, false, java.util.Set.of("java"));
        FindResult result = finder.find(tempDir, "find_me", options);

        assertEquals(1, result.count());
        assertEquals("find_me.java", result.matches().get(0).getFileName().toString());
    }

    @Test
    void testFindByRegex() throws IOException {
        Files.createFile(tempDir.resolve("log_01.txt"));
        Files.createFile(tempDir.resolve("log_02.txt"));
        Files.createFile(tempDir.resolve("log_abc.txt"));
        Files.createFile(tempDir.resolve("other.txt"));

        fi finder = new fi();
        // 数字2桁にマッチ: log_[0-9]{2}
        fi.FindOptions options = new fi.FindOptions(false, false, false, true, java.util.Set.of("txt"));
        FindResult result = finder.find(tempDir, "log_[0-9]{2}", options);

        assertEquals(2, result.count(), "log_01.txt と log_02.txt の2件がマッチするべき");
    }

    @Test
    void testFindByRegexCaseSensitive() throws IOException {
        Files.createFile(tempDir.resolve("AppController.java"));
        Files.createFile(tempDir.resolve("app_helper.java"));

        fi finder = new fi();
        // 大文字始まり正規表現 + caseSensitive = true
        fi.FindOptions optionsCaseSensitive = new fi.FindOptions(true, false, false, true, java.util.Set.of());
        FindResult result = finder.find(tempDir, "^[A-Z].*", optionsCaseSensitive);

        assertEquals(1, result.count());
        assertEquals("AppController.java", result.matches().get(0).getFileName().toString());

        // caseSensitive = false だと両方マッチ
        fi.FindOptions optionsIgnoreCase = new fi.FindOptions(false, false, false, true, java.util.Set.of());
        FindResult resultAll = finder.find(tempDir, "^[A-Z].*", optionsIgnoreCase);
        assertEquals(2, resultAll.count());
    }

    @Test
    void testFindInvalidRegexThrows() {
        fi finder = new fi();
        fi.FindOptions options = new fi.FindOptions(false, false, false, true, java.util.Set.of());
        assertThrows(java.util.regex.PatternSyntaxException.class, () -> {
            finder.find(tempDir, "[invalid_regex", options);
        });
    }
}
