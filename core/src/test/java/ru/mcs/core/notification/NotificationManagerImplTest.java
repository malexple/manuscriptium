package ru.mcs.core.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import ru.mcs.spi.ui.NotificationType;

class NotificationManagerImplTest {

    private final NotificationManagerImpl manager = new NotificationManagerImpl();

    @Test
    void historyKeepsOrderAndSource() {
        manager.scopedFor("p1").info("t1", "c1");
        manager.scopedFor("p2").error("t2", "c2");
        List<NotificationManagerImpl.Notice> history = manager.history();
        assertThat(history).extracting(NotificationManagerImpl.Notice::title).containsExactly("t1", "t2");
        assertThat(history).extracting(NotificationManagerImpl.Notice::source).containsExactly("p1", "p2");
        assertThat(history.get(1).type()).isEqualTo(NotificationType.ERROR);
    }

    @Test
    void historyIsCapped() {
        for (int i = 0; i < NotificationManagerImpl.MAX_HISTORY + 20; i++) {
            manager.notify("core", "n" + i, "", NotificationType.INFO);
        }
        assertThat(manager.history()).hasSize(NotificationManagerImpl.MAX_HISTORY);
        assertThat(manager.history().get(0).title()).isEqualTo("n20");
    }

    @Test
    void listenersGetNoticesUntilRemoved() {
        List<String> titles = new ArrayList<>();
        Runnable remove = manager.addListener(n -> titles.add(n.title()));
        manager.notify("core", "a", "", NotificationType.INFO);
        remove.run();
        manager.notify("core", "b", "", NotificationType.INFO);
        assertThat(titles).containsExactly("a");
    }

    @Test
    void nullsAreNormalised() {
        manager.notify("core", null, null, null);
        NotificationManagerImpl.Notice n = manager.history().get(0);
        assertThat(n.title()).isEmpty();
        assertThat(n.content()).isEmpty();
        assertThat(n.type()).isEqualTo(NotificationType.INFO);
    }
}
