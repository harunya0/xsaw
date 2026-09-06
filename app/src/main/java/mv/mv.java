package mv;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class mv {
    public MoveResult move(Path source, Path target) throws IOException {
        return move(source, target, MoveOptions.DEFAULT);
    }

    public MoveResult move(Path source, Path target, MoveOptions options) throws IOException {
        Path src = source.toAbsolutePath();
        Path dest = target.toAbsolutePath();

        if (!Files.exists(src)) {
            throw new FileNotFoundException("Source file does not exist: " + src);
        }

        String targetStr = target.toString();
        boolean isExpectedDirTarget = targetStr.endsWith("/") || targetStr.endsWith("\\") || Files.isDirectory(dest);
        Path finalDest = dest;
        if (isExpectedDirTarget) {
            finalDest = dest.resolve(src.getFileName());
        }

        Path parent = finalDest.getParent();
        if (parent != null && !Files.exists(parent) && !options.dryRun()) {
            Files.createDirectories(parent);
        }

        boolean distExists = Files.exists(finalDest);
        boolean isDir = Files.isDirectory(src);
        long size = isDir ? 0 : Files.size(src);
        Instant now = Instant.now();

        if (distExists && options.noClobber()) {
            return new MoveResult(src, finalDest, size, now, isDir, MoveStatus.SKIPPED);
        }

        if (distExists && !options.force()) {
            throw new FileAlreadyExistsException("Destination already exists: " + finalDest);
        }

        if (options.dryRun()) {
            return new MoveResult(src, finalDest, size, now, isDir, MoveStatus.DRY_RUN);
        }

        MoveStatus status = distExists ? MoveStatus.OVERWRITTEN : MoveStatus.MOVED;
        try {
            if (options.force()) {
                Files.move(src, finalDest, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } else {
                Files.move(src, finalDest);
            }
        } catch (AtomicMoveNotSupportedException e) {
            if (options.force()) {
                Files.move(src, finalDest, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.move(src, finalDest);
            }
        }

        return new MoveResult(src, finalDest, size, now, isDir, status);
    }

    public List<MoveResult> moveAll(List<Path> sources, Path targetDir, MoveOptions options) throws IOException {
        List<MoveResult> results = new ArrayList<>();
        for (Path src : sources) {
            results.add(move(src, targetDir.resolve(src.getFileName()), options));
        }
        return results;
    }
    public static Path generateUniquePath(Path path) {
        if (!Files.exists(path)) return path;
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        Path parent = path.getParent();
        int count = 1;
        Path candidate;
        do {
            candidate = (parent == null) ? Path.of(base + " (" + count + ")" + ext)
                                        : parent.resolve(base + " (" + count + ")" + ext);
            count++;
        } while (Files.exists(candidate));
        return candidate;
    }
}
