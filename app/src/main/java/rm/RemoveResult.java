package rm;

import java.nio.file.Path;
import java.util.UUID;

public record RemoveResult(
    Path path,
    long sizeByte,
    boolean isDirectory,
    UUID trashUuid
) {

}
