package eu.amadeos.imagescan.domain;

import java.util.Objects;

/**
 * A scanner the backend found.
 *
 * @param id     device name the backend addresses it by: a SANE device such as {@code epson2:libusb:001:004},
 *               or a WIA device id on Windows
 * @param vendor maker, e.g. {@code Epson}; may be empty
 * @param model  model, e.g. {@code Perfection V39}; may be empty
 * @param type   kind of device as the backend calls it, e.g. {@code flatbed scanner}; may be empty
 */
public record Scanner(String id, String vendor, String model, String type) {
    public Scanner {
        Objects.requireNonNull(id, "id");
        vendor = vendor == null ? "" : vendor.strip();
        model = model == null ? "" : model.strip();
        type = type == null ? "" : type.strip();
        if (id.isBlank()) throw new IllegalArgumentException("Scanner id must not be blank");
    }

    /** "Epson Perfection V39", or the device name if the backend knows no name. */
    public String displayName() {
        String name = (vendor + " " + model).strip();
        return name.isEmpty() ? id : name;
    }

    /** Whether the backend reports a camera or video device rather than a scanner. */
    public boolean isCamera() {
        String t = type.toLowerCase(java.util.Locale.ROOT);
        return t.contains("camera") || t.contains("video");
    }

    @Override
    public String toString() {
        return displayName();
    }
}
