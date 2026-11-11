package ru.mcs.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.desktop.QuitResponse;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class MacOsSupportTest {

    @Test
    void detectsOnlyMacOs() {
        assertThat(MacOsSupport.isMacOs("Mac OS X")).isTrue();
        assertThat(MacOsSupport.isMacOs("Darwin")).isTrue();
        assertThat(MacOsSupport.isMacOs("Windows 11")).isFalse();
        assertThat(MacOsSupport.isMacOs("Linux")).isFalse();
    }

    @Test
    void earlyPropertiesAreInstalledOnlyOnMacOs() {
        Properties mac = new Properties();
        MacOsSupport.installEarly(mac, true);

        assertThat(mac.getProperty("apple.laf.useScreenMenuBar")).isEqualTo("true");
        assertThat(mac.getProperty("apple.awt.application.name")).isEqualTo("Manuscriptium");
        assertThat(mac.getProperty("apple.awt.application.appearance")).isEqualTo("system");

        Properties other = new Properties();
        other.setProperty("apple.laf.useScreenMenuBar", "unchanged");
        MacOsSupport.installEarly(other, false);

        assertThat(other.getProperty("apple.laf.useScreenMenuBar")).isEqualTo("unchanged");
        assertThat(other.getProperty("apple.awt.application.name")).isNull();
        assertThat(other.getProperty("apple.awt.application.appearance")).isNull();
    }

    @Test
    void unifiedToolbarKeepsWindowButtonsInsetOutsideRefreshableContent() {
        JRootPane rootPane = new JRootPane();
        JPanel toolbar = new JPanel();
        toolbar.add(new JButton("action"));

        JComponent unified = MacOsSupport.unifiedToolbar(rootPane, toolbar, true);

        assertThat(unified).isNotSameAs(toolbar);
        assertThat(rootPane.getClientProperty("apple.awt.fullWindowContent")).isEqualTo(true);
        assertThat(rootPane.getClientProperty("apple.awt.transparentTitleBar")).isEqualTo(true);
        assertThat(rootPane.getClientProperty("apple.awt.windowTitleVisible")).isEqualTo(false);

        BorderLayout layout = (BorderLayout) unified.getLayout();
        Component inset = layout.getLayoutComponent(BorderLayout.WEST);
        assertThat(inset.getPreferredSize().width).isEqualTo(MacOsSupport.WINDOW_BUTTONS_INSET);
        assertThat(layout.getLayoutComponent(BorderLayout.CENTER)).isSameAs(toolbar);

        toolbar.removeAll(); // Именно так ActionToolbar.refresh() перестраивает кнопки.
        assertThat(layout.getLayoutComponent(BorderLayout.WEST)).isSameAs(inset);
        assertThat(layout.getLayoutComponent(BorderLayout.CENTER)).isSameAs(toolbar);
    }

    @Test
    void nonMacToolbarAndRootPaneRemainUntouched() {
        JRootPane rootPane = new JRootPane();
        JPanel toolbar = new JPanel();

        JComponent result = MacOsSupport.unifiedToolbar(rootPane, toolbar, false);

        assertThat(result).isSameAs(toolbar);
        assertThat(rootPane.getClientProperty("apple.awt.fullWindowContent")).isNull();
        assertThat(rootPane.getClientProperty("apple.awt.transparentTitleBar")).isNull();
        assertThat(rootPane.getClientProperty("apple.awt.windowTitleVisible")).isNull();
    }

    @Test
    void nativeQuitIsCancelledBeforeCommonShellQuitRuns() throws Exception {
        AtomicBoolean cancelled = new AtomicBoolean();
        AtomicBoolean quitOnEdt = new AtomicBoolean();
        AtomicInteger quitCalls = new AtomicInteger();
        List<String> events = Collections.synchronizedList(new ArrayList<>());

        QuitResponse response = new QuitResponse() {
            @Override
            public void performQuit() {
                throw new AssertionError(
                        "Нативный Quit не должен обходить проверку редакторов"
                );
            }

            @Override
            public void cancelQuit() {
                events.add("cancelQuit");
                cancelled.set(true);
            }
        };

        MacOsSupport.quitHandler(() -> {
            events.add("quit");
            quitOnEdt.set(SwingUtilities.isEventDispatchThread());
            quitCalls.incrementAndGet();
        }).handleQuitRequestWith(null, response);

        assertThat(cancelled).isTrue();

        // Ждём выполнения ранее поставленного в EDT обратного вызова.
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(events).containsExactly("cancelQuit", "quit");
        assertThat(quitCalls).hasValue(1);
        assertThat(quitOnEdt).isTrue();
    }
}
