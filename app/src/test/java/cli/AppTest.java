package cli;

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
    void testFiExtOptionWithoutQuery() throws Exception {
        Files.createFile(tempDir.resolve("alpha.java"));
        Files.createFile(tempDir.resolve("beta.java"));
        Files.createFile(tempDir.resolve("gamma.py"));

        String output = runWithOutputCapture("fi", "-e", "java", "", tempDir.toString());
        assertTrue(output.contains("alpha.java"));
        assertTrue(output.contains("beta.java"));
        assertFalse(output.contains("gamma.py"));
        assertTrue(output.contains("Found 2 matches"));
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
        assertTrue(output.contains("[MOVED] cli_mv.txt"));
        assertFalse(Files.exists(src));
        assertTrue(Files.exists(dest));
        assertEquals("mv content", Files.readString(dest));
    }

    @Test
    void testMvCommandShortAlias() throws Exception {
        Path src = Files.writeString(tempDir.resolve("alias_src.txt"), "alias content");
        Path subDir = Files.createDirectory(tempDir.resolve("sub_alias"));

        String output = runWithOutputCapture("m", src.toString(), subDir.toString());
        assertTrue(output.contains("[MOVED] alias_src.txt"));
        assertFalse(Files.exists(src));
        assertTrue(Files.exists(subDir.resolve("alias_src.txt")));
    }

    @Test
    void testFiRegexOption() throws Exception {
        Files.createFile(tempDir.resolve("order_123.json"));
        Files.createFile(tempDir.resolve("order_abc.json"));

        String output = runWithOutputCapture("fi", "-r", "order_\\d+", tempDir.toString());
        assertTrue(output.contains("order_123.json"));
        assertFalse(output.contains("order_abc.json"));
    }

    @Test
    void testFiInvalidRegexReturnsErrorCode() {
        int exitCode = new CommandLine(new App()).execute("fi", "-r", "[unclosed", tempDir.toString());
        assertEquals(1, exitCode, "不正な正規表現は終了コード 1 で終了するべき");
    }

    @Test
    void testDuQuestionMarkShowsHelp() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream origOut = System.out;
        try {
            System.setOut(new PrintStream(baos, true, StandardCharsets.UTF_8));
            int exitCode = new CommandLine(new App()).execute("du", "?");
            assertEquals(0, exitCode, "du ? はヘルプを表示して正常終了 (0) するべき");
            assertTrue(baos.toString(StandardCharsets.UTF_8).contains("Usage:"));
        } finally {
            System.setOut(origOut);
        }
    }

    @Test
    void testDuInvalidPathReturnsError() {
        ByteArrayOutputStream errBaos = new ByteArrayOutputStream();
        PrintStream origErr = System.err;
        try {
            System.setErr(new PrintStream(errBaos, true, StandardCharsets.UTF_8));
            int exitCode = new CommandLine(new App()).execute("du", "invalid*path");
            assertEquals(1, exitCode, "不正なパス文字は終了コード 1 で終了するべき");
            assertTrue(errBaos.toString(StandardCharsets.UTF_8).contains("Error: Invalid path"));
        } finally {
            System.setErr(origErr);
        }
    }

    @Test
    void testFiQuestionMarkShowsHelp() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream origOut = System.out;
        try {
            System.setOut(new PrintStream(baos, true, StandardCharsets.UTF_8));
            int exitCode = new CommandLine(new App()).execute("fi", "?");
            assertEquals(0, exitCode, "fi ? はヘルプを表示して正常終了 (0) するべき");
            assertTrue(baos.toString(StandardCharsets.UTF_8).contains("Usage:"));
        } finally {
            System.setOut(origOut);
        }
    }

    @Test
    void testDuOutputsElapsed() throws Exception {
        Files.createFile(tempDir.resolve("elapsed_test.txt"));
        String output = runWithOutputCapture("du", tempDir.toString());
        assertTrue(output.contains("Elapsed:"), "du の出力に Elapsed: が含まれるべき");
    }

    @Test
    void testMvQuestionMarkShowsHelp() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream origOut = System.out;
        try {
            System.setOut(new PrintStream(baos, true, StandardCharsets.UTF_8));
            int exitCode = new CommandLine(new App()).execute("mv", "?");
            assertEquals(0, exitCode, "mv ? はヘルプを表示して正常終了 (0) するべき");
            assertTrue(baos.toString(StandardCharsets.UTF_8).contains("Usage:"));
        } finally {
            System.setOut(origOut);
        }
    }

    @Test
    void testMvMissingDestinationReturnsError() {
        ByteArrayOutputStream errBaos = new ByteArrayOutputStream();
        PrintStream origErr = System.err;
        try {
            System.setErr(new PrintStream(errBaos, true, StandardCharsets.UTF_8));
            int exitCode = new CommandLine(new App()).execute("mv", "single_arg.txt");
            assertEquals(1, exitCode, "移動先省略時は終了コード 1 で終了するべき");
            assertTrue(errBaos.toString(StandardCharsets.UTF_8).contains("Error: Source and destination paths are required."));
        } finally {
            System.setErr(origErr);
        }
    }

    @Test
    void testMvDryRunOption() throws Exception {
        Path src = Files.writeString(tempDir.resolve("dry_src.txt"), "dry content");
        Path dest = tempDir.resolve("dry_dest.txt");

        String output = runWithOutputCapture("mv", "-d", src.toString(), dest.toString());
        assertTrue(output.contains("[DRY RUN]"));
        assertTrue(Files.exists(src), "dry-run では移動元ファイルが残るべき");
        assertFalse(Files.exists(dest), "dry-run では移動先ファイルが作られないべき");
    }

    @Test
    void testMvForceOption() throws Exception {
        Path src = Files.writeString(tempDir.resolve("force_src.txt"), "new content");
        Path dest = Files.writeString(tempDir.resolve("force_dest.txt"), "old content");

        String output = runWithOutputCapture("mv", "-f", src.toString(), dest.toString());
        assertTrue(output.contains("[OVERWRITTEN]"));
        assertFalse(Files.exists(src));
        assertTrue(Files.exists(dest));
        assertEquals("new content", Files.readString(dest), "-f で上書きされるべき");
    }

    @Test
    void testMvNoClobberOption() throws Exception {
        Path src = Files.writeString(tempDir.resolve("no_clobber_src.txt"), "new content");
        Path dest = Files.writeString(tempDir.resolve("no_clobber_dest.txt"), "existing content");

        String output = runWithOutputCapture("mv", "-n", src.toString(), dest.toString());
        assertTrue(output.contains("[SKIPPED]"));
        assertTrue(Files.exists(src), "-n でスキップされた場合移動元は残るべき");
        assertEquals("existing content", Files.readString(dest), "既存ファイルの内容は維持されるべき");
    }

    @Test
    void testMvMultipleSourcesToDirectory() throws Exception {
        Path f1 = Files.writeString(tempDir.resolve("multi1.txt"), "content 1");
        Path f2 = Files.writeString(tempDir.resolve("multi2.txt"), "content 2");
        Path targetDir = Files.createDirectory(tempDir.resolve("multi_dest"));

        String output = runWithOutputCapture("mv", f1.toString(), f2.toString(), targetDir.toString());
        assertTrue(output.contains("[MOVED] multi1.txt"));
        assertTrue(output.contains("[MOVED] multi2.txt"));
        assertFalse(Files.exists(f1));
        assertFalse(Files.exists(f2));
        assertTrue(Files.exists(targetDir.resolve("multi1.txt")));
        assertTrue(Files.exists(targetDir.resolve("multi2.txt")));
    }

    @Test
    void testMvToNonExistentDirectoryWithTrailingSlash() throws Exception {
        Path f1 = Files.writeString(tempDir.resolve("slash1.txt"), "hello");
        String targetDirPath = tempDir.resolve("new_sub_dir").toString() + "/";

        String output = runWithOutputCapture("mv", f1.toString(), targetDirPath);
        assertTrue(output.contains("[MOVED] slash1.txt"));
        assertTrue(Files.exists(tempDir.resolve("new_sub_dir").resolve("slash1.txt")));
    }

    @Test
    void testMvMultipleSourcesToNonExistentDirectoryWithTrailingSlash() throws Exception {
        Path f1 = Files.writeString(tempDir.resolve("slash_multi1.txt"), "hello 1");
        Path f2 = Files.writeString(tempDir.resolve("slash_multi2.txt"), "hello 2");
        String targetDirPath = tempDir.resolve("new_sub_dir2").toString() + "\\";

        String output = runWithOutputCapture("mv", f1.toString(), f2.toString(), targetDirPath);
        assertTrue(output.contains("[MOVED] slash_multi1.txt"));
        assertTrue(output.contains("[MOVED] slash_multi2.txt"));
        assertTrue(Files.exists(tempDir.resolve("new_sub_dir2").resolve("slash_multi1.txt")));
        assertTrue(Files.exists(tempDir.resolve("new_sub_dir2").resolve("slash_multi2.txt")));
    }

    @Test
    void testMvMultipleSourcesToNonDirectoryReturnsError() throws Exception {
        Path f1 = Files.writeString(tempDir.resolve("err1.txt"), "1");
        Path f2 = Files.writeString(tempDir.resolve("err2.txt"), "2");
        Path targetNonDir = tempDir.resolve("non_existent_file");

        ByteArrayOutputStream errBaos = new ByteArrayOutputStream();
        PrintStream origErr = System.err;
        try {
            System.setErr(new PrintStream(errBaos, true, StandardCharsets.UTF_8));
            int exitCode = new CommandLine(new App()).execute("mv", f1.toString(), f2.toString(), targetNonDir.toString());
            assertEquals(1, exitCode);
            assertTrue(errBaos.toString(StandardCharsets.UTF_8).contains("When moving multiple sources, the target must be a directory."));
        } finally {
            System.setErr(origErr);
        }
    }

    @Test
    void testMvVerboseOption() throws Exception {
        Path src = Files.writeString(tempDir.resolve("v_src.txt"), "verbose");
        Path dest = tempDir.resolve("v_dest.txt");

        String output = runWithOutputCapture("mv", "-v", src.toString(), dest.toString());
        assertTrue(output.contains("[MOVED] " + src.toAbsolutePath() + " -> " + dest.toAbsolutePath()));
    }

    @Test
    void testMvConflictingForceAndNoClobber() {
        ByteArrayOutputStream errBaos = new ByteArrayOutputStream();
        PrintStream origErr = System.err;
        try {
            System.setErr(new PrintStream(errBaos, true, StandardCharsets.UTF_8));
            int exitCode = new CommandLine(new App()).execute("mv", "-f", "-n", "a.txt", "b.txt");
            assertEquals(1, exitCode, "--force と --no-clobber の同時指定はエラー");
            assertTrue(errBaos.toString(StandardCharsets.UTF_8).contains("Cannot specify both --force and --no-clobber"));
        } finally {
            System.setErr(origErr);
        }
    }

    @Test
    void testGrepDirectoryBasic() throws Exception {
        Files.writeString(tempDir.resolve("sample1.txt"), "hello world\nanother line");
        Files.writeString(tempDir.resolve("sample2.txt"), "goodbye world");

        String output = runWithOutputCapture("grep", "hello", tempDir.toString());
        assertTrue(output.contains("sample1.txt: hello world"));
        assertTrue(output.contains("Found 1 matches"));
    }

    @Test
    void testAliasGAndGr() throws Exception {
        Files.writeString(tempDir.resolve("sample.txt"), "alias test match");

        String outputG = runWithOutputCapture("g", "alias", tempDir.toString());
        assertTrue(outputG.contains("alias test match"));

        String outputGr = runWithOutputCapture("gr", "alias", tempDir.toString());
        assertTrue(outputGr.contains("alias test match"));
    }

    @Test
    void testGrepSingleFileDirectly() throws Exception {
        Path singleFile = Files.writeString(tempDir.resolve("direct.txt"), "first\nsingle match\nthird");

        String output = runWithOutputCapture("g", "single", singleFile.toString());
        assertTrue(output.contains("direct.txt: single match"));
        assertTrue(output.contains("Found 1 matches"));
    }

    @Test
    void testGrepFilesWithMatchesFlags() throws Exception {
        Files.writeString(tempDir.resolve("file_a.txt"), "target line 1\ntarget line 2");
        Files.writeString(tempDir.resolve("file_b.txt"), "no match");

        // -l flag
        String outputL = runWithOutputCapture("g", "target", tempDir.toString(), "-l");
        assertTrue(outputL.contains("file_a.txt"));
        assertFalse(outputL.contains("target line 1"), "-l ではマッチ行の内容は出ないべき");

        // -f flag (alias)
        String outputF = runWithOutputCapture("g", "target", tempDir.toString(), "-f");
        assertTrue(outputF.contains("file_a.txt"));
        assertFalse(outputF.contains("target line 1"));
    }

    @Test
    void testGrepLineNumbersFlag() throws Exception {
        Files.writeString(tempDir.resolve("lines.txt"), "line 1\ntarget line\nline 3");

        String output = runWithOutputCapture("g", "target", tempDir.toString(), "-n");
        assertTrue(output.contains(":2: target line"));
    }

    @Test
    void testGrepNonExistentTargetReturnsError() {
        ByteArrayOutputStream errBaos = new ByteArrayOutputStream();
        PrintStream origErr = System.err;
        try {
            System.setErr(new PrintStream(errBaos, true, StandardCharsets.UTF_8));
            int exitCode = new CommandLine(new App()).execute("g", "query", tempDir.resolve("not_found").toString());
            assertEquals(1, exitCode);
            assertTrue(errBaos.toString(StandardCharsets.UTF_8).contains("does not exist"));
        } finally {
            System.setErr(origErr);
        }
    }

    @Test
    void testCliDuExcludeAndHidden() throws Exception {
        Files.writeString(tempDir.resolve(".env"), "SECRET");
        Path build = Files.createDirectory(tempDir.resolve("build"));
        Files.writeString(build.resolve("app.jar"), "binary");
        Files.writeString(tempDir.resolve("main.txt"), "text");

        // デフォルト: 隠しファイル除外 (main.txt + build/app.jar = 2 files)
        String outDefault = runWithOutputCapture("du", tempDir.toString());
        assertTrue(outDefault.contains("Files:                  2"));

        // -x build: build 除外 (main.txt = 1 file)
        String outExclude = runWithOutputCapture("du", "-x", "build", tempDir.toString());
        assertTrue(outExclude.contains("Files:                  1"));

        // -h: 隠しファイル包含 (.env + main.txt + build/app.jar = 3 files)
        String outHidden = runWithOutputCapture("du", "-h", tempDir.toString());
        assertTrue(outHidden.contains("Files:                  3"));

        // -h -x build: 隠しファイル包含かつ build 除外 (.env + main.txt = 2 files)
        String outHiddenExclude = runWithOutputCapture("du", "-h", "-x", "build", tempDir.toString());
        assertTrue(outHiddenExclude.contains("Files:                  2"));
    }

    @Test
    void testCliFiExcludeAndHidden() throws Exception {
        Files.writeString(tempDir.resolve(".config"), "cfg");
        Path bin = Files.createDirectory(tempDir.resolve("bin"));
        Files.writeString(bin.resolve("run.exe"), "exe");
        Files.writeString(tempDir.resolve("app.java"), "class");

        // デフォルト: 隠しファイル除外
        String outDefault = runWithOutputCapture("fi", "", tempDir.toString());
        assertFalse(outDefault.contains(".config"));
        assertTrue(outDefault.contains("app.java"));
        assertTrue(outDefault.contains("bin"));

        // -x bin: bin 除外
        String outExclude = runWithOutputCapture("fi", "-x", "bin", "", tempDir.toString());
        assertFalse(outExclude.contains("bin"));
        assertTrue(outExclude.contains("app.java"));

        // -h: 隠しファイル包含
        String outHidden = runWithOutputCapture("fi", "-h", "", tempDir.toString());
        assertTrue(outHidden.contains(".config"));
        assertTrue(outHidden.contains("app.java"));

        // -h -x bin: 隠しファイル含むが bin は除外
        String outBoth = runWithOutputCapture("fi", "-h", "-x", "bin", "", tempDir.toString());
        assertTrue(outBoth.contains(".config"));
        assertFalse(outBoth.contains("bin"));
    }

    @Test
    void testCliGrepExcludeAndHidden() throws Exception {
        Files.writeString(tempDir.resolve(".secret.txt"), "magic_token");
        Path build = Files.createDirectory(tempDir.resolve("build"));
        Files.writeString(build.resolve("compiled.txt"), "magic_token");
        Files.writeString(tempDir.resolve("code.txt"), "magic_token");

        // デフォルト: 隠しファイル (.secret.txt) は grep されない (code.txt と build/compiled.txt)
        String outDefault = runWithOutputCapture("g", "magic_token", tempDir.toString(), "-l");
        assertFalse(outDefault.contains(".secret.txt"));
        assertTrue(outDefault.contains("code.txt"));
        assertTrue(outDefault.contains("compiled.txt"));

        // -x build: build 除外
        String outExclude = runWithOutputCapture("g", "magic_token", tempDir.toString(), "-x", "build", "-l");
        assertFalse(outExclude.contains("build"));
        assertTrue(outExclude.contains("code.txt"));

        // -h: 隠しファイル包含
        String outHidden = runWithOutputCapture("g", "magic_token", tempDir.toString(), "-h", "-l");
        assertTrue(outHidden.contains(".secret.txt"));

        // -h -x build: 隠しファイル含めるが build 除外
        String outBoth = runWithOutputCapture("g", "magic_token", tempDir.toString(), "-h", "-x", "build", "-l");
        assertTrue(outBoth.contains(".secret.txt"));
        assertFalse(outBoth.contains("build"));
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