package eu.amadeos.apps.imagescan.repository;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

/**
 * Reads and writes image files. Writing goes to a temporary file next to the target that then replaces it,
 * so a full disk never leaves half an image; an existing file keeps its permissions.
 */
public class ImageFiles {
    /** Larger files are refused instead of filling the memory. */
    public static final long MAX_SIZE = 1024L * 1024 * 1024;

    public byte[] read(Path file) throws IOException {
        long size = Files.size(file);
        if (size > MAX_SIZE) throw new IOException("File too large: " + size + " bytes");
        return Files.readAllBytes(file);
    }

    public void write(Path file, byte[] bytes) throws IOException {
        Path target = file.toAbsolutePath();
        Set<PosixFilePermission> permissions = existingPermissions(target);
        Path temp = Files.createTempFile(target.getParent(), "." + target.getFileName(), ".tmp");
        try {
            Files.write(temp, bytes);
            if (permissions != null) Files.setPosixFilePermissions(temp, permissions);
            else defaultPermissions(temp);
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static Set<PosixFilePermission> existingPermissions(Path file) {
        try {
            return Files.exists(file) ? Files.getPosixFilePermissions(file) : null;
        } catch (IOException | UnsupportedOperationException e) {
            return null;
        }
    }

    /** Temporary files are owner-only; a new image should be readable like any other file the user saves. */
    private static void defaultPermissions(Path file) {
        try {
            Files.setPosixFilePermissions(file, Set.of(PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE, PosixFilePermission.GROUP_READ, PosixFilePermission.OTHERS_READ));
        } catch (IOException | UnsupportedOperationException e) {
            // Not POSIX (Windows): the default permissions apply.
        }
    }
}
