package gr;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public record GrepResult(
    List<GrepMatch> matches,
    int fileSearched,
    long elapsedMillis
) {
    public Set<Path> matchingFiles() {
        if (matches == null) {
            return Set.of();
        }
        Set<Path> files = new LinkedHashSet<>();
        for (GrepMatch match : matches) {
            if (match != null && match.file() != null) {
                files.add(match.file());
            }
        }
        return files;
    }

    public int matchCount() {
        return matches == null ? 0 : matches.size();
    }
}
