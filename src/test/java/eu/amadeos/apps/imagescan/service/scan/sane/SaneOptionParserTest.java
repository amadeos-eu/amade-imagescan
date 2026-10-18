package eu.amadeos.apps.imagescan.service.scan.sane;

import eu.amadeos.apps.imagescan.domain.ColorMode;
import eu.amadeos.apps.imagescan.domain.Scanner;
import eu.amadeos.apps.imagescan.domain.ScannerOptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SaneOptionParserTest {
    /** Abridged {@code scanimage -A} of an Epson Perfection with the epson2 backend. */
    static final String EPSON = """
            All options specific to device `epson2:libusb:001:004':
              Scan Mode:
                --mode Lineart|Gray|Color [Lineart]
                    Selects the scan mode (e.g., lineart, monochrome, or color).
                --depth 8|16 [inactive]
                    Number of bits per sample, typical values are 1 for "line-art" and 8
                --halftoning None|Halftone A (Hard Tone)|Halftone B (Soft Tone) [None]
                --resolution 75|300|600|1200dpi [75]
                    Sets the resolution of the scanned image.
              Geometry:
                -l 0..215.9mm [0]
                    Top-left x position of scan area.
                -t 0..297.18mm [0]
                    Top-left y position of scan area.
                -x 0..215.9mm [215.9]
                    Width of scan-area.
                -y 0..297.18mm [297.18]
                    Height of scan-area.
            """;

    /** sane-airscan (eSCL network scanner), with a range of resolutions. */
    static final String AIRSCAN = """
            All options specific to device `airscan:e0:HP LaserJet MFP':
              Standard:
                --resolution 75..1200dpi (in steps of 1) [300]
                --mode Color|Gray [Color]
                --source Flatbed|ADF [Flatbed]
              Geometry:
                -l 0..215.9mm (in steps of 0.0211) [0]
                -t 0..297.011mm (in steps of 0.0211) [0]
                -x 0..215.9mm (in steps of 0.0211) [215.9]
                -y 0..297.011mm (in steps of 0.0211) [297.011]
            """;

    @Test
    void readsTheEpson() {
        ScannerOptions o = SaneOptionParser.parseOptions(EPSON);
        assertEquals(List.of(75, 300, 600, 1200), o.resolutions());
        assertEquals(Map.of(ColorMode.LINEART, "Lineart", ColorMode.GRAY, "Gray", ColorMode.COLOR, "Color"), o.modes());
        assertEquals(215.9, o.maxWidth(), 1e-9);
        assertEquals(297.18, o.maxHeight(), 1e-9);
        assertTrue(o.hasGlassSize());
    }

    @Test
    void offersCommonStepsOfARange() {
        ScannerOptions o = SaneOptionParser.parseOptions(AIRSCAN);
        assertEquals(List.of(75, 100, 150, 200, 300, 400, 600, 1200), o.resolutions());
        assertEquals(List.of(ColorMode.COLOR, ColorMode.GRAY), o.colorModes());
        assertEquals(297.011, o.maxHeight(), 1e-9);
    }

    @Test
    void mapsTheModeNamesOfOtherBackends() {
        assertEquals(Map.of(ColorMode.LINEART, "Binary", ColorMode.GRAY, "True Gray", ColorMode.COLOR, "24bit Color"),
                SaneOptionParser.mapModes("Binary|True Gray|24bit Color"));
        // The plain names win over variants; halftone is not line art.
        assertEquals(Map.of(ColorMode.COLOR, "Color", ColorMode.GRAY, "Gray", ColorMode.LINEART, "Lineart"),
                SaneOptionParser.mapModes("Color Lineart|Halftone|Gray|Color|Lineart"));
        assertEquals(Map.of(ColorMode.LINEART, "Black & White", ColorMode.GRAY, "Grayscale"),
                SaneOptionParser.mapModes("Black & White|Grayscale"));
    }

    @Test
    void geometryInPixelsIsIgnored() {
        ScannerOptions o = SaneOptionParser.parseOptions("""
                    --resolution 300dpi [300]
                    -x 0..2550 [2550]
                    -y 0..3508 [3508]
                """);
        assertEquals(List.of(300), o.resolutions());
        assertEquals(0, o.maxWidth(), 0);
        assertTrue(!o.hasGlassSize());
        // No mode option: colour, as SANE scans by default.
        assertEquals(List.of(ColorMode.COLOR), o.colorModes());
    }

    @Test
    void readsTheDeviceListOfOurFormat() {
        String out = "epson2:libusb:001:004\tEpson\tPerfection V39\tflatbed scanner\n"
                + "airscan:e0:HP LaserJet MFP\tHP\tLaserJet MFP M28w\teSCL network scanner\n"
                + "\n";
        List<Scanner> list = SaneOptionParser.parseDeviceList(out);
        assertEquals(2, list.size());
        assertEquals("airscan:e0:HP LaserJet MFP", list.get(1).id());
        assertEquals("HP LaserJet MFP M28w", list.get(1).displayName());
    }

    @Test
    void readsTheClassicList() {
        String out = """
                device `epson2:libusb:001:004' is a Epson PID 0x013d flatbed scanner
                device `v4l:/dev/video0' is a Noname Integrated Camera virtual device
                No scanners were identified.
                """;
        List<Scanner> list = SaneOptionParser.parseLegacyList(out);
        assertEquals(2, list.size());
        assertEquals(new Scanner("epson2:libusb:001:004", "Epson", "PID 0x013d", "flatbed scanner"), list.getFirst());
        assertEquals("virtual device", list.get(1).type());
    }
}
