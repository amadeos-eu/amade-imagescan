package eu.amadeos.imagescan.service.print;

import eu.amadeos.imagescan.domain.ScannedImage;

import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.standard.JobName;
import javax.print.attribute.standard.OrientationRequested;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.util.Locale;

/** Prints a scan through the system's printers (CUPS on amadeOS), after the print dialog. */
public class PrintService {
    /**
     * Shows the print dialog and prints.
     *
     * @return false if the user cancelled
     */
    public boolean print(ScannedImage scan, String jobName) throws PrinterException {
        PrinterJob job = PrinterJob.getPrinterJob();
        job.setPrintable(new ImagePrintable(scan));
        job.setJobName(jobName);
        PrintRequestAttributeSet attributes = new HashPrintRequestAttributeSet();
        attributes.add(new JobName(jobName, Locale.getDefault()));
        // A landscape scan fills the paper better turned.
        attributes.add(scan.width() > scan.height() ? OrientationRequested.LANDSCAPE : OrientationRequested.PORTRAIT);
        if (!job.printDialog(attributes)) return false;
        job.print(attributes);
        return true;
    }
}
