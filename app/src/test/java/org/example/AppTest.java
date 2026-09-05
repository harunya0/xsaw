package org.example;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import picocli.CommandLine;

class AppTest {

    @TempDir
    Path tempDir;

    @Test
    void testHelpReturnsSuccess() {
        int exitCode = new CommandLine(new App()).execute("--help");
        assertEquals(0, exitCode);
    }

    @Test
    void testLsWithValidDirectory() throws Exception {
        // テスト用ダミーファイルを作成
        Files.createFile(tempDir.resolve("test.txt"));

        // ls を実行
        int exitCode = new CommandLine(new App()).execute("ls", tempDir.toString());
        assertEquals(0, exitCode);
    }

    @Test
    void testLsWithNonExistentDirectory() {
        Path invalidPath = tempDir.resolve("not_found_dir");
        int exitCode = new CommandLine(new App()).execute("ls", invalidPath.toString());
        assertEquals(1, exitCode);
    }

    @Test
    void testLsWithFileInsteadOfDirectory() throws Exception {
        Path filePath = Files.createFile(tempDir.resolve("single_file.txt"));
        int exitCode = new CommandLine(new App()).execute("ls", filePath.toString());
        assertEquals(1, exitCode, "ディレクトリではなくファイルを指定した場合はエラーになるべき");
    }
}