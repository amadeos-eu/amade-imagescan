package eu.amadeos.imagescan.ui.main;

/** Where the image in the window came from; decides what can be done with it. */
public enum ImageKind {
    /** A quick scan of the whole glass, to frame the part to scan; not edited or saved. */
    PREVIEW,
    /** A scan, not saved yet. */
    SCAN,
    /** An image opened from a file, or a scan that was saved. */
    FILE
}
