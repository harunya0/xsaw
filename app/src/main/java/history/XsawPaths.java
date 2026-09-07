package history;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class XsawPaths {
    private XsawPaths(){}

    public static Path getBaseDir() {
        String prop = System.getProperty("xsaw.home");
        if (prop != null && !prop.isBlank()) {
            return Path.of(prop);
        }

        String env = System.getenv("XSAW_HOME");
        if (env != null && !env.isBlank()) {
            return Path.of(env);
        }

        return Path.of(System.getProperty("user.home"), ".xsaw");
    }

    public static Path getDatabasePath() {
        return getBaseDir().resolve("history.db");
    }

    public static Path getTrashDir() {
        return getBaseDir().resolve("trash");
    }

    public static void ensureDirectoriesExist() throws IOException {
        Files.createDirectories(getTrashDir());
    }
}
