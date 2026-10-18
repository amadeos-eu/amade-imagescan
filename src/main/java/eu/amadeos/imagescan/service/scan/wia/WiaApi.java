package eu.amadeos.imagescan.service.scan.wia;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * The few calls of the WIA automation library ({@code WIA.DeviceManager}) the backend needs. {@link ComWiaApi}
 * makes them over COM inside this process; tests use a fake.
 */
public interface WiaApi {
    /** A WIA device of type scanner. */
    record Device(String id, String manufacturer, String name) {
    }

    /**
     * The scanners WIA knows.
     *
     * @throws eu.amadeos.imagescan.service.scan.BackendMissingException if WIA is not available
     */
    List<Device> devices() throws IOException;

    /**
     * Sets the item properties in the given order, transfers one image and saves it as PNG.
     *
     * @param properties WIA property id → value, applied in iteration order
     * @param output     a file that does not exist yet (WIA refuses to replace one)
     */
    void scan(String deviceId, Map<Integer, Integer> properties, Path output) throws IOException;
}
