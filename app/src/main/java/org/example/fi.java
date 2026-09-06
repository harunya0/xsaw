package org.example;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

public class fi {
    public static final class FindOptions {
        private final boolean caseSensitive;
        private final boolean dirOnly;
        private final boolean fileOnly;
        private final boolean regex;
        private final java.util.Set<String> exts;

        public FindOptions(boolean caseSensitive, boolean dirOnly, boolean fileOnly, boolean regex, java.util.Set<String> exts) {
            this.caseSensitive = caseSensitive;
            this.dirOnly = dirOnly;
            this.fileOnly = fileOnly;
            this.regex = regex;
            this.exts = exts == null ? java.util.Set.of() : exts;
        }

        public FindOptions(boolean caseSensitive, boolean dirOnly, boolean fileOnly, java.util.Set<String> exts) {
            this(caseSensitive, dirOnly, fileOnly, false, exts);
        }

        public FindOptions(boolean caseSensitive, boolean dirOnly, boolean fileOnly) {
            this(caseSensitive, dirOnly, fileOnly, java.util.Set.of());
        }

        public boolean caseSensitive() { return caseSensitive; }
        public boolean dirOnly() { return dirOnly; }
        public boolean fileOnly() { return fileOnly; }
        public boolean regex() { return regex; }
        public java.util.Set<String> exts() { return exts; }
    }

    public FindResult find(Path root, String query, FindOptions options) throws IOException {
        long startTime = System.currentTimeMillis();
        ConcurrentLinkedQueue<Path> matchedPaths = new ConcurrentLinkedQueue<>();

        Pattern pattern = null;
        if (options.regex() && query != null && !query.isEmpty()) {
            int flags = options.caseSensitive() ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
            pattern = Pattern.compile(query, flags);
        }

        Pattern finalPattern = pattern;

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            try (var stream = Files.newDirectoryStream(root)) {
                for (Path entry : stream) {
                    if (Files.isDirectory(entry)) {
                        if (isMatch(entry, true, query, finalPattern, options)) {
                            matchedPaths.add(entry);
                        }
                        executor.submit(() -> scanSubTree(entry, query, options, finalPattern, matchedPaths));
                    } else if (Files.isRegularFile(entry)) {
                        if (isMatch(entry, false, query, finalPattern, options)) {
                            matchedPaths.add(entry);
                        }
                    }
                }
            }
        }

        long elapsed = System.currentTimeMillis() - startTime;
        List<Path> matches = matchedPaths.stream().sorted().toList();
        return new FindResult(root, matches, elapsed);
    }

    private void scanSubTree(
        Path dir,
        String query,
        FindOptions options,
        Pattern pattern,
        ConcurrentLinkedQueue<Path> matchedPaths
    ) {
        try {
            Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                @Override 
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (isMatch(file, false, query, pattern, options)) {
                        matchedPaths.add(file);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override 
                public FileVisitResult preVisitDirectory(Path dirPath, BasicFileAttributes attrs) {
                    if (!dirPath.equals(dir) && isMatch(dirPath, true, query, pattern, options)) {
                        matchedPaths.add(dirPath);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            // through the exception
        }
    }

    private boolean isMatch(Path path, boolean isDir, String query, Pattern pattern, FindOptions options) {
        if (options.dirOnly() && !isDir) return false;
        if (options.fileOnly() && isDir) return false;

        Path fileNamePath = path.getFileName();
        if (fileNamePath == null) return false;
        String fileName = fileNamePath.toString();

        if (options.exts() != null && !options.exts().isEmpty()) {
            if (isDir) return false;
            int dot = fileName.lastIndexOf('.');
            String fileExt = (dot > 0 && dot < fileName.length() - 1) ? fileName.substring(dot + 1).toLowerCase() : "";
            if (!options.exts().contains(fileExt)) {
                return false;
            }
        }

        if (query == null || query.isEmpty()) {
            return true;
        }
        
        if (pattern != null) {
            return pattern.matcher(fileName).find();
        }

        if (options.caseSensitive()) {
            return fileName.contains(query);
        } else {
            return fileName.toLowerCase().contains(query.toLowerCase());
        }
    }
}
