package ru.mcs.core;

import com.formdev.flatlaf.FlatLightLaf;

final class LafInstaller {

    private LafInstaller() {
    }

    static void install() {
        FlatLightLaf.setup();
    }
}
