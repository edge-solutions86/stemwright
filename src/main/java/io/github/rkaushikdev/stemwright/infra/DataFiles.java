package io.github.rkaushikdev.stemwright.infra;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Optional;

/**
 * The file operations the desk's storage is built from, all in UTF-8.
 *
 * <p>Writes that are not appends go through {@link #atomicWrite}, which writes a neighbouring
 * temporary file and renames it over the target. A rename is atomic on both of the platforms this
 * runs on, so a reader never sees a half written snapshot, and a crash leaves the old one intact.
 *
 * <p>Every operation reports an I/O failure as an {@link UncheckedIOException} around the {@link
 * IOException} the file system raised, which {@link #isMissing} can tell apart from the rest.
 */
public final class DataFiles {

    private DataFiles() {}

    /** Creates the directory and any parents it lacks, and does nothing when it already exists. */
    public static void ensureDirectory(Path path) {
        try {
            Files.createDirectories(path);
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    /**
     * Replaces the file's contents in one step, creating the directory it belongs in when needed. The
     * text is written to the file's name with {@code .tmp} added and then moved over the file.
     */
    public static void atomicWrite(Path path, String contents) {
        Path absolute = path.toAbsolutePath();
        ensureDirectory(absolute.getParent());
        Path temporary = absolute.resolveSibling(absolute.getFileName() + ".tmp");
        try {
            Files.writeString(temporary, contents, StandardCharsets.UTF_8);
            Files.move(temporary, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    /**
     * The file's text, or nothing when there is no such file. Bytes that are not UTF-8, such as the
     * torn end of a write a crash interrupted, read as U+FFFD rather than failing the read, so the
     * damage is reported by whatever parses the text, at the line where it is.
     */
    public static Optional<String> readIfPresent(Path path) {
        try {
            return Optional.of(new String(Files.readAllBytes(path), StandardCharsets.UTF_8));
        } catch (NoSuchFileException missing) {
            return Optional.empty();
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    /**
     * True for the failure a file system raises for a path that does not exist, whether bare or
     * wrapped as these operations wrap it, and false for any other failure and for {@code null}.
     */
    public static boolean isMissing(Throwable error) {
        return error instanceof NoSuchFileException
                || (error instanceof UncheckedIOException wrapped && wrapped.getCause() instanceof NoSuchFileException);
    }

    /**
     * Removes the file, or the directory and everything in it, and tolerates a path that is not
     * there. A symbolic link is removed, never followed.
     */
    public static void removeIfPresent(Path path) {
        try {
            Files.walkFileTree(path, new Remover());
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    /** Deletes a tree from the leaves up, passing over anything that has already gone. */
    private static final class Remover extends SimpleFileVisitor<Path> {

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
            Files.deleteIfExists(file);
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException error) throws IOException {
            if (error instanceof NoSuchFileException) {
                return FileVisitResult.CONTINUE;
            }
            throw error;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path directory, IOException error) throws IOException {
            if (error != null) {
                throw error;
            }
            Files.deleteIfExists(directory);
            return FileVisitResult.CONTINUE;
        }
    }
}
