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

public class fi {
    public static final class FindOptions {
        private final boolean caseSensitive;
        private final boolean dirOnly;
        private final boolean fileOnly;
        private final String ext;

        public FindOptions (boolean caseSensitive, boolean dirOnly, boolean fileOnly, String ext) {
            this.caseSensitive = caseSensitive;
            this.dirOnly = dirOnly;
            this.fileOnly = fileOnly;
            this.ext = ext;
        }

        public boolean caseSensitive() { return caseSensitive; }
        public boolean dirOnly() { return dirOnly; }
        public boolean fileOnly() { return fileOnly; }
        public String ext() { return ext; }
    }

    public FindResult find (Path root, String query, FindOptions options) throws IOException {
        long startTime = System.currentTimeMillis();
        ConcurrentLinkedQueue<Path> matchedPaths = new ConcurrentLinkedQueue<>();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            try (var stream = Files.newDirectoryStream(root)) {
                for (Path entry : stream) {
                    if (Files.isDirectory(entry)) {
                        if (isMatch(entry, true, query, options)) {
                            matchedPaths.add(entry);
                        }
                        executor.submit(() -> scanSubTree(entry, query, options, matchedPaths));
                    } else if (Files.isRegularFile(entry)) {
                        if (isMatch(entry, false, query, options)) {
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
        ConcurrentLinkedQueue<Path> matchedPaths
    ) {
        try {
            Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                @Override 
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (isMatch(file, false, query, options)) {
                        matchedPaths.add(file);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override 
                public FileVisitResult preVisitDirectory(Path dirPath, BasicFileAttributes attrs) {
                    if (!dirPath.equals(dir) && isMatch(dirPath, true, query, options)) {
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

    private boolean isMatch(Path path, boolean isDir, String query, FindOptions options) {
        if (options.dirOnly() && !isDir) return false;
        if (options.fileOnly() && isDir) return false;

        Path fileNamePath = path.getFileName();
        if (fileNamePath == null) return false;
        String fileName = fileNamePath.toString();

        if (options.ext() != null && !options.ext().isBlank()) {
            if (isDir) return false;
            String cleanExt = options.ext().startsWith(".") ? options.ext().substring(1) : options.ext();
            if (!fileName.toLowerCase().endsWith("." + cleanExt.toLowerCase())) {
                return false;
            }
        }

        if (query == null || query.isEmpty()) {
            return true;
        }
        if(options.caseSensitive()) {
            return fileName.contains(query);
        } else {
            return fileName.toLowerCase().contains(query.toLowerCase());
        }
    }
}
