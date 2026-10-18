package eu.amadeos.apps.imagescan.service.scan.sane;

import eu.amadeos.apps.imagescan.domain.ColorMode;
import eu.amadeos.apps.imagescan.domain.Scanner;
import eu.amadeos.apps.imagescan.domain.ScannerOptions;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads what {@code scanimage} prints: the device list ({@code -f} or {@code -L}) and the options of a device
 * ({@code -A}). The option list differs between SANE backends, so the parser looks for the few options every
 * flatbed has (mode, resolution, the geometry) and maps the backend's mode names to {@link ColorMode}.
 */
public final class SaneOptionParser {
    /** Field separator of the device list format SaneBackend asks for. */
    static final String SEPARATOR = "\t";

    private static final Pattern LIST_LINE = Pattern.compile("device `([^']+)' is a (.*)");
    private static final Pattern OPTION = Pattern.compile("^\\s*(--?[A-Za-z][\\w-]*)\\s+(.*)$");
    private static final Pattern RANGE = Pattern.compile("(-?\\d+(?:\\.\\d+)?)\\.\\.(-?\\d+(?:\\.\\d+)?)");
    private static final Pattern NUMBER = Pattern.compile("\\d+(?:\\.\\d+)?");

    private SaneOptionParser() {
    }

    /** Lines "device\tvendor\tmodel\ttype" as {@code scanimage -f} prints them with our format. */
    public static List<Scanner> parseDeviceList(String output) {
        List<Scanner> scanners = new ArrayList<>();
        for (String line : output.split("\\R")) {
            if (line.isBlank()) continue;
            String[] fields = line.split(SEPARATOR, -1);
            if (fields.length >= 4 && !fields[0].isBlank()) {
                scanners.add(new Scanner(fields[0].strip(), fields[1], fields[2], fields[3]));
            }
        }
        return scanners;
    }

    /**
     * The classic {@code scanimage -L} lines, "device `epson2:libusb:001:004' is a Epson PID 0x013d flatbed
     * scanner", for scanimage versions that do not understand {@code -f}.
     */
    public static List<Scanner> parseLegacyList(String output) {
        List<Scanner> scanners = new ArrayList<>();
        for (String line : output.split("\\R")) {
            Matcher m = LIST_LINE.matcher(line.strip());
            if (!m.matches()) continue;
            String description = m.group(2).strip();
            String type = "";
            for (String kind : List.of("flatbed scanner", "sheetfed scanner", "film scanner", "scanner",
                    "camera", "video camera", "virtual device")) {
                if (description.toLowerCase(Locale.ROOT).endsWith(kind)) {
                    type = kind;
                    description = description.substring(0, description.length() - kind.length()).strip();
                    break;
                }
            }
            int space = description.indexOf(' ');
            String vendor = space < 0 ? description : description.substring(0, space);
            String model = space < 0 ? "" : description.substring(space + 1);
            scanners.add(new Scanner(m.group(1), vendor, model, type));
        }
        return scanners;
    }

    /** The options {@code scanimage -d <device> -A} lists. */
    public static ScannerOptions parseOptions(String output) {
        List<Integer> resolutions = new ArrayList<>();
        Map<ColorMode, String> modes = new EnumMap<>(ColorMode.class);
        double width = 0;
        double height = 0;
        double left = 0;
        double top = 0;
        for (String line : output.split("\\R")) {
            Matcher m = OPTION.matcher(line);
            if (!m.matches()) continue;
            String name = m.group(1);
            String value = m.group(2).strip();
            if (value.contains("[inactive]")) continue;
            String values = value.contains("[") ? value.substring(0, value.indexOf('[')).strip() : value;
            switch (name) {
                case "--mode", "--scan-mode" -> {
                    if (modes.isEmpty()) modes.putAll(mapModes(values));
                }
                case "--resolution" -> {
                    if (resolutions.isEmpty()) resolutions.addAll(resolutions(values));
                }
                case "-x" -> width = maxMillimetres(values);
                case "-y" -> height = maxMillimetres(values);
                case "-l" -> left = maxMillimetres(values);
                case "-t" -> top = maxMillimetres(values);
                default -> {
                    // not needed
                }
            }
        }
        if (resolutions.isEmpty()) resolutions.addAll(ScannerOptions.FALLBACK.resolutions());
        if (modes.isEmpty()) modes.put(ColorMode.COLOR, "Color");
        // The glass is the larger of the two ranges SANE gives (origin and extent).
        return new ScannerOptions(resolutions, modes, Math.max(width, left), Math.max(height, top));
    }

    /** Maps "Lineart|Gray|Color" (or "Binary|True Gray|24bit Color", ...) to our modes. */
    static Map<ColorMode, String> mapModes(String values) {
        Map<ColorMode, String> modes = new EnumMap<>(ColorMode.class);
        String[] names = values.split("\\|");
        // Exact names first, so "Color" wins over "Color Lineart" and "Gray" over "Halftone Gray".
        for (String raw : names) {
            String name = raw.strip();
            switch (name.toLowerCase(Locale.ROOT)) {
                case "color", "colour" -> modes.putIfAbsent(ColorMode.COLOR, name);
                case "gray", "grey", "grayscale", "greyscale" -> modes.putIfAbsent(ColorMode.GRAY, name);
                case "lineart", "binary", "black & white", "black and white" ->
                        modes.putIfAbsent(ColorMode.LINEART, name);
                default -> {
                }
            }
        }
        for (String raw : names) {
            String name = raw.strip();
            String lower = name.toLowerCase(Locale.ROOT);
            if (lower.contains("halftone") || lower.contains("dither")) continue;
            if (lower.contains("color") || lower.contains("colour")) modes.putIfAbsent(ColorMode.COLOR, name);
            else if (lower.contains("gray") || lower.contains("grey")) modes.putIfAbsent(ColorMode.GRAY, name);
            else if (lower.contains("lineart") || lower.contains("binary") || lower.contains("mono"))
                modes.putIfAbsent(ColorMode.LINEART, name);
        }
        return modes;
    }

    /** "75|150|300dpi" or "50..2400dpi (in steps of 1)". */
    static List<Integer> resolutions(String values) {
        Matcher range = RANGE.matcher(values);
        if (range.find()) {
            double min = Double.parseDouble(range.group(1));
            double max = Double.parseDouble(range.group(2));
            List<Integer> steps = new ArrayList<>(ScannerOptions.COMMON_RESOLUTIONS.stream()
                    .filter(r -> r >= min && r <= max).toList());
            if (steps.isEmpty()) {
                steps.add((int) Math.round(min));
                if (max > min) steps.add((int) Math.round(max));
            }
            return steps;
        }
        List<Integer> list = new ArrayList<>();
        for (String part : values.split("\\|")) {
            Matcher n = NUMBER.matcher(part);
            if (n.find()) {
                int dpi = (int) Math.round(Double.parseDouble(n.group()));
                if (dpi > 0) list.add(dpi);
            }
        }
        return list;
    }

    /** The upper end of a geometry range in millimetres; 0 if the backend measures in something else. */
    static double maxMillimetres(String values) {
        if (!values.contains("mm")) return 0;
        Matcher range = RANGE.matcher(values);
        return range.find() ? Double.parseDouble(range.group(2)) : 0;
    }
}
