package org.example;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AppTest {

    @TempDir
    Path tempDir;

    @Test
    void testHelpReturnsSuccess() {
        int exitCode = new CommandLine(new App()).execute("--help");
        assertEquals(0, exitCode);
    }

    @Test
    void testDuWithValidDirectory() throws Exception {
        Files.createFile(tempDir.resolve("test.txt"));

        int exitCode = new CommandLine(new App()).execute("du", tempDir.toString());
        assertEquals(0, exitCode);
    }

    @Test
    void testAliasDWithValidDirectory() throws Exception {
        Files.createFile(tempDir.resolve("test.txt"));

        // 1文字エイリアス "d" でも動くことをテスト
        int exitCode = new CommandLine(new App()).execute("d", tempDir.toString());
        assertEquals(0, exitCode, "1文字エイリアス 'd' でも正常終了するべき");
    }

    @Test
    void testDuWithNonExistentDirectory() {
        Path invalidPath = tempDir.resolve("not_found_dir");
        int exitCode = new CommandLine(new App()).execute("du", invalidPath.toString());
        assertEquals(1, exitCode);
    }

    @Test
    void testDuWithFileInsteadOfDirectory() throws Exception {
        Path filePath = Files.createFile(tempDir.resolve("single_file.txt"));
        int exitCode = new CommandLine(new App()).execute("du", filePath.toString());
        assertEquals(1, exitCode, "ディレクトリではなくファイルを指定した場合はエラーになるべき");
    }

    @Test
    void testDuWithTopNOption() throws Exception {
        Files.createFile(tempDir.resolve("a.zip"));
        Files.createFile(tempDir.resolve("b.mp4"));
        Files.createFile(tempDir.resolve("c.jpg"));
        Files.createFile(tempDir.resolve("d.pdf"));
        Files.createFile(tempDir.resolve("e.txt"));

        String output = runWithOutputCapture("du", "-n", "2", tempDir.toString());

        assertTrue(output.contains("Extension statistics:"));
        assertTrue(output.contains("Other"), "-n 2 の場合、残り 3 件が Other に合算されるべき");
    }

    @Test
    void testDuWithTopNZeroShowsAllWithoutOther() throws Exception {
        Files.createFile(tempDir.resolve("a.zip"));
        Files.createFile(tempDir.resolve("b.mp4"));

        String output = runWithOutputCapture("du", "-n", "0", tempDir.toString());

        assertTrue(output.contains("zip"));
        assertTrue(output.contains("mp4"));
        assertFalse(output.contains("Other"), "-n 0 の場合、すべて表示されるため Other は出ないべき");
    }

    @Test
    void testDuWithListOnlyOption() throws Exception {
        Files.createFile(tempDir.resolve("a.zip"));
        Files.createFile(tempDir.resolve("b.mp4"));

        String output = runWithOutputCapture("du", "-l", tempDir.toString());

        assertTrue(output.contains("extensions:"), "-l の場合は extensions: ヘッダが出るべき");
        assertTrue(output.contains("zip"));
        assertTrue(output.contains("mp4"));
        assertFalse(output.contains("%"), "-l の場合はパーセント表示が含まれないべき");
    }

    @Test
    void testDuWithCombinedOptions() throws Exception {
        Files.createFile(tempDir.resolve("a.zip"));
        Files.createFile(tempDir.resolve("b.mp4"));
        Files.createFile(tempDir.resolve("c.txt"));

        String output = runWithOutputCapture("du", "-l", "-n", "0", tempDir.toString());

        assertTrue(output.contains("extensions:"));
        assertTrue(output.contains("zip"));
        assertTrue(output.contains("mp4"));
        assertTrue(output.contains("txt"));
        assertFalse(output.contains("%"));
    }

    @Test
    void testDuWithListOnlyGridFormat() throws Exception {
        // 件数に差をつけて 6 種類の拡張子を作成 (4列なので 1行目に件数上位4つ、2行目に残り2つが並ぶ)
        // .aaa: 6個, .bbb: 5個, .ccc: 4個, .ddd: 3個, .eee: 2個, .fff: 1個
        for (int i = 0; i < 6; i++) Files.createFile(tempDir.resolve("f" + i + ".aaa"));
        for (int i = 0; i < 5; i++) Files.createFile(tempDir.resolve("f" + i + ".bbb"));
        for (int i = 0; i < 4; i++) Files.createFile(tempDir.resolve("f" + i + ".ccc"));
        for (int i = 0; i < 3; i++) Files.createFile(tempDir.resolve("f" + i + ".ddd"));
        for (int i = 0; i < 2; i++) Files.createFile(tempDir.resolve("f" + i + ".eee"));
        for (int i = 0; i < 1; i++) Files.createFile(tempDir.resolve("f" + i + ".fff"));

        String output = runWithOutputCapture("du", "-l", "-n", "0", tempDir.toString());

        // 出力を行ごとに分割
        var lines = output.lines().map(s -> s.trim()).filter(s -> !s.isEmpty()).toList();
        int extHeaderIndex = lines.indexOf("extensions:");
        assertTrue(extHeaderIndex >= 0, "extensions: ヘッダが存在するべき");

        // 1行目に上位4件 (.aaa, .bbb, .ccc, .ddd) が横並びで含まれていること
        String firstRow = lines.get(extHeaderIndex + 1);
        assertTrue(firstRow.contains(".aaa"));
        assertTrue(firstRow.contains(".bbb"));
        assertTrue(firstRow.contains(".ccc"));
        assertTrue(firstRow.contains(".ddd"));

        // 2行目に残り2件 (.eee, .fff) が横並びで含まれていること
        String secondRow = lines.get(extHeaderIndex + 2);
        assertTrue(secondRow.contains(".eee"));
        assertTrue(secondRow.contains(".fff"));
    }

    @Test
    void testFiWithValidDirectory() throws Exception {
        Files.createFile(tempDir.resolve("target.txt"));
        Files.createFile(tempDir.resolve("other.txt"));

        String output = runWithOutputCapture("fi", "target", tempDir.toString());
        assertTrue(output.contains("target.txt"));
        assertFalse(output.contains("other.txt"));
        assertTrue(output.contains("Found 1 matches"));
    }

    @Test
    void testFAliasWithValidDirectory() throws Exception {
        Files.createFile(tempDir.resolve("test_file.txt"));

        String output = runWithOutputCapture("f", "test", tempDir.toString());
        assertTrue(output.contains("test_file.txt"));
        assertTrue(output.contains("Found 1 matches"));
    }

    @Test
    void testFiCaseSensitive() throws Exception {
        Files.createFile(tempDir.resolve("UpperCase.txt"));

        // 小文字で検索した場合はマッチしない (0件)
        String outputMismatch = runWithOutputCapture("fi", "-s", "upper", tempDir.toString());
        assertFalse(outputMismatch.contains("UpperCase.txt"));
        assertTrue(outputMismatch.contains("Found 0 matches"));

        // 正確な大文字小文字で検索した場合はマッチする (1件)
        String outputMatch = runWithOutputCapture("fi", "-s", "Upper", tempDir.toString());
        assertTrue(outputMatch.contains("UpperCase.txt"));
        assertTrue(outputMatch.contains("Found 1 matches"));
    }

    @Test
    void testFiDirOnly() throws Exception {
        Files.createFile(tempDir.resolve("match_file.txt"));
        Files.createDirectory(tempDir.resolve("match_dir"));

        String output = runWithOutputCapture("fi", "-d", "match", tempDir.toString());
        assertTrue(output.contains("match_dir"));
        assertFalse(output.contains("match_file.txt"));
        assertTrue(output.contains("Found 1 matches"));
    }

    @Test
    void testFiFileOnly() throws Exception {
        Files.createFile(tempDir.resolve("match_file.txt"));
        Files.createDirectory(tempDir.resolve("match_dir"));

        String output = runWithOutputCapture("fi", "-f", "match", tempDir.toString());
        assertTrue(output.contains("match_file.txt"));
        assertFalse(output.contains("match_dir"));
        assertTrue(output.contains("Found 1 matches"));
    }

    @Test
    void testFiExtOption() throws Exception {
        Files.createFile(tempDir.resolve("test.java"));
        Files.createFile(tempDir.resolve("test.py"));

        String output = runWithOutputCapture("fi", "-e", "java", "test", tempDir.toString());
        assertTrue(output.contains("test.java"));
        assertFalse(output.contains("test.py"));
        assertTrue(output.contains("Found 1 matches"));
    }

    @Test
    void testFiConflictingDirAndFileOptions() {
        int exitCode = new CommandLine(new App()).execute("fi", "-d", "-f", "query", tempDir.toString());
        assertEquals(1, exitCode, "--dir-only と --file-only の同時指定はエラーになるべき");
    }

    @Test
    void testFiWithNonExistentDirectory() {
        Path invalidPath = tempDir.resolve("non_existent_folder");
        int exitCode = new CommandLine(new App()).execute("fi", "query", invalidPath.toString());
        assertEquals(1, exitCode);
    }

    @Test
    void testFiWithFileInsteadOfDirectory() throws Exception {
        Path singleFile = Files.createFile(tempDir.resolve("file.txt"));
        int exitCode = new CommandLine(new App()).execute("fi", "query", singleFile.toString());
        assertEquals(1, exitCode, "ディレクトリではなくファイルを指定した場合はエラーになるべき");
    }

    @Test
    void testDuFromStandardInputWithHyphen() throws Exception {
        Path f1 = Files.writeString(tempDir.resolve("item1.txt"), "hello");
        Path f2 = Files.writeString(tempDir.resolve("item2.java"), "class Test {}");

        // 絶対パスで標準入力をエミュレート
        String inputLines = f1.toAbsolutePath().toString() + System.lineSeparator() +
                            f2.toAbsolutePath().toString() + System.lineSeparator();
        java.io.InputStream origIn = System.in;
        try {
            System.setIn(new java.io.ByteArrayInputStream(inputLines.getBytes(StandardCharsets.UTF_8)));
            String output = runWithOutputCapture("du", "-");
            assertTrue(output.contains("Directory: (standard input)"));
            assertTrue(output.contains("Files:                  2"));
            assertTrue(output.contains("txt"));
            assertTrue(output.contains("java"));
        } finally {
            System.setIn(origIn);
        }
    }

    @Test
    void testFiStdoutOnlyContainsPathsForPiping() throws Exception {
        Files.createFile(tempDir.resolve("pipe_test.txt"));

        // stdout のみをキャプチャ
        ByteArrayOutputStream outCapture = new ByteArrayOutputStream();
        PrintStream origOut = System.out;
        try {
            System.setOut(new PrintStream(outCapture, true, StandardCharsets.UTF_8));
            int exitCode = new CommandLine(new App()).execute("fi", "pipe_test", tempDir.toString());
            assertEquals(0, exitCode);
        } finally {
            System.setOut(origOut);
        }

        String stdout = outCapture.toString(StandardCharsets.UTF_8);
        assertTrue(stdout.contains("pipe_test.txt"), "stdout にはマッチしたパスが含まれるべき");
        assertFalse(stdout.contains("Found"), "stdout にはサマリ (Found... ms) が含まれないべき (stderr に分離)");
    }

    @Test
    void testDuWithMultipleExtensionsCommaSeparated() throws Exception {
        Files.createFile(tempDir.resolve("code.java"));
        Files.createFile(tempDir.resolve("doc.txt"));
        Files.createFile(tempDir.resolve("image.png"));

        String output = runWithOutputCapture("du", "-e", "java,txt", tempDir.toString());
        assertTrue(output.contains("Files:                  2"));
        assertTrue(output.contains("java"));
        assertTrue(output.contains("txt"));
        assertFalse(output.contains("png"));
    }

    @Test
    void testDuWithMultipleExtensionsRepeatedFlags() throws Exception {
        Files.createFile(tempDir.resolve("code.java"));
        Files.createFile(tempDir.resolve("doc.txt"));
        Files.createFile(tempDir.resolve("image.png"));

        String output = runWithOutputCapture("du", "-e", "java", "-e", "txt", tempDir.toString());
        assertTrue(output.contains("Files:                  2"));
        assertTrue(output.contains("java"));
        assertTrue(output.contains("txt"));
        assertFalse(output.contains("png"));
    }

    @Test
    void testFiWithMultipleExtensions() throws Exception {
        Files.createFile(tempDir.resolve("app.java"));
        Files.createFile(tempDir.resolve("app.kt"));
        Files.createFile(tempDir.resolve("app.py"));

        String output = runWithOutputCapture("fi", "-e", "java,kt", "app", tempDir.toString());
        assertTrue(output.contains("app.java"));
        assertTrue(output.contains("app.kt"));
        assertFalse(output.contains("app.py"));
        assertTrue(output.contains("Found 2 matches"));
    }

    @Test
    void testMvCommand() throws Exception {
        Path src = Files.writeString(tempDir.resolve("cli_mv.txt"), "mv content");
        Path dest = tempDir.resolve("cli_moved.txt");

        String output = runWithOutputCapture("mv", src.toString(), dest.toString());
        assertTrue(output.contains("Moved: cli_mv.txt"));
        assertFalse(Files.exists(src));
        assertTrue(Files.exists(dest));
        assertEquals("mv content", Files.readString(dest));
    }

    @Test
    void testMvCommandShortAlias() throws Exception {
        Path src = Files.writeString(tempDir.resolve("alias_src.txt"), "alias content");
        Path subDir = Files.createDirectory(tempDir.resolve("sub_alias"));

        String output = runWithOutputCapture("m", src.toString(), subDir.toString());
        assertTrue(output.contains("Moved: alias_src.txt"));
        assertFalse(Files.exists(src));
        assertTrue(Files.exists(subDir.resolve("alias_src.txt")));
    }

    // System.out と System.err の出力をキャプチャするヘルパーメソッド
    private String runWithOutputCapture(String... args) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        try {
            PrintStream ps = new PrintStream(baos, true, StandardCharsets.UTF_8);
            System.setOut(ps);
            System.setErr(ps);
            int exitCode = new CommandLine(new App()).execute(args);
            assertEquals(0, exitCode, "コマンド実行は正常終了 (0) するべき");
            return baos.toString(StandardCharsets.UTF_8);
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
    }
}