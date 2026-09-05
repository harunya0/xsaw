package org.example;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public final class FindResult {
    private final Path rootDir;
    private final List<Path> matches;
    private final long elapsedMillis;

    public FindResult(Path rootDir, List<Path> matches, long elapsedMillis) {
        this.rootDir = rootDir;
        this.matches = matches == null ? List.of() : List.copyOf(matches);
        this.elapsedMillis = elapsedMillis;
    }

    public Path rootDir() {
        return rootDir;
    }

    public List<Path> matches() {
        return matches;
    }

    public long elapsedMillis() {
        return elapsedMillis;
    }

    public int count() {
        return matches.size();
    }

    public List<Path> relativeMatches() {
        return matches.stream()
            .map(rootDir::relativize)
            .toList();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FindResult that)) return false;
        return elapsedMillis == that.elapsedMillis &&
               Objects.equals(rootDir, that.rootDir) &&
               Objects.equals(matches, that.matches);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rootDir, matches, elapsedMillis);
    }

    @Override
    public String toString() {
        return "FindResult[" +
               "rootDir=" + rootDir + ", " +
               "matches=" + matches + ", " +
               "elapsedMillis=" + elapsedMillis + ']';
    }
}
