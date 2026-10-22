package eu.amadeos.apps.imagescan.service.image;

import eu.amadeos.apps.imagescan.domain.ScannedImage;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.Optional;

/**
 * Undo and redo for edits of one image. Each step keeps a whole image, so only the last
 * {@link #DEFAULT_LIMIT} are kept: an A4 page at 600 dpi in colour takes about 100 MB.
 */
public final class ImageHistory {
    public static final int DEFAULT_LIMIT = 8;

    private final int limit;
    private final Deque<ScannedImage> undo = new ArrayDeque<>();
    private final Deque<ScannedImage> redo = new ArrayDeque<>();
    private ScannedImage current;

    public ImageHistory() {
        this(DEFAULT_LIMIT);
    }

    public ImageHistory(int limit) {
        if (limit < 0) throw new IllegalArgumentException("limit: " + limit);
        this.limit = limit;
    }

    /** Starts over with a new image (a scan, an opened file): nothing to undo. */
    public void reset(ScannedImage image) {
        undo.clear();
        redo.clear();
        current = image;
    }

    public void clear() {
        reset(null);
    }

    public Optional<ScannedImage> current() {
        return Optional.ofNullable(current);
    }

    // --8<-- [start:push]
    /** Records an edit: the old image can be brought back, what was undone before cannot be redone. */
    public void push(ScannedImage edited) {
        Objects.requireNonNull(edited, "edited");
        if (current != null && limit > 0) {
            undo.push(current);
            while (undo.size() > limit) undo.removeLast();
        }
        redo.clear();
        current = edited;
    }

    public boolean canUndo() {
        return !undo.isEmpty();
    }

    public boolean canRedo() {
        return !redo.isEmpty();
    }

    public Optional<ScannedImage> undo() {
        if (undo.isEmpty()) return Optional.empty();
        redo.push(current);
        current = undo.pop();
        return Optional.of(current);
    }
    // --8<-- [end:push]

    public Optional<ScannedImage> redo() {
        if (redo.isEmpty()) return Optional.empty();
        undo.push(current);
        current = redo.pop();
        return Optional.of(current);
    }
}
