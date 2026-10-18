package eu.amadeos.imagescan.service.scan.wia;

import eu.amadeos.imagescan.service.scan.BackendMissingException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** The real WIA over COM; runs on Windows only (the CI Windows job), usually with no scanner attached. */
class ComWiaApiTest {
    @Test
    void asksWiaForItsDevicesWithoutAnyExternalProgram() throws Exception {
        assumeTrue(System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows"), "Windows only");
        List<WiaApi.Device> devices;
        try {
            devices = new ComWiaApi().devices();
        } catch (BackendMissingException e) {
            assumeTrue(false, "WIA is not installed on this Windows");
            return;
        }
        for (WiaApi.Device d : devices) assertTrue(!d.id().isBlank(), d.toString());
    }
}
