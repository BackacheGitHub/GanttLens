package com.ganttlens.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for TaskSelectionModel — pure Java, no JavaFX.
 */
class TaskSelectionModelTest {

    private TaskSelectionModel model;
    private List<Task> sampleTasks;

    @BeforeEach
    void setUp() {
        model = new TaskSelectionModel();
        sampleTasks = List.of(
            new Task("t1", "Task A", null, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 5), 5, List.of(), List.of(), TaskStatus.IN_PROGRESS, null),
            new Task("t2", "Task B", null, LocalDate.of(2026, 7, 6), LocalDate.of(2026, 7, 10), 5, List.of(), List.of(), TaskStatus.PENDING, null),
            new Task("t3", "Task C", null, LocalDate.of(2026, 7, 11), LocalDate.of(2026, 7, 15), 5, List.of(), List.of(), TaskStatus.COMPLETED, null)
        );
        model.setTasks(sampleTasks);
    }

    @Test
    void selectTask_addsToSelectedIds() {
        model.selectTask("t1");
        assertThat(model.getSelectedIds()).containsExactly("t1");
    }

    @Test
    void selectTask_singleSelect_clearsPrevious() {
        model.selectTask("t1");
        model.selectTask("t2");
        assertThat(model.getSelectedIds()).containsExactly("t2");
    }

    @Test
    void selectTask_withNull_clearsSelection() {
        model.selectTask("t1");
        model.selectTask(null);
        assertThat(model.getSelectedIds()).isEmpty();
    }

    @Test
    void addToSelection_multiSelect() {
        model.selectTask("t1");
        model.addToSelection("t2");
        assertThat(model.getSelectedIds()).containsExactlyInAnyOrder("t1", "t2");
    }

    @Test
    void addToSelection_withNull_doesNothing() {
        model.selectTask("t1");
        model.addToSelection(null);
        assertThat(model.getSelectedIds()).containsExactly("t1");
    }

    @Test
    void deselectTask_removesFromSelection() {
        model.selectTask("t1");
        model.addToSelection("t2");
        model.deselectTask("t1");
        assertThat(model.getSelectedIds()).containsExactly("t2");
    }

    @Test
    void clearSelection_emptiesAll() {
        model.selectTask("t1");
        model.addToSelection("t2");
        model.addToSelection("t3");
        model.clearSelection();
        assertThat(model.getSelectedIds()).isEmpty();
    }

    @Test
    void isSelected_reflectsState() {
        assertThat(model.isSelected("t1")).isFalse();
        model.selectTask("t1");
        assertThat(model.isSelected("t1")).isTrue();
        assertThat(model.isSelected("t2")).isFalse();
    }

    @Test
    void getSelectedTask_returnsFullTask() {
        model.selectTask("t2");
        Task selected = model.getSelectedTask();
        assertThat(selected).isNotNull();
        assertThat(selected.id()).isEqualTo("t2");
        assertThat(selected.name()).isEqualTo("Task B");
    }

    @Test
    void getSelectedTask_returnsNullWhenEmpty() {
        assertThat(model.getSelectedTask()).isNull();
    }

    @Test
    void getSelectedTasks_returnsAllSelected() {
        model.selectTask("t1");
        model.addToSelection("t3");
        List<Task> tasks = model.getSelectedTasks();
        assertThat(tasks).hasSize(2);
        assertThat(tasks).extracting(Task::id).containsExactlyInAnyOrder("t1", "t3");
    }

    @Test
    void hasSelection_reflectsState() {
        assertThat(model.hasSelection()).isFalse();
        model.selectTask("t1");
        assertThat(model.hasSelection()).isTrue();
        model.clearSelection();
        assertThat(model.hasSelection()).isFalse();
    }

    @Test
    void onSelectionChanged_callbackFires() {
        AtomicReference<Task> lastSelected = new AtomicReference<>();
        model.setOnSelectionChanged(lastSelected::set);

        model.selectTask("t1");
        assertThat(lastSelected.get()).isNotNull();
        assertThat(lastSelected.get().id()).isEqualTo("t1");

        model.clearSelection();
        assertThat(lastSelected.get()).isNull();
    }

    @Test
    void selectTask_unknownId_stillSelected() {
        // Selecting an ID not in the task list should still add to selectedIds
        model.selectTask("unknown");
        assertThat(model.getSelectedIds()).containsExactly("unknown");
        // But getSelectedTask should return null since the task isn't in the list
        assertThat(model.getSelectedTask()).isNull();
    }
}
