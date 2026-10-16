package org.datazuul.amadeos.imagescan.bootstrap;

import org.datazuul.amadeos.commons.app.AmadeApplication;
import org.datazuul.amadeos.commons.app.AppInfo;
import org.datazuul.amadeos.imagescan.ui.main.MainPresenter;
import org.datazuul.amadeos.imagescan.ui.main.MainWindow;

import java.util.List;

public final class AmadeImagescanApplication {
    public static final AppInfo APP = new AppInfo("amade-imagescan", "Image Scan", "imagescan");

    private AmadeImagescanApplication() {
    }

    /** The version from the jar's manifest; "dev" when run from the IDE. */
    public static String version() {
        String version = AmadeImagescanApplication.class.getPackage().getImplementationVersion();
        return version == null ? "dev" : version;
    }

    /** Opens the image file named as first argument, if any. */
    public static void main(String[] args) {
        AmadeApplication.launch(APP, injector -> {
            MainWindow window = injector.getInstance(MainWindow.class);
            MainPresenter presenter = injector.getInstance(MainPresenter.class);
            window.bind(presenter);
            window.setVisible(true);
            presenter.start(List.of(args));
        }, new ImagescanModule());
    }
}
