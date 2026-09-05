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

    // System.out の出力をキャプチャするヘルパーメソッド
    private String runWithOutputCapture(String... args) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        try {
            System.setOut(new PrintStream(baos, true, StandardCharsets.UTF_8));
            int exitCode = new CommandLine(new App()).execute(args);
            assertEquals(0, exitCode, "コマンド実行は正常終了 (0) するべき");
            return baos.toString(StandardCharsets.UTF_8);
        } finally {
            System.setOut(originalOut);
        }
    }
}