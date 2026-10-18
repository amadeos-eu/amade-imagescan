package eu.amadeos.imagescan.service.scan;

import java.io.IOException;

/** The scanning program a backend needs ({@code scanimage}, {@code powershell.exe}) is not installed. */
public class BackendMissingException extends IOException {
    private final String program;

    public BackendMissingException(String program, Throwable cause) {
        super(program + " not found", cause);
        this.program = program;
    }

    public String program() {
        return program;
    }
}
