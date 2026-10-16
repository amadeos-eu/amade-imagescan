# amade-imagescan

**amade-imagescan** is the scanning program of the [amadeOS](https://github.com/datazuul/amadeos) desktop: scan photos, letters and documents from a flatbed scanner, frame the part you want, crop, turn and save.

![License](https://img.shields.io/badge/license-EUPL--1.2-blue)
![Java](https://img.shields.io/badge/Java-21-orange)

## Features

- **Preview, frame, scan** as in XSane or the Windows scanning dialog: *Preview* scans the whole glass quickly at 75 dpi, you drag a frame around the photo or page you want, and *Scan* scans only that part at the chosen resolution. Without a frame the whole glass is scanned.
- **Scanner, mode and resolution** in a panel beside the image. The modes (colour, greyscale, black and white) and resolutions are the ones the scanner offers; the panel shows the area and the size of the result ("≈ 2480 × 3508 pixels, 25 MB"). Very large scans are confirmed first. The scanner, mode and resolution used last are remembered.
- **Progress and cancel**: the status bar shows how far the scan is; *Cancel Scan* stops the scanner and keeps the image you had.
- **Editing** that keeps every pixel: crop to the frame, rotate left/right/180°, flip; undo and redo. Line art stays one bit per pixel, grey stays grey.
- **Image view** with zoom (mouse wheel at the pointer, *Fit in Window*, *Actual Pixels*), panning with the right or middle mouse button, and a frame with eight handles, its size in pixels and millimetres.
- **Save** as PNG (default), JPEG or TIFF, with the resolution written into the file so that other programs print the scan at its true size. Black-and-white scans are saved as 1-bit PNG or CCITT G4 TIFF. A dated file name in your Pictures folder is suggested.
- **Open** existing images to crop or turn them, **print** at true size (or shrunk to fit), **copy** to the clipboard.
- Five languages (English, Deutsch, Français, Español, Italiano), following the amadeOS desktop language; *Options → Language* overrides it.
- Theme colours, Motif look and 3D toolbar buttons from [amade-commons](https://github.com/datazuul/amade-commons); the toolbar icons are drawn in Java2D, so no icon theme is needed.

## Scanning backends

| Platform | Backend | Needs |
|---|---|---|
| Linux / amadeOS | SANE through its command line program `scanimage` | `sudo apt install sane-utils` (a dependency of the Debian package); network scanners (eSCL/AirScan, WSD) also `sane-airscan` |
| Windows | Windows Image Acquisition over COM, inside the program (JNA) | nothing beyond a WIA or TWAIN driver for the scanner |

`scanimage` is asked for the device list (`scanimage -f`), the options of the chosen scanner (`scanimage -d <device> -A`: modes, resolutions, size of the glass) and then scans with `--format=pnm --progress` to standard output. The image is decoded while it arrives, so no temporary files are needed. Check that SANE sees your scanner with `scanimage -L`.

## Architecture

Java 21, Swing and Guice (and JNA on Windows), built like [amade-writepad](https://github.com/datazuul/amade-writepad); theme, language and start-up come from `AmadeApplication` in amade-commons. Packages, key decisions and flows are described in [ARCHITECTURE.md](ARCHITECTURE.md).

## Build and run

```sh
./gradlew run                      # start
./gradlew run --args=photo.png     # open an image
./gradlew test                     # Swing tests need a display: xvfb-run ./gradlew test
./gradlew deb                      # build/distributions/amade-imagescan_<version>_all.deb
./gradlew installToAmadeOs         # the .deb into ../amadeos/config/packages.chroot/ for the ISO build
./gradlew windowsInstaller         # on Windows: MSI with its own Java runtime (needs WiX Toolset 3)
./gradlew windowsInstaller -PinstallerType=app-image   # portable folder with amade-imagescan.exe
```

With `../amade-commons` checked out next to this repository, Gradle builds against that checkout instead of the JitPack version.

## Planned

- Several pages into one PDF (documents with a sheet feeder or one page after another)
- Brightness, contrast and automatic straightening
- Scanner buttons ("scan" key on the device) through `scanbd`

## Licence

EUPL-1.2, see [LICENSE](LICENSE).
