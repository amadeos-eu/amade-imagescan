package eu.amadeos.apps.imagescan.ui.view;

import org.junit.jupiter.api.Test;

import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ViewTest {
    // ---------------------------------------------------------------- Viewport

    private static Viewport viewport() {
        Viewport v = new Viewport();
        v.setPanelSize(800, 600);
        v.setImageSize(2000, 3000);
        return v;
    }

    @Test
    void fitsAndCentres() {
        Viewport v = viewport();
        assertTrue(v.isFit());
        assertEquals(0.2, v.scale(), 1e-9);
        assertEquals(20, v.zoomPercent());
        Rectangle2D shown = v.displayedImage();
        assertEquals(200, shown.getX(), 1e-9);
        assertEquals(0, shown.getY(), 1e-9);
        assertEquals(400, shown.getWidth(), 1e-9);
        // Still fitted after the window is resized.
        v.setPanelSize(1600, 1200);
        assertEquals(0.4, v.scale(), 1e-9);
    }

    @Test
    void zoomKeepsThePointUnderThePointer() {
        Viewport v = viewport();
        Point2D before = v.toImage(300, 100);
        v.zoomAt(4, 300, 100);
        assertFalse(v.isFit());
        assertEquals(0.8, v.scale(), 1e-9);
        Point2D after = v.toImage(300, 100);
        assertEquals(before.getX(), after.getX(), 1e-6);
        assertEquals(before.getY(), after.getY(), 1e-6);
        v.fit();
        assertEquals(0.2, v.scale(), 1e-9);
    }

    @Test
    void zoomIsLimitedAndActualSizeIsOneToOne() {
        Viewport v = viewport();
        for (int i = 0; i < 100; i++) v.zoomIn();
        assertEquals(Viewport.MAX_SCALE, v.scale(), 1e-9);
        v.actualSize();
        assertEquals(100, v.zoomPercent());
        for (int i = 0; i < 100; i++) v.zoomOut();
        assertEquals(Viewport.MIN_SCALE, v.scale(), 1e-9);
    }

    @Test
    void panningStopsAtTheEdges() {
        Viewport v = viewport();
        v.actualSize();
        v.panBy(100000, 100000);
        assertEquals(0, v.originX(), 1e-9);
        assertEquals(0, v.originY(), 1e-9);
        v.panBy(-100000, -100000);
        assertEquals(800 - 2000, v.originX(), 1e-9);
        assertEquals(600 - 3000, v.originY(), 1e-9);
        Rectangle2D r = v.toPanel(new Rectangle2D.Double(1500, 2500, 100, 100));
        assertEquals(300, r.getX(), 1e-9);
        assertEquals(100, r.getWidth(), 1e-9);
    }

    // ---------------------------------------------------------------- CropSelection

    private static CropSelection crop() {
        CropSelection c = new CropSelection();
        c.setImageSize(100, 80);
        return c;
    }

    @Test
    void drawsAFrameInsideTheImage() {
        CropSelection c = crop();
        c.press(10, 10, 2);
        c.drag(150, -20);
        c.release(1);
        assertEquals(Optional.of(new Rectangle(10, 0, 90, 10)), c.selection());
    }

    @Test
    void aClickRemovesTheFrame() {
        CropSelection c = crop();
        c.set(new Rectangle(10, 10, 20, 20));
        c.press(60, 60, 2);
        c.drag(60.5, 60.5);
        c.release(2);
        assertEquals(Optional.empty(), c.selection());
    }

    @Test
    void movesWithoutLeavingTheImage() {
        CropSelection c = crop();
        c.set(new Rectangle(10, 10, 20, 20));
        assertEquals(CropSelection.Hit.INSIDE, c.hit(20, 20, 2));
        c.press(20, 20, 2);
        c.drag(25, 22);
        assertEquals(Optional.of(new Rectangle(15, 12, 20, 20)), c.selection());
        c.drag(500, 500);
        c.release(1);
        assertEquals(Optional.of(new Rectangle(80, 60, 20, 20)), c.selection());
    }

    @Test
    void resizesAtAHandleAndTurnsOverWhenDraggedPastTheOtherSide() {
        CropSelection c = crop();
        c.set(new Rectangle(10, 10, 20, 20));
        assertEquals(Optional.of(CropSelection.Handle.SE), c.handleAt(31, 29, 2));
        assertEquals(CropSelection.Hit.HANDLE, c.hit(10, 20, 2));
        c.press(30, 30, 2);
        c.drag(40, 35);
        assertEquals(Optional.of(new Rectangle(10, 10, 30, 25)), c.selection());
        c.drag(0, 30);
        c.release(1);
        assertEquals(Optional.of(new Rectangle(0, 10, 10, 20)), c.selection());
    }

    @Test
    void edgeHandleChangesOneSide() {
        CropSelection c = crop();
        c.set(new Rectangle(10, 10, 20, 20));
        c.press(20, 10, 1);
        c.drag(20, -50);
        c.release(1);
        assertEquals(Optional.of(new Rectangle(10, 0, 20, 30)), c.selection());
    }

    @Test
    void selectAllAndNewImage() {
        CropSelection c = crop();
        c.selectAll();
        assertEquals(Optional.of(new Rectangle(0, 0, 100, 80)), c.selection());
        assertEquals(8, c.handles().size());
        c.setImageSize(10, 10);
        assertEquals(Optional.empty(), c.selection());
        c.set(new Rectangle(5, 5, 50, 50));
        assertEquals(Optional.of(new Rectangle(5, 5, 5, 5)), c.selection());
    }
}
