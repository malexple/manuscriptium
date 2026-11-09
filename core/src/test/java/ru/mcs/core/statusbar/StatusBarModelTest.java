package ru.mcs.core.statusbar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import javax.swing.JComponent;
import javax.swing.JLabel;
import org.junit.jupiter.api.Test;
import ru.mcs.spi.ui.StatusBarTask;
import ru.mcs.spi.ui.StatusBarWidget;

class StatusBarModelTest {

    private static final class TestWidget implements StatusBarWidget {
        final String id;
        boolean disposed;

        TestWidget(String id) {
            this.id = id;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public Position getPosition() {
            return Position.RIGHT;
        }

        @Override
        public JComponent createComponent() {
            return new JLabel(id);
        }

        @Override
        public void dispose() {
            disposed = true;
        }
    }

    private final StatusBarModel model = new StatusBarModel();

    @Test
    void widgetsAreRegisteredAndDuplicatesRejected() {
        model.addWidget("p", new TestWidget("w1"));
        assertThat(model.widgetIds()).containsExactly("w1");
        assertThatThrownBy(() -> model.addWidget("q", new TestWidget("w1")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void foreignOwnerCannotRemoveWidget() {
        model.addWidget("p", new TestWidget("w1"));
        assertThatThrownBy(() -> model.removeWidget("q", "w1")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void removeOwnerDisposesWidgetsAndFinishesTasks() {
        TestWidget w = new TestWidget("w1");
        model.addWidget("p", w);
        StatusBarModel.Task t = model.startTask("p", "work");
        model.startTask("other", "keep");

        model.removeOwner("p");

        assertThat(w.disposed).isTrue();
        assertThat(model.widgets()).isEmpty();
        assertThat(model.tasks()).hasSize(1);
        assertThat(t.isFinished()).isTrue();
    }

    @Test
    void taskFinishIsIdempotent() {
        int[] changes = {0};
        model.addChangeListener(() -> changes[0]++);
        StatusBarTask task = model.startTask("p", "work");
        task.finish();
        task.finish();
        assertThat(model.tasks()).isEmpty();
        assertThat(changes[0]).isEqualTo(2);
    }

    @Test
    void taskTitleCanBeUpdated() {
        StatusBarModel.Task task = model.startTask("p", "a");
        task.setTitle("b");
        assertThat(model.tasks().get(0).title()).isEqualTo("b");
    }

    @Test
    void messageIsStoredAndNullBecomesEmpty() {
        model.setMessage("hello");
        assertThat(model.message()).isEqualTo("hello");
        model.setMessage(null);
        assertThat(model.message()).isEmpty();
    }

    @Test
    void scopedViewUsesOwner() {
        model.scopedFor("p").addWidget(new TestWidget("w"));
        model.scopedFor("p").startTask("t");
        model.removeOwner("p");
        assertThat(model.widgets()).isEmpty();
        assertThat(model.tasks()).isEmpty();
    }
}
