package eu.amadeos.apps.imagescan.bootstrap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImagescanModuleTest {
    @Test
    void choosesTheBackendByOperatingSystem() {
        assertTrue(ImagescanModule.isWindows("Windows 11"));
        assertFalse(ImagescanModule.isWindows("Linux"));
        assertFalse(ImagescanModule.isWindows("Mac OS X"));
    }

    @Test
    void appInfo() {
        assertEquals("imagescan", AmadeImagescanApplication.APP.configName());
        assertEquals("amade-imagescan", AmadeImagescanApplication.APP.id());
        assertEquals("dev", AmadeImagescanApplication.version());
    }
}
