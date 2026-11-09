package ru.mcs.core.plugin;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SdkCompatibilityTest {

    @Test
    void zeroMajorRequiresSameMinor() {
        assertThat(SdkCompatibility.isCompatible("0.1.0", "0.1")).isTrue();
        assertThat(SdkCompatibility.isCompatible("0.2.0", "0.1")).isFalse();
    }

    @Test
    void stableMajorAllowsOlderMinor() {
        assertThat(SdkCompatibility.isCompatible("1.3.0", "1.2")).isTrue();
        assertThat(SdkCompatibility.isCompatible("1.1.0", "1.2")).isFalse();
        assertThat(SdkCompatibility.isCompatible("2.0.0", "1.0")).isFalse();
    }

    @Test
    void snapshotSuffixIsIgnored() {
        assertThat(SdkCompatibility.isCompatible("0.1.0-SNAPSHOT", "0.1")).isTrue();
    }
}
