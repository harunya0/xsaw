package org.example;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.LongAdder;

public class du {
    private static class ExtAccumulator {
        final LongAdder count = new LongAdder();
        final LongAdder bytes = new LongAdder();
    }

    public FileResult analyze(Path root) throws IOException {
        return analyze(root, java.util.Set.of());
    }

    public FileResult analyze(Path root, java.util.Set<String> fileExt) throws IOException {
        LongAdder fileCount = new LongAdder();
        LongAdder dirCount = new LongAdder();
        LongAdder totalBytes = new LongAdder();
        ConcurrentHashMap<String, ExtAccumulator> extMap = new ConcurrentHashMap<>();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            try (var stream = Files.newDirectoryStream(root)) {
                for (Path entry : stream) {
                    if (Files.isDirectory(entry)) {
                        dirCount.increment();
                        executor.submit(() -> scanSubTree(entry, fileCount, dirCount, totalBytes, extMap, fileExt));
                    } else if (Files.isRegularFile(entry)) {
                        recordFile(entry, Files.size(entry), fileCount, totalBytes, extMap, fileExt);
                    }
                }
            }
        }
        Map<String, FileResult.ExtensionStat> finalExtStats = new HashMap<>();
        for (var entry : extMap.entrySet()) {
            finalExtStats.put(entry.getKey(), new FileResult.ExtensionStat(
                entry.getValue().count.sum(),
                entry.getValue().bytes.sum()
            ));
        } 
        return new FileResult(root, fileCount.sum(), dirCount.sum(), totalBytes.sum(), Collections.unmodifiableMap(finalExtStats));
    }

    private void scanSubTree(
        Path dir,
        LongAdder fileCount,
        LongAdder dirCount,
        LongAdder totalBytes,
        ConcurrentHashMap<String, ExtAccumulator> extMap,
        java.util.Set<String> fileExt
    ) {
        try {
            Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dirPath, BasicFileAttributes attrs) {
                    if (!dirPath.equals(dir)) {
                        dirCount.increment();
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (Files.isRegularFile(file)) {
                        recordFile(file, attrs.size(), fileCount, totalBytes, extMap, fileExt);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override 
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
        }
    }

    private void recordFile(
        Path file,
        long size,
        LongAdder fileCount,
        LongAdder totalBytes,
        ConcurrentHashMap<String, ExtAccumulator> extMap,
        java.util.Set<String> fileExt
    ) {
        String ext = getFileExtension(file);
        if (fileExt != null && !fileExt.isEmpty() && !fileExt.contains(ext)) {
            return;
        }

        fileCount.increment();
        totalBytes.add(size);
        extMap.computeIfAbsent(ext, k -> new ExtAccumulator()).count.increment();
        extMap.computeIfAbsent(ext, k -> new ExtAccumulator()).bytes.add(size);
    }

    private String getFileExtension(Path file) {
        Path fileName = file.getFileName();
        if (fileName == null) return "(none)";
        String name = fileName.toString();
        int dot = name.lastIndexOf('.');
        if (dot > 0 && dot < name.length() - 1) {
            return name.substring(dot + 1).toLowerCase();
        }
        return "(none)";
    }

    public FileResult analyze(java.util.List<Path> paths, Path baseDir) throws IOException {
        return analyze(paths, baseDir, java.util.Set.of());
    }

    public FileResult analyze(java.util.List<Path> paths, Path baseDir, java.util.Set<String> fileExt) throws IOException {
        LongAdder fileCount = new LongAdder();
        LongAdder dirCount = new LongAdder();
        LongAdder totalBytes = new LongAdder();
        ConcurrentHashMap<String, ExtAccumulator> extMap = new ConcurrentHashMap<>();

        for (Path path : paths) {
            if (Files.isRegularFile(path)) {
                recordFile(path, Files.size(path), fileCount, totalBytes, extMap, fileExt);
            } else if (Files.isDirectory(path)) {
                dirCount.increment();
            }
        }

        Map<String, FileResult.ExtensionStat> finalExtStats = new HashMap<>();
        for (var entry : extMap.entrySet()) {
            finalExtStats.put(entry.getKey(), new FileResult.ExtensionStat(
                entry.getValue().count.sum(),
                entry.getValue().bytes.sum()
            ));
        }
        return new FileResult(baseDir, fileCount.sum(), dirCount.sum(), totalBytes.sum(), Collections.unmodifiableMap(finalExtStats));
    }
}
