package gr;

import java.nio.file.Path;

public record GrepMatch(
    Path file,
    int lineNumber,
    String lineContent
) {

}
