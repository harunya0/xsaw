package gr;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

public class gr {
    public GrepResult grep(
        Path root,
        String query,
        GrepOptions options
    ) throws IOException {
        long start = System.currentTimeMillis();
        List<Path> files = collectFiles(root, options);
        return scanFilesParallel(files, query, options, start);
    }

    public GrepResult grepFiles(
        List<Path> files,
        String query,
        GrepOptions options
    ) {
        long start = System.currentTimeMillis();
        return scanFilesParallel(files, query, options, start);
    }

    public GrepResult grepStream(
        BufferedReader reader,
        String query,
        GrepOptions options
    ) throws IOException {
        long start = System.currentTimeMillis();
        List<GrepMatch> matches = new ArrayList<>();
        Pattern pattern = compilePattern(query, options);
        String lowerQuery = (!options.caseSensitive() && query != null) ? query.toLowerCase() : null;

        String line;
        int lineNum = 1;
        while ((line = reader.readLine()) != null) {
            if (isMatch(line, pattern, query, lowerQuery, options)) {
                matches.add(new GrepMatch(null, lineNum, line));
            }
            lineNum++;
        }
        long elapsed = System.currentTimeMillis() - start;
        return new GrepResult(matches, 0, elapsed);
    }

    private GrepResult scanFilesParallel(
        List<Path> files,
        String query,
        GrepOptions options,
        long startTime
    ) {
        ConcurrentLinkedQueue<GrepMatch> matches = new ConcurrentLinkedQueue<>();
        AtomicInteger filesSearched = new AtomicInteger(0);
        Pattern pattern = compilePattern(query, options);
        String lowerQuery = (!options.caseSensitive() && query != null) ? query.toLowerCase() : null;
        
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (Path file : files) {
                executor.submit(() -> {
                    filesSearched.incrementAndGet();
                    scanSingleFile(file, pattern, query, lowerQuery, options, matches);
                });
            }
        }

        long elapsed = System.currentTimeMillis() - startTime;
        return new GrepResult(matches.stream().toList(), filesSearched.get(), elapsed);
    }

    private void scanSingleFile(
        Path file,
        Pattern pattern,
        String query,
        String lowerQuery,
        GrepOptions options,
        ConcurrentLinkedQueue<GrepMatch> matches
    ) {
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            int lineNum = 1;
            while ((line = reader.readLine()) != null) {
                if (isMatch(line, pattern, query, lowerQuery, options)) {
                    matches.add(new GrepMatch(file, lineNum, line));
                    if (options.filesWithMatches()) {
                        break;
                    }
                }
                lineNum++;
            }
        } catch (IOException e) {
            // Handle exception or log it
        }
    }

    private List<Path> collectFiles(
        Path root,
        GrepOptions options
    ) throws IOException {
        List<Path> files = new ArrayList<>();

        if (Files.isRegularFile(root)) {
            if ((options.hidden() || !isHidden(root)) && isExtMatch(root, options)) {
                files.add(root);
            }
            return files;
        }

        if (!Files.isDirectory(root)) {
            return files;
        }

        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (!dir.equals(root)) {
                    if ((!options.hidden() && isHidden(dir)) || isExcludedDir(dir, options.excludeDirs())) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                }
                return FileVisitResult.CONTINUE;
            }

            @Override 
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (!options.hidden() && isHidden(file)) {
                    return FileVisitResult.CONTINUE;
                }
                if (isExtMatch(file, options)) {
                    files.add(file);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override 
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                return FileVisitResult.CONTINUE;
            }
        });

        return files;
    }

    private static boolean isHidden(Path path) {
        Path fileName = path.getFileName();
        if (fileName != null && fileName.toString().startsWith(".")) {
            return true;
        }
        try {
            return Files.isHidden(path);
        } catch (IOException | SecurityException e) {
            return false;
        }
    }

    private static boolean isExcludedDir(Path dir, java.util.Set<String> excludeDirs) {
        if (excludeDirs == null || excludeDirs.isEmpty()) {
            return false;
        }
        Path fileName = dir.getFileName();
        if (fileName == null) return false;
        String name = fileName.toString();
        for (String excluded : excludeDirs) {
            if (name.equalsIgnoreCase(excluded)) {
                return true;
            }
        }
        return false;
    }

    private boolean isExtMatch(
        Path file,
        GrepOptions options
    ) {
        if (options.exts() == null || options.exts().isEmpty()) {
            return true;
        }
        Path fileNamePath = file.getFileName();
        if (fileNamePath == null) return false;
        String fileName = fileNamePath.toString();
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() -1) return false;
        String ext = fileName.substring(dot + 1).toLowerCase();
        return options.exts().contains(ext);
    }

    private Pattern compilePattern(
        String query,
        GrepOptions options
    ) {
        if (!options.regex() || query == null || query.isEmpty()) {
            return null;
        }
        int flags = options.caseSensitive() ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
        return Pattern.compile(query, flags);
    }

    private boolean isMatch(
        String line,
        Pattern pattern,
        String query,
        String lowerQuery,
        GrepOptions options
    ) {
        if (query == null || query.isEmpty()) {
            return true;
        }
        if (pattern != null) {
            return pattern.matcher(line).find();
        }
        if (options.caseSensitive()) {
            return line.contains(query);
        } else {
            return lowerQuery != null && line.toLowerCase().contains(lowerQuery);
        }
    }
}
