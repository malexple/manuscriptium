package ru.mcs.core.plugin;

import ru.mcs.spi.McsSdk;

/** major должен совпадать; для 0.x (нестабильный API) должен совпадать и minor; иначе minor плагина <= minor SDK. */
public final class SdkCompatibility {

    private SdkCompatibility() {
    }

    public static boolean isCompatible(String required) {
        return isCompatible(McsSdk.VERSION, required);
    }

    public static boolean isCompatible(String sdk, String required) {
        int[] s = parse(sdk);
        int[] r = parse(required);
        if (s[0] != r[0]) {
            return false;
        }
        return s[0] == 0 ? s[1] == r[1] : r[1] <= s[1];
    }

    private static int[] parse(String version) {
        String[] p = version.trim().split("[.\\-]");
        try {
            return new int[]{Integer.parseInt(p[0]), p.length > 1 ? Integer.parseInt(p[1]) : 0};
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Bad version: " + version);
        }
    }
}
