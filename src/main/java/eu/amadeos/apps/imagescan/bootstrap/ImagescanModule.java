package eu.amadeos.apps.imagescan.bootstrap;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.apps.imagescan.repository.ImageFiles;
import eu.amadeos.apps.imagescan.repository.ImagescanPreferences;
import eu.amadeos.apps.imagescan.service.image.ImageService;
import eu.amadeos.apps.imagescan.service.print.PrintService;
import eu.amadeos.apps.imagescan.service.scan.ScanService;
import eu.amadeos.apps.imagescan.service.scan.ScannerBackend;
import eu.amadeos.apps.imagescan.service.scan.sane.SaneBackend;
import eu.amadeos.apps.imagescan.service.scan.wia.WiaBackend;
import eu.amadeos.apps.imagescan.ui.main.MainView;
import eu.amadeos.apps.imagescan.ui.main.MainWindow;


import java.util.Locale;
/**
 * amade-imagescan bindings. The background executor, theme, language and the other shared services come
 * from {@link eu.amadeos.commons.app.CommonsModule}.
 */
public final class ImagescanModule extends AbstractModule {
    /** The texts, {@code messages.properties} and its translations. */
    public static final String MESSAGES = "eu.amadeos.apps.imagescan.messages";

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

    /** The texts in the language {@code AmadeApplication} chose. */
    @Provides
    @Singleton
    Messages messages(Locale locale) {
        return Messages.load(ImagescanModule.class, MESSAGES, locale);
    }
}
