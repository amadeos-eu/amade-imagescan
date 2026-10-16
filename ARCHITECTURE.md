# amade-imagescan – Architecture

Scanning program for flatbed scanners on amadeOS. Java 21, Swing, Guice, [amade-commons](https://github.com/datazuul/amade-commons). Gradle, EUPL-1.2.

Packaging:
- Debian package: depends on `sane-utils`, recommends `sane-airscan`.
- Windows: MSI and portable app image built with jpackage.

Languages: English, German, French, Spanish and Italian. The language follows the amadeOS desktop.

## Key decisions

- **SANE through `scanimage`, not a native binding.** On Linux the app needs only a program, no JNI or JNA library:
  - `scanimage -f` lists the devices.
  - `scanimage -d <device> -A` lists a scanner's options.
  - `scanimage --format=pnm --progress` scans to standard output and reports progress on standard error.
- **Own PNM decoder.** The JDK's ImageIO cannot read PNM. `PnmReader` decodes the image while it arrives on standard output, so there are no temporary files.
- **WIA over COM inside the process on Windows.** WIA's automation objects (`WIA.DeviceManager`, `DeviceInfo`, `Item`, `ImageFile`) are called with JNA. No PowerShell or other program is started: Windows Defender blocks scripts started by another program as typical malware behaviour.
- **Workflow of XSane and the Windows scanning dialog:**
  1. A preview of the whole glass at 75 dpi.
  2. A frame drawn around the part wanted.
  3. A scan of only that part at the chosen resolution.

  A frame on a finished scan means cropping instead.
- **The scanner decides what is offered.**
  - Modes and resolutions come from `-A`.
  - Backend names such as "True Gray" or "Binary" are mapped to `ColorMode`.
  - A range such as `75..1200dpi` is reduced to common steps.
  - WIA cannot report its values, so the usual ones are offered.
- **Edits keep every pixel and the raster type.** Line art stays 1 bit, grey stays grey. The resolution travels with the image (`ScannedImage`), so that saving and printing keep the true size.
- **Testable without a display.** The presenter, the viewport and the crop frame have no Swing code. Backends get their processes (`CommandRunner`) or their COM calls (`WiaApi`) injected, so tests use fakes.

## Packages

| Package | Contents |
|---|---|
| `bootstrap` | `AmadeImagescanApplication` (start through `AmadeApplication`), `ImagescanModule` (Guice; picks `SaneBackend` or `WiaBackend` by operating system) |
| `domain` | `Scanner`, `ScanSettings` (dpi, `ColorMode`, optional `ScanArea` in mm), `ScannerOptions` (resolutions, mode names, glass size), `ScannedImage` (pixels + dpi), `ImageFormat` (PNG, JPEG, TIFF) |
| `service.scan` | `ScannerBackend` (list, options, scan), `ScanService` (runs the backend in the background, caches options), `ScanMonitor` (progress, cancel), `CommandRunner` and `ProcessOutput` (external programs, standard output and error read at the same time), `BackendMissingException` |
| `service.scan.sane` | `SaneBackend`, `SaneOptionParser` (device list in our `-f` format or the classic `-L` lines, option list), `PnmReader` (P1–P6, 16-bit samples, line art as 1 bit) |
| `service.scan.wia` | `WiaBackend` (settings → WIA item properties: intent first, then resolution and area), `WiaApi` (the calls needed), `ComWiaApi` (COM with JNA on one platform thread of its own; arguments as `VARIANT` by reference as WIA's type library declares them) |
| `service.image` | `ImageOperations` (crop, quarter turns, mirroring on the raster), `ImageCodec` (resolution in PNG `pHYs`, JFIF density and TIFF tags, read back from ImageIO's neutral metadata), `ImageHistory` (undo/redo, last 8 steps), `ImageService` (open and save in the background) |
| `service.print` | `ImagePrintable` (true size, centred, shrunk to fit if needed), `PrintService` |
| `repository` | `ImageFiles` (atomic writes that keep permissions), `ImagescanPreferences` (`~/.config/amadeos/imagescan/imagescan.properties`: scanner, dpi, mode, file type, JPEG quality, visible bars) |
| `ui.main` | `MainPresenter` (all commands, no Swing), `MainView`, `MainWindow`, `MainMenuBar`, `MainToolBar`, `ScanSettingsPanel` (scanner, mode, resolution, area and expected size, Preview and Scan), `ScanActions` (one action per command for menus and toolbar), `ImageKind` (preview, scan, file) |
| `ui.view` | `ImageView` (zoom at the pointer, panning with the right or middle button, frame with eight handles and a size label), `Viewport` (zoom and scroll position), `CropSelection` (the frame in image coordinates) |
| `ui.icons` | `ToolIcon` (toolbar icons drawn in Java2D in the theme's colours) |

## Flows

- **Start**
  1. The window opens.
  2. The presenter asks the backend for scanners in the background.
  3. The scanner used last is selected, else the first one; cameras that SANE also offers go last.
  4. The scanner's options fill mode and resolution.
- **Preview and scan**
  1. `MainPresenter.preview()` scans the whole glass at the resolution nearest to 75 dpi.
  2. The frame in the preview is converted to millimetres (`ScanArea.fromPixels`) and cut down to the glass.
  3. `scan()` passes it to the backend. SANE gets it as `-l/-t/-x/-y` in mm; a backend without millimetre geometry scans the whole glass, and the area is cut out afterwards. WIA gets it as position and extent in pixels.
  4. Scans that would need more than 512 MB of memory are confirmed first.
- **Threads**
  - Scans, file access and edits run on the virtual-thread executor from amade-commons and continue on the event dispatch thread (`@Ui` executor).
  - COM calls run on their own platform thread.
  - Progress from `scanimage` arrives on a reader thread and is forwarded to the status bar.
- **Cancel**
  - SANE: the `scanimage` process is stopped.
  - WIA: the transfer cannot be interrupted, so its result is dropped and the scanner finishes on its own.
  - In both cases the previous image stays.
- **Unsaved scans:** a new preview, scan, open or exit asks whether to save first. A preview is never asked about.
- **Theme and language:** colours come from `AmadeUI.style(...)` and follow the Style Manager immediately. Texts are in `messages*.properties`, checked by `TranslationCheck`, including a test against clashing mnemonics.

## Dependencies

- Guice, amade-commons, SLF4J/Logback.
- JNA (`jna-platform`, Apache-2.0/LGPL), used on Windows only.
- Image formats and printing come from the JDK (ImageIO, `java.awt.print`).
- At runtime on Linux: `sane-utils`, and `sane-airscan` for network scanners.

## Planned

- Several pages into one PDF (sheet feeder, or one page after another)
- Brightness, contrast and automatic straightening
- Scanner buttons through `scanbd`
