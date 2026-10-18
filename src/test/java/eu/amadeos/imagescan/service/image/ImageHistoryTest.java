package eu.amadeos.imagescan.service.image;

import eu.amadeos.imagescan.domain.ScannedImage;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageHistoryTest {
    private static ScannedImage image(int width) {
        return new ScannedImage(new BufferedImage(width, 1, BufferedImage.TYPE_BYTE_GRAY), 300);
    }

    @Test
    void undoesAndRedoes() {
        ImageHistory history = new ImageHistory();
        ScannedImage a = image(1);
        ScannedImage b = image(2);
        ScannedImage c = image(3);
        history.reset(a);
        assertFalse(history.canUndo());
        history.push(b);
        history.push(c);
        assertSame(b, history.undo().orElseThrow());
        assertSame(a, history.undo().orElseThrow());
        assertEquals(Optional.empty(), history.undo());
        assertSame(b, history.redo().orElseThrow());
        assertTrue(history.canRedo());
        // A new edit drops what could be redone.
        history.push(image(4));
        assertFalse(history.canRedo());
        assertEquals(4, history.current().orElseThrow().width());
    }

    @Test
    void keepsOnlyTheLastSteps() {
        ImageHistory history = new ImageHistory(2);
        history.reset(image(1));
        for (int i = 2; i <= 5; i++) history.push(image(i));
        assertEquals(4, history.undo().orElseThrow().width());
        assertEquals(3, history.undo().orElseThrow().width());
        assertFalse(history.canUndo());
        history.clear();
        assertEquals(Optional.empty(), history.current());
    }
}
