package org.datazuul.amadeos.imagescan.service.scan;

import java.io.IOException;
import java.util.List;

/** Starts external programs; tests replace it with canned output. */
@FunctionalInterface
public interface CommandRunner {
    Process start(List<String> command) throws IOException;

    /** Starts real processes, with the environment of this program. */
    static CommandRunner system() {
        return command -> new ProcessBuilder(command).start();
    }
}
