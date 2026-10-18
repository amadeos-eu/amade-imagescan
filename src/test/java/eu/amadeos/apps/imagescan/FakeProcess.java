package eu.amadeos.apps.imagescan;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** A process with canned output, for the backends' tests. */
public final class FakeProcess extends Process {
    private final InputStream stdout;
    private final InputStream stderr;
    private final int exitCode;
    private final ByteArrayOutputStream stdin = new ByteArrayOutputStream();
    private final CountDownLatch destroyed = new CountDownLatch(1);
    private final boolean hangs;

    public FakeProcess(byte[] stdout, byte[] stderr, int exitCode) {
        this.stdout = new ByteArrayInputStream(stdout);
        this.stderr = new ByteArrayInputStream(stderr);
        this.exitCode = exitCode;
        this.hangs = false;
    }

    public FakeProcess(String stdout, String stderr, int exitCode) {
        this(stdout.getBytes(), stderr.getBytes(), exitCode);
    }

    /** A process whose standard output delivers {@code prefix} and then blocks until it is destroyed. */
    public static FakeProcess hanging(byte[] prefix) {
        return new FakeProcess(prefix);
    }

    private FakeProcess(byte[] prefix) {
        this.hangs = true;
        this.exitCode = 143;
        this.stderr = new ByteArrayInputStream("Progress: 10.0%\r".getBytes());
        ByteArrayInputStream first = new ByteArrayInputStream(prefix);
        this.stdout = new InputStream() {
            @Override
            public int read() throws IOException {
                int b = first.read();
                if (b >= 0) return b;
                try {
                    destroyed.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                throw new IOException("Stream closed");
            }
        };
    }

    public boolean isDestroyed() {
        return destroyed.getCount() == 0;
    }

    @Override
    public OutputStream getOutputStream() {
        return stdin;
    }

    @Override
    public InputStream getInputStream() {
        return stdout;
    }

    @Override
    public InputStream getErrorStream() {
        return stderr;
    }

    @Override
    public int waitFor() throws InterruptedException {
        if (hangs) destroyed.await();
        return exitCode;
    }

    @Override
    public boolean waitFor(long timeout, TimeUnit unit) throws InterruptedException {
        return !hangs || destroyed.await(timeout, unit);
    }

    @Override
    public int exitValue() {
        if (hangs && !isDestroyed()) throw new IllegalThreadStateException("running");
        return exitCode;
    }

    @Override
    public void destroy() {
        destroyed.countDown();
    }

    @Override
    public Process destroyForcibly() {
        destroy();
        return this;
    }

    @Override
    public boolean isAlive() {
        return hangs && !isDestroyed();
    }
}
