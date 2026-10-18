package eu.amadeos.imagescan.bootstrap;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.swing.EdtExecutor;
import eu.amadeos.imagescan.repository.ImageFiles;
import eu.amadeos.imagescan.repository.ImagescanPreferences;
import eu.amadeos.imagescan.service.image.ImageService;
import eu.amadeos.imagescan.service.print.PrintService;
import eu.amadeos.imagescan.service.scan.ScanService;
import eu.amadeos.imagescan.service.scan.ScannerBackend;
import eu.amadeos.imagescan.service.scan.sane.SaneBackend;
import eu.amadeos.imagescan.service.scan.wia.WiaBackend;
import eu.amadeos.imagescan.ui.main.MainView;
import eu.amadeos.imagescan.ui.main.MainWindow;
import eu.amadeos.imagescan.ui.main.Ui;

import java.util.Locale;
import java.util.concurrent.Executor;

/**
 * amade-imagescan bindings. The background executor, theme, language and the other shared services come
 * from {@link eu.amadeos.commons.app.CommonsModule}.
 */
public final class ImagescanModule extends AbstractModule {
    /** The texts, {@code messages.properties} and its translations. */
    public static final String MESSAGES = "eu.amadeos.imagescan.messages";

    @Override
    protected void configure() {
        bind(ImageFiles.class).in(Scopes.SINGLETON);
        bind(ImagescanPreferences.class).in(Scopes.SINGLETON);
        bind(ImageService.class).in(Scopes.SINGLETON);
        bind(ScanService.class).in(Scopes.SINGLETON);
        bind(PrintService.class).in(Scopes.SINGLETON);
        bind(MainWindow.class).in(Scopes.SINGLETON);
        bind(MainView.class).to(MainWindow.class);
    }

    /** SANE on Linux (amadeOS), WIA on Windows. */
    @Provides
    @Singleton
    ScannerBackend backend() {
        return isWindows(System.getProperty("os.name", "")) ? new WiaBackend() : new SaneBackend();
    }

    static boolean isWindows(String osName) {
        return osName.toLowerCase(Locale.ROOT).startsWith("windows");
    }

    /** Continues on the event dispatch thread after background work. */
    @Provides
    @Ui
    Executor ui(EdtExecutor edt) {
        return edt;
    }

    /** The texts in the language {@code AmadeApplication} chose. */
    @Provides
    @Singleton
    Messages messages(Locale locale) {
        return Messages.load(ImagescanModule.class, MESSAGES, locale);
    }
}
