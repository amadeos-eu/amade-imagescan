package org.datazuul.amadeos.imagescan.service.scan.wia;

import org.datazuul.amadeos.imagescan.FakeProcess;
import org.datazuul.amadeos.imagescan.domain.ColorMode;
import org.datazuul.amadeos.imagescan.domain.ScanArea;
import org.datazuul.amadeos.imagescan.domain.ScanSettings;
import org.datazuul.amadeos.imagescan.domain.Scanner;
import org.datazuul.amadeos.imagescan.domain.ScannerOptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WiaBackendTest {
    private static final Scanner CANON = new Scanner("{6BDD1FC6-810F-11D0-BEC7-08002BE2092F}\\0001", "", "Canon LiDE 300", "");

    @Test
    void listsWiaScannersAndDropsARepeatedMaker() throws Exception {
        List<List<String>> commands = new ArrayList<>();
        WiaBackend backend = new WiaBackend(command -> {
            commands.add(command);
            return new FakeProcess("{ID}\\0001\tCanon\tCanon LiDE 300\r\n{ID}\\0002\tHewlett-Packard\tScanjet G3110\r\n", "", 0);
        });
        List<Scanner> scanners = backend.listScanners();
        assertEquals(2, scanners.size());
        assertEquals("Canon LiDE 300", scanners.get(0).displayName());
        assertEquals("Hewlett-Packard Scanjet G3110", scanners.get(1).displayName());
        List<String> command = commands.getFirst();
        // Plain text, no -EncodedCommand (which Windows Defender treats as malware), no double quotes.
        assertEquals(List.of("powershell.exe", "-NoProfile", "-NonInteractive", "-Command"), command.subList(0, 4));
        assertEquals(5, command.size());
        assertTrue(command.getLast().contains("WIA.DeviceManager"));
        assertFalse(command.getLast().contains("\""));
        assertFalse(command.getLast().contains("\n"));
    }

    @Test
    void scriptQuotesIdsAndPathsAndSetsIntentFirst() throws Exception {
        ScannerOptions options = new WiaBackend().options(CANON);
        ScanSettings settings = new ScanSettings(300, ColorMode.GRAY, Optional.of(new ScanArea(25.4, 50.8, 101.6, 25.4)));
        String script = WiaBackend.scanScript(CANON, settings, options, Path.of("/tmp/O'Brien/scan.png"));
        assertTrue(script.contains("-eq '{6BDD1FC6-810F-11D0-BEC7-08002BE2092F}\\0001'"), script);
        // Single quotes are doubled, backslashes stay as they are.
        assertTrue(script.contains("SaveFile('" + Path.of("/tmp/O''Brien/scan.png").toAbsolutePath() + "')"), script);
        assertTrue(script.indexOf("Set-Prop 6146 2") < script.indexOf("Set-Prop 6147 300"), script);
        assertTrue(script.contains("Set-Prop 6149 300"), script);
        assertTrue(script.contains("Set-Prop 6150 600"), script);
        assertTrue(script.contains("Set-Prop 6151 1200"), script);
        assertTrue(script.contains("Set-Prop 6152 300"), script);
    }

    @Test
    void refusesWhatWouldBreakTheCommandLine() {
        Scanner odd = new Scanner("a\"b", "", "x", "");
        assertThrows(java.io.IOException.class, () -> WiaBackend.scanScript(odd, ScanSettings.DEFAULT,
                new WiaBackend().options(odd), Path.of("scan.png")));
    }

    @Test
    void quoteDoublesSingleQuotes() {
        assertEquals("'it''s'", WiaBackend.quote("it's"));
        assertEquals("'$env:x'", WiaBackend.quote("$env:x"));
    }
}
