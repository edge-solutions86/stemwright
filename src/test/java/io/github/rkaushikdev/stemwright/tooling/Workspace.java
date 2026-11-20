package io.github.rkaushikdev.stemwright.tooling;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Where the repository is, and the two ways the tools read it: every Java file under a directory,
 * and the forward slashed key a file is reported under.
 */
final class Workspace {

    private Workspace() {}

    /**
     * The repository root: the nearest directory above these classes that holds a {@code pom.xml},
     * whichever directory the tool was started from. When the classes were not loaded from a
     * directory on disk, the search starts at the working directory instead.
     */
    static Path root() {
        Path start = classesDirectory();
        for (Path candidate = start; candidate != null; candidate = candidate.getParent()) {
            if (Files.isRegularFile(candidate.resolve("pom.xml"))) {
                return candidate;
            }
        }
        throw new IllegalStateException("there is no pom.xml above " + start);
    }

    /**
     * Every regular file under the directory whose name ends {@code .java}, ordered by key. A
     * directory that does not exist holds nothing, which the callers count and refuse on.
     */
    static List<Path> javaFiles(Path root, Path directory) {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .sorted(Comparator.comparing(path -> key(root, path)))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("could not walk " + directory, e);
        }
    }

    /** The path relative to the root, separated by forward slashes on every platform. */
    static String key(Path root, Path path) {
        Path relative = root.relativize(path);
        StringBuilder key = new StringBuilder();
        for (Path part : relative) {
            if (!key.isEmpty()) {
                key.append('/');
            }
            key.append(part);
        }
        return key.toString();
    }

    /** The file's text, read as UTF-8. */
    static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + path, e);
        }
    }

    private static Path classesDirectory() {
        CodeSource source = Workspace.class.getProtectionDomain().getCodeSource();
        URL location = source == null ? null : source.getLocation();
        if (location != null && location.getProtocol().equals("file")) {
            try {
                return Path.of(location.toURI()).toAbsolutePath();
            } catch (URISyntaxException e) {
                throw new IllegalStateException("could not read the class path entry " + location, e);
            }
        }
        return Path.of("").toAbsolutePath();
    }
}
