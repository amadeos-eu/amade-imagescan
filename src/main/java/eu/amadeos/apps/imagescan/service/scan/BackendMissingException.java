package eu.amadeos.apps.imagescan.service.scan;

import eu.amadeos.commons.process.ProgramMissingException;

/**
 * What a backend needs is not installed: a program ({@code scanimage} reports this as the
 * {@link ProgramMissingException} of amade-commons) or a system part such as Windows Image Acquisition.
 */
public class BackendMissingException extends ProgramMissingException {
    public BackendMissingException(String program, Throwable cause) {
        super(program, cause);
    }
}
