package ru.mcs.core;

import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.JOptionPane;
import ru.mcs.core.i18n.Messages;
import ru.mcs.core.shell.ShellFrame;

/** Общий флаг «требуется перезапуск» для установки и включения/отключения плагинов. */
public final class PluginRestartController {

    private final ShellFrame frame;
    private final String[] args;
    private final AtomicBoolean required = new AtomicBoolean();

    public PluginRestartController(ShellFrame frame, String[] args) {
        this.frame = frame;
        this.args = args.clone();
    }

    public void markRequired() {
        required.set(true);
    }

    /** Вызывать после закрытия модального диалога настроек. */
    public void promptIfNeeded() {
        if (!required.getAndSet(false)) {
            return;
        }
        int answer = JOptionPane.showConfirmDialog(frame, Messages.get("plugins.restart.message"),
                Messages.get("plugins.restart.title"), JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (answer == JOptionPane.YES_OPTION) {
            AppRestarter.restart(frame, args);
        }
    }
}
