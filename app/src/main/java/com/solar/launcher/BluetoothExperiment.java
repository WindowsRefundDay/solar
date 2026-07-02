package com.solar.launcher;

import android.content.SharedPreferences;

/** Experiment gate — in-app Bluetooth screen; off launches Android BT settings. On by default. */
public final class BluetoothExperiment {
    public static final String PREF_BLUETOOTH_EXPERIMENT = "solar_bluetooth_experiment";

    private BluetoothExperiment() {}

    /** On by default; disable from Settings → Debug and Experiments. */
    public static boolean isEnabled(SharedPreferences prefs) {
        return prefs == null || prefs.getBoolean(PREF_BLUETOOTH_EXPERIMENT, true);
    }
}
