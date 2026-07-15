package com.solar.launcher;

import android.os.IBinder;

/**
 * Standalone helper executed out-of-process via app_process as root.
 * Toggles USB mass storage and switches {@code sys.usb.config} so the kernel
 * function list actually changes (MountService alone is not enough on Y1).
 */
public class UmsEnabler {
    static final String MASS_STORAGE_USB_CONFIG = "mass_storage,adb";
    static final String DEFAULT_USB_CONFIG = "mtp,adb";

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: UmsEnabler [1|0]");
            System.exit(1);
        }
        boolean enable = "1".equals(args[0]);
        try {
            Class<?> smClass = Class.forName("android.os.ServiceManager");
            IBinder service = (IBinder) smClass.getMethod("getService", String.class).invoke(null, "mount");
            Class<?> stub = Class.forName("android.os.storage.IMountService$Stub");
            Object mountService = stub.getMethod("asInterface", IBinder.class).invoke(null, service);
            mountService.getClass().getMethod("setUsbMassStorageEnabled", boolean.class)
                    .invoke(mountService, enable);
            // ponytail: Y1 keeps mass_storage in the kernel list until config is switched.
            setUsbConfig(usbConfigFor(enable));
            System.out.println("UMS enable=" + enable + " executed successfully.");
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    static String usbConfigFor(boolean enable) {
        return enable ? MASS_STORAGE_USB_CONFIG : DEFAULT_USB_CONFIG;
    }

    private static void setUsbConfig(String config) throws Exception {
        Process p = Runtime.getRuntime().exec(new String[]{"setprop", "sys.usb.config", config});
        if (p.waitFor() != 0) {
            throw new RuntimeException("setprop sys.usb.config " + config + " failed");
        }
    }
}
