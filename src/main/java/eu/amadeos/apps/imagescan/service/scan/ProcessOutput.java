package eu.amadeos.apps.imagescan.service.scan;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Runs short commands such as {@code scanimage -L} and collects what they print. */
public final class ProcessOutput {
    private ProcessOutput() {
    }

    /** What a command printed and how it ended. */
    public record Result(int exitCode, String stdout, String stderr) {
        public boolean succeeded() {
            return exitCode == 0;
        }
    }

    /**
     * Runs the command to its end, reading both streams at once so that neither fills up and blocks the
     * program.
     *
     * @param timeoutSeconds the program is stopped after this long
     * @throws BackendMissingException if the program does not exist
     */
    public static Result run(CommandRunner runner, List<String> command, long timeoutSeconds, Charset charset)
            throws IOException, InterruptedException {
        return run(runner, command, timeoutSeconds, charset, p -> {
        });
    }

    /** As {@link #run(CommandRunner, List, long, Charset)}, handing the started process to {@code started}. */
    public static Result run(CommandRunner runner, List<String> command, long timeoutSeconds, Charset charset,
                             Consumer<Process> started) throws IOException, InterruptedException {
        Process process;
        try {
            process = runner.start(command);
        } catch (IOException e) {
            throw missing(command.getFirst(), e);
        }
        started.accept(process);
        process.getOutputStream().close();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        Thread errReader = Thread.ofVirtual().start(() -> copy(process.getErrorStream(), err));
        try {
            byte[] out = process.getInputStream().readAllBytes();
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException(command.getFirst() + " did not finish within " + timeoutSeconds + " s");
            }
            errReader.join(TimeUnit.SECONDS.toMillis(5));
            return new Result(process.exitValue(), new String(out, charset), err.toString(charset));
        } catch (InterruptedException e) {
            process.destroyForcibly();
            throw e;
        }
    }

    /** Turns "Cannot run program" into {@link BackendMissingException}; other errors stay as they are. */
    public static IOException missing(String program, IOException e) {
        String message = String.valueOf(e.getMessage());
        if (message.contains("Cannot run program") || message.contains("error=2")
                || message.contains("No such file")) {
            return new BackendMissingException(program, e);
        }
        return e;
    }

    static void copy(InputStream in, ByteArrayOutputStream out) {
        try (in) {
            in.transferTo(out);
        } catch (IOException e) {
            // The process was stopped; what it printed so far is kept.
        }
    }
}
