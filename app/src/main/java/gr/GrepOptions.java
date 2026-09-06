package gr;

import java.util.Set;

public record GrepOptions(
    boolean caseSensitive,
    boolean regex,
    Set<String> exts,
    boolean filesWithMatches,
    boolean lineNumbers,
    Set<String> excludeDirs,
    boolean hidden
) {
    public GrepOptions(boolean caseSensitive, boolean regex, Set<String> exts, boolean filesWithMatches, boolean lineNumbers) {
        this(caseSensitive, regex, exts, filesWithMatches, lineNumbers, Set.of(), false);
    }

    public static final GrepOptions DEFAULT = new GrepOptions(
        false,
        false,
        Set.of(),
        false,
        false,
        Set.of(),
        false
    );
}
