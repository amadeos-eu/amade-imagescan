package org.datazuul.amadeos.imagescan.service.scan.wia;

import com.google.inject.Inject;
import org.datazuul.amadeos.imagescan.domain.ScanArea;
import org.datazuul.amadeos.imagescan.domain.ScanSettings;
import org.datazuul.amadeos.imagescan.domain.Scanner;
import org.datazuul.amadeos.imagescan.domain.ScannerOptions;
import org.datazuul.amadeos.imagescan.service.scan.CommandRunner;
import org.datazuul.amadeos.imagescan.service.scan.ProcessOutput;
import org.datazuul.amadeos.imagescan.service.scan.ScanMonitor;
import org.datazuul.amadeos.imagescan.service.scan.ScannerBackend;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CancellationException;

/**
 * Scans on Windows through Windows Image Acquisition, driven by PowerShell. WIA ships with every Windows and
 * bridges TWAIN drivers itself. Scripts go to PowerShell as {@code -EncodedCommand}, so device ids and paths
 * need no quoting on the command line; inside the script they are single-quoted PowerShell strings.
 */
public class WiaBackend implements ScannerBackend {
    public static final String PROGRAM = "powershell.exe";
    private static final String FORMAT_PNG = "{B96B3CAF-0728-11D3-9D7B-0000F81EF32E}";
    /** WIA item properties. */
    private static final int CURRENT_INTENT = 6146;
    private static final int X_RESOLUTION = 6147;
    private static final int Y_RESOLUTION = 6148;
    private static final int X_POSITION = 6149;
    private static final int Y_POSITION = 6150;
    private static final int X_EXTENT = 6151;
    private static final int Y_EXTENT = 6152;
    /** Scan time on a slow flatbed at high resolution. */
    private static final long SCAN_TIMEOUT_SECONDS = 15 * 60;

    private final CommandRunner runner;

    @Inject
    public WiaBackend() {
        this(CommandRunner.system());
    }

    public WiaBackend(CommandRunner runner) {
        this.runner = runner;
    }

    @Override
    public String name() {
        return "WIA";
    }

    @Override
    public List<Scanner> listScanners() throws IOException, InterruptedException {
        String script = String.join("\n",
                "$ErrorActionPreference = 'Stop'",
                "$wia = New-Object -ComObject WIA.DeviceManager",
                "foreach ($d in $wia.DeviceInfos) {",
                "  if ($d.Type -eq 1) {",
                "    $m = ''; try { $m = $d.Properties['Manufacturer'].Value } catch {}",
                "    Write-Output (\"{0}`t{1}`t{2}\" -f $d.DeviceID, $m, $d.Properties['Name'].Value)",
                "  }",
                "}");
        ProcessOutput.Result result = ProcessOutput.run(runner, command(script), 60, StandardCharsets.UTF_8);
        if (!result.succeeded()) throw new IOException("WIA: " + result.stderr().strip());
        return parseList(result.stdout());
    }

    static List<Scanner> parseList(String output) {
        List<Scanner> scanners = new ArrayList<>();
        for (String line : output.split("\\R")) {
            String[] f = line.split("\t", -1);
            if (f.length >= 3 && !f[0].isBlank()) {
                String model = f[2].strip();
                String vendor = f[1].strip();
                // WIA names often start with the maker already.
                if (!vendor.isEmpty() && model.toLowerCase(Locale.ROOT).startsWith(vendor.toLowerCase(Locale.ROOT))) {
                    vendor = "";
                }
                scanners.add(new Scanner(f[0].strip(), vendor, model, "flatbed scanner"));
            }
        }
        return scanners;
    }

    /** WIA through PowerShell cannot list the values a scanner allows; the usual ones work on nearly all. */
    @Override
    public ScannerOptions options(Scanner scanner) {
        return new ScannerOptions(List.of(75, 100, 150, 200, 300, 600, 1200), ScannerOptions.FALLBACK.modes(),
                ScannerOptions.FALLBACK.maxWidth(), ScannerOptions.FALLBACK.maxHeight());
    }

    /** The PowerShell script for a scan; package access for tests. */
    static String scanScript(Scanner scanner, ScanSettings settings, ScannerOptions options, Path output) {
        int dpi = options.nearestResolution(settings.resolution());
        int intent = switch (settings.mode()) {
            case COLOR -> 1;
            case GRAY -> 2;
            case LINEART -> 4;
        };
        List<String> lines = new ArrayList<>(List.of(
                "$ErrorActionPreference = 'Stop'",
                "$wia = New-Object -ComObject WIA.DeviceManager",
                "$info = $null",
                "foreach ($d in $wia.DeviceInfos) { if ($d.DeviceID -eq " + quote(scanner.id()) + ") { $info = $d } }",
                "if ($info -eq $null) { throw 'Scanner not found' }",
                "$device = $info.Connect()",
                "$item = $device.Items.Item(1)",
                "function Set-Prop($id, $value) { foreach ($p in $item.Properties) { if ($p.PropertyID -eq $id) { $p.Value = $value } } }",
                // The intent first: it resets resolution and area to the defaults of that kind of picture.
                "Set-Prop " + CURRENT_INTENT + " " + intent,
                "Set-Prop " + X_RESOLUTION + " " + dpi,
                "Set-Prop " + Y_RESOLUTION + " " + dpi));
        settings.area().ifPresent(area -> {
            ScanArea a = area.clampTo(options.maxWidth(), options.maxHeight());
            double px = dpi / ScanArea.MM_PER_INCH;
            lines.add("Set-Prop " + X_POSITION + " " + Math.round(a.left() * px));
            lines.add("Set-Prop " + Y_POSITION + " " + Math.round(a.top() * px));
            lines.add("Set-Prop " + X_EXTENT + " " + Math.round(a.width() * px));
            lines.add("Set-Prop " + Y_EXTENT + " " + Math.round(a.height() * px));
        });
        lines.add("$image = $item.Transfer('" + FORMAT_PNG + "')");
        lines.add("$image.SaveFile(" + quote(output.toAbsolutePath().toString()) + ")");
        return String.join("\n", lines);
    }

    @Override
    public BufferedImage scan(Scanner scanner, ScanSettings settings, ScannerOptions options, ScanMonitor monitor)
            throws IOException, InterruptedException {
        monitor.checkCancelled();
        Path dir = Files.createTempDirectory("amade-imagescan");
        // WIA's SaveFile refuses to replace a file, so the name must not exist yet.
        Path output = dir.resolve("scan.png");
        try {
            monitor.progress(ScanMonitor.UNKNOWN);
            ProcessOutput.Result result = ProcessOutput.run(runner,
                    command(scanScript(scanner, settings, options, output)), SCAN_TIMEOUT_SECONDS,
                    StandardCharsets.UTF_8, process -> monitor.onCancel(process::destroy));
            if (monitor.isCancelled()) throw new CancellationException("Scan cancelled");
            if (!result.succeeded() || !Files.exists(output)) {
                throw new IOException("WIA: " + (result.stderr() + result.stdout()).strip());
            }
            BufferedImage image = ImageIO.read(output.toFile());
            if (image == null) throw new IOException("WIA delivered no readable image");
            monitor.progress(1);
            return image;
        } finally {
            Files.deleteIfExists(output);
            Files.deleteIfExists(dir);
        }
    }

    private static List<String> command(String script) {
        // -EncodedCommand takes UTF-16LE in Base64.
        String encoded = Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
        return List.of(PROGRAM, "-NoProfile", "-NonInteractive", "-EncodedCommand", encoded);
    }

    /** A single-quoted PowerShell string: nothing inside is expanded, a quote is doubled. */
    static String quote(String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    /** Decodes a command line built by this class; for tests. */
    static String decodeScript(List<String> command) {
        return new String(Base64.getDecoder().decode(command.getLast()), StandardCharsets.UTF_16LE);
    }
}
