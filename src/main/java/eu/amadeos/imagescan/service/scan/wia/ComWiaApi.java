package eu.amadeos.imagescan.service.scan.wia;

import com.sun.jna.platform.win32.COM.COMException;
import com.sun.jna.platform.win32.COM.COMInvokeException;
import com.sun.jna.platform.win32.COM.COMLateBindingObject;
import com.sun.jna.platform.win32.COM.IDispatch;
import com.sun.jna.platform.win32.OleAuto;
import com.sun.jna.platform.win32.Ole32;
import com.sun.jna.platform.win32.Variant;
import com.sun.jna.platform.win32.Variant.VARIANT;
import com.sun.jna.platform.win32.WinNT.HRESULT;
import eu.amadeos.imagescan.service.scan.BackendMissingException;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * WIA over COM with JNA, the way VBScript uses it, but inside the Java process. A PowerShell script started
 * by Java would be what malware does, and Windows Defender stops it.
 *
 * <p>All COM calls run on one platform thread of their own: COM is set up per thread, and virtual threads
 * change their carrier thread. The objects WIA hands out are used on that thread only.
 */
public final class ComWiaApi implements WiaApi {
    /** ImageFile format "PNG" of WIA. */
    static final String FORMAT_PNG = "{B96B3CAF-0728-11D3-9D7B-0000F81EF32E}";
    /** Device type "scanner" in DeviceInfo.Type. */
    static final int SCANNER_DEVICE = 1;

    private final ExecutorService com = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "wia-com");
        t.setDaemon(true);
        return t;
    });
    private boolean initialized;

    @Override
    public List<Device> devices() throws IOException {
        return onComThread(() -> {
            Disp manager = Disp.create("WIA.DeviceManager");
            Disp infos = manager.get("DeviceInfos");
            int count = infos.intValue("Count");
            List<Device> devices = new ArrayList<>();
            for (int i = 1; i <= count; i++) {
                Disp info = infos.get("Item", i);
                if (info.intValue("Type") != SCANNER_DEVICE) continue;
                Disp properties = info.get("Properties");
                devices.add(new Device(info.stringValue("DeviceID"), property(properties, "Manufacturer"),
                        property(properties, "Name")));
            }
            return devices;
        });
    }

    /** A property's value as text, or "" if the device does not have it. */
    private static String property(Disp properties, String name) {
        try {
            Object value = properties.get("Item", name).value("Value");
            return value == null ? "" : value.toString();
        } catch (COMException e) {
            return "";
        }
    }

    @Override
    public void scan(String deviceId, Map<Integer, Integer> properties, Path output) throws IOException {
        onComThread(() -> {
            Disp manager = Disp.create("WIA.DeviceManager");
            Disp infos = manager.get("DeviceInfos");
            Disp found = null;
            int count = infos.intValue("Count");
            for (int i = 1; i <= count && found == null; i++) {
                Disp info = infos.get("Item", i);
                if (deviceId.equals(info.stringValue("DeviceID"))) found = info;
            }
            if (found == null) throw new IOException("Scanner not found: " + deviceId);
            Disp device = found.call("Connect");
            Disp item = device.get("Items").get("Item", 1);
            Disp itemProperties = item.get("Properties");
            for (Map.Entry<Integer, Integer> p : properties.entrySet()) {
                try {
                    itemProperties.get("Item", String.valueOf(p.getKey())).put("Value", p.getValue());
                } catch (COMException e) {
                    // The scanner does not offer this setting (or not this value): scan with its default.
                }
            }
            Disp image = item.call("Transfer", FORMAT_PNG);
            image.call("SaveFile", output.toAbsolutePath().toString());
            return null;
        });
    }

    private interface ComWork<T> {
        T run() throws IOException;
    }

    private <T> T onComThread(ComWork<T> work) throws IOException {
        Callable<T> task = () -> {
            initialize();
            try {
                return work.run();
            } catch (COMException e) {
                throw new IOException(describe(e), e);
            }
        };
        Future<T> result = com.submit(task);
        try {
            return result.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException io) throw io;
            if (cause instanceof UnsatisfiedLinkError || cause instanceof NoClassDefFoundError) {
                throw new BackendMissingException("Windows Image Acquisition", cause);
            }
            throw new IOException(String.valueOf(cause), cause);
        }
    }

    /** Joins the multithreaded apartment once; WIA's objects then live in an apartment COM picks. */
    private void initialize() throws IOException {
        if (initialized) return;
        HRESULT hr = Ole32.INSTANCE.CoInitializeEx(null, Ole32.COINIT_MULTITHREADED);
        // S_FALSE: already initialized on this thread, which is fine too.
        if (hr.intValue() < 0) throw new IOException("COM could not be started: 0x" + Integer.toHexString(hr.intValue()));
        initialized = true;
    }

    /** Texts for the errors a user can act on; others keep WIA's own text and code. */
    static String describe(COMException e) {
        int code = e.getHresult() == null ? 0 : e.getHresult().intValue();
        String text = switch (code) {
            case 0x80210003 -> "The scanner reports no paper.";
            case 0x80210005 -> "The scanner is offline. Check that it is connected and switched on.";
            case 0x80210006 -> "The scanner is busy.";
            case 0x80210064 -> "The scan was cancelled at the scanner.";
            case 0x80040154, 0x800401F3 -> "Windows Image Acquisition is not installed on this computer.";
            default -> null;
        };
        if (text != null) return text;
        if (e instanceof COMInvokeException invoke && invoke.getDescription() != null) {
            return invoke.getDescription() + " (0x" + Integer.toHexString(code) + ")";
        }
        return e.getMessage();
    }

    /** An automation object: properties, methods and the values they return. */
    static final class Disp extends COMLateBindingObject {
        Disp(IDispatch dispatch) {
            super(dispatch);
        }

        private Disp(String progId) {
            super(progId, false);
        }

        static Disp create(String progId) throws IOException {
            try {
                return new Disp(progId);
            } catch (COMException e) {
                int code = e.getHresult() == null ? 0 : e.getHresult().intValue();
                if (code == 0x800401F3 || code == 0x80040154) {
                    throw new BackendMissingException("Windows Image Acquisition", e);
                }
                throw e;
            }
        }

        /** A property (with arguments, e.g. {@code Item(1)}) that is an object. */
        Disp get(String name, Object... args) {
            VARIANT.ByReference result = new VARIANT.ByReference();
            oleMethod(OleAuto.DISPATCH_PROPERTYGET, result, name, arguments(args));
            Object value = result.getValue();
            if (!(value instanceof IDispatch dispatch)) throw new COMException(name + " is not an object");
            return new Disp(dispatch);
        }

        /** A property's plain value: Integer, String (from BSTR), ... */
        Object value(String name, Object... args) {
            VARIANT.ByReference result = new VARIANT.ByReference();
            oleMethod(OleAuto.DISPATCH_PROPERTYGET, result, name, arguments(args));
            Object value = result.getValue();
            if (value instanceof com.sun.jna.platform.win32.WTypes.BSTR) {
                String text = result.stringValue();
                OleAuto.INSTANCE.VariantClear(result);
                return text;
            }
            return value;
        }

        int intValue(String name) {
            Object v = value(name);
            if (v instanceof Number n) return n.intValue();
            if (v instanceof com.sun.jna.IntegerType n) return n.intValue();
            throw new COMException(name + " is not a number: " + v);
        }

        String stringValue(String name) {
            Object v = value(name);
            return v == null ? "" : v.toString();
        }

        void put(String name, int value) {
            oleMethod(OleAuto.DISPATCH_PROPERTYPUT, null, name, new VARIANT[]{byReference(new VARIANT(value))});
        }

        /** A method that returns an object. */
        Disp call(String name, Object... args) {
            VARIANT.ByReference result = new VARIANT.ByReference();
            VARIANT[] values = new VARIANT[args.length];
            // Method parameters of WIA (FormatID, Filename) are BSTRs passed by value.
            for (int i = 0; i < args.length; i++) values[i] = variant(args[i]);
            oleMethod(OleAuto.DISPATCH_METHOD, result, name, values);
            Object value = result.getValue();
            return value instanceof IDispatch dispatch ? new Disp(dispatch) : null;
        }

        /** {@code Item(Index)} and {@code Value} take a VARIANT by reference in WIA's type library. */
        private static VARIANT[] arguments(Object... args) {
            VARIANT[] values = new VARIANT[args.length];
            for (int i = 0; i < args.length; i++) values[i] = byReference(variant(args[i]));
            return values;
        }

        private static VARIANT variant(Object arg) {
            if (arg instanceof Integer i) return new VARIANT(i);
            if (arg instanceof String s) return new VARIANT(s);
            throw new IllegalArgumentException("Unsupported argument " + arg);
        }

        private static VARIANT byReference(VARIANT value) {
            VARIANT ref = new VARIANT();
            ref.setValue(Variant.VT_BYREF | Variant.VT_VARIANT, new VARIANT.ByReference(value));
            return ref;
        }
    }
}
