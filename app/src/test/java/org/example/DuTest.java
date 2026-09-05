package org.example;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DuTest {

    @TempDir
    Path tempDir;

    @Test
    void testAnalyzeEmptyDirectory() throws IOException {
        du analyzer = new du();
        FileResult result = analyzer.analyze(tempDir);

        assertEquals(tempDir, result.rootDir());
        assertEquals(0, result.fileCount(), "空ディレクトリのファイル数は 0");
        assertEquals(0, result.dirCount(), "空ディレクトリのサブディレクトリ数は 0");
        assertEquals(0, result.totalBytes(), "空ディレクトリの合計サイズは 0");
        assertTrue(result.extensions().isEmpty(), "空ディレクトリの拡張子マップは空");
    }

    @Test
    void testAnalyzeNestedDirectories() throws IOException {
        // 階層構造とファイルを作成:
        // tempDir/
        //   ├── file1.txt (10 bytes)
        //   ├── file2.zip (20 bytes)
        //   └── sub1/
        //        ├── file3.txt (30 bytes)
        //        ├── image.png (40 bytes)
        //        └── deep/
        //             ├── file4.zip (50 bytes)
        //             └── noextension (60 bytes)

        Files.write(tempDir.resolve("file1.txt"), "0123456789".getBytes(StandardCharsets.UTF_8));
        Files.write(tempDir.resolve("file2.zip"), "01234567890123456789".getBytes(StandardCharsets.UTF_8));

        Path sub1 = Files.createDirectory(tempDir.resolve("sub1"));
        Files.write(sub1.resolve("file3.txt"), "012345678901234567890123456789".getBytes(StandardCharsets.UTF_8));
        Files.write(sub1.resolve("image.png"), "0123456789012345678901234567890123456789".getBytes(StandardCharsets.UTF_8));

        Path deep = Files.createDirectory(sub1.resolve("deep"));
        Files.write(deep.resolve("file4.zip"), "a".repeat(50).getBytes(StandardCharsets.UTF_8));
        Files.write(deep.resolve("noextension"), "b".repeat(60).getBytes(StandardCharsets.UTF_8));

        // 解析を実行
        du analyzer = new du();
        FileResult result = analyzer.analyze(tempDir);

        // 検証
        assertEquals(6, result.fileCount(), "合計ファイル数は 6");
        assertEquals(2, result.dirCount(), "サブディレクトリ数は 2 (sub1, deep)");
        assertEquals(210, result.totalBytes(), "合計サイズは 210 bytes");

        // 拡張子の検証 (.txt が 2件, .zip が 2件, .png が 1件, other が 1件)
        var exts = result.extensions();
        assertTrue(exts.containsKey("txt"));
        assertEquals(2, exts.get("txt").count());
        assertEquals(40, exts.get("txt").totalBytes()); // 10 + 30

        assertTrue(exts.containsKey("zip"));
        assertEquals(2, exts.get("zip").count());
        assertEquals(70, exts.get("zip").totalBytes()); // 20 + 50

        assertTrue(exts.containsKey("png"));
        assertEquals(1, exts.get("png").count());
        assertEquals(40, exts.get("png").totalBytes());

        assertTrue(exts.containsKey("(none)"));
        assertEquals(1, exts.get("(none)").count());
        assertEquals(60, exts.get("(none)").totalBytes());
    }

    @Test
    void testFileExtensionCaseInsensitivity() throws IOException {
        // 大文字の拡張子も小文字として同一視されるか (.TXT と .txt)
        Files.writeString(tempDir.resolve("upper.TXT"), "test");
        Files.writeString(tempDir.resolve("lower.txt"), "test");

        du analyzer = new du();
        FileResult result = analyzer.analyze(tempDir);

        assertEquals(2, result.fileCount());
        assertTrue(result.extensions().containsKey("txt"));
        assertEquals(2, result.extensions().get("txt").count(), "TXT と txt は同じ拡張子として集計される");
    }
}
