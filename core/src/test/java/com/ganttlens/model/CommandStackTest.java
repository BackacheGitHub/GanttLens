package com.ganttlens.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for CommandStack and EditTaskCommand — pure Java.
 */
class CommandStackTest {

    private CommandStack stack;
    private ArrayList<Task> taskList;
    private Task task1;
    private Task task2;

    @BeforeEach
    void setUp() {
        stack = new CommandStack();
        task1 = new Task("t1", "Task A", null,
            LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 5), 5,
            List.of(), List.of(), TaskStatus.PENDING, null);
        taskList = new ArrayList<>(List.of(task1));
    }

    @Test
    void push_executesCommand() {
        Task modified = new Task("t1", "Task A Modified", null,
            task1.startDate(), task1.endDate(), task1.durationDays(),
            task1.assignments(), task1.dependencyIds(), task1.status(), task1.color());

        EditTaskCommand cmd = new EditTaskCommand(taskList, task1, modified, "Rename");
        stack.push(cmd);

        assertThat(taskList.get(0).name()).isEqualTo("Task A Modified");
    }

    @Test
    void push_setsCanUndoTrue() {
        Task modified = new Task("t1", "Modified", null,
            task1.startDate(), task1.endDate(), task1.durationDays(),
            task1.assignments(), task1.dependencyIds(), task1.status(), task1.color());

        stack.push(new EditTaskCommand(taskList, task1, modified, "Edit"));

        assertThat(stack.canUndo()).isTrue();
        assertThat(stack.canRedo()).isFalse();
    }

    @Test
    void undo_revertsCommand() {
        Task modified = new Task("t1", "Modified", null,
            task1.startDate(), task1.endDate(), task1.durationDays(),
            task1.assignments(), task1.dependencyIds(), task1.status(), task1.color());

        stack.push(new EditTaskCommand(taskList, task1, modified, "Edit"));
        assertThat(taskList.get(0).name()).isEqualTo("Modified");

        stack.undo();
        assertThat(taskList.get(0).name()).isEqualTo("Task A");
    }

    @Test
    void undo_afterPush_setsCanRedoTrue() {
        Task modified = new Task("t1", "Modified", null,
            task1.startDate(), task1.endDate(), task1.durationDays(),
            task1.assignments(), task1.dependencyIds(), task1.status(), task1.color());

        stack.push(new EditTaskCommand(taskList, task1, modified, "Edit"));
        stack.undo();

        assertThat(stack.canRedo()).isTrue();
    }

    @Test
    void redo_reappliesCommand() {
        Task modified = new Task("t1", "Modified", null,
            task1.startDate(), task1.endDate(), task1.durationDays(),
            task1.assignments(), task1.dependencyIds(), task1.status(), task1.color());

        stack.push(new EditTaskCommand(taskList, task1, modified, "Edit"));
        stack.undo();
        stack.redo();

        assertThat(taskList.get(0).name()).isEqualTo("Modified");
    }

    @Test
    void push_afterUndo_clearsRedoStack() {
        Task modified1 = new Task("t1", "V1", null,
            task1.startDate(), task1.endDate(), task1.durationDays(),
            task1.assignments(), task1.dependencyIds(), task1.status(), task1.color());
        Task modified2 = new Task("t1", "V2", null,
            task1.startDate(), task1.endDate(), task1.durationDays(),
            task1.assignments(), task1.dependencyIds(), task1.status(), task1.color());

        stack.push(new EditTaskCommand(taskList, task1, modified1, "V1"));
        stack.undo();
        assertThat(stack.canRedo()).isTrue();

        stack.push(new EditTaskCommand(taskList, task1, modified2, "V2"));
        assertThat(stack.canRedo()).isFalse();
    }

    @Test
    void undo_emptyStack_returnsFalse() {
        assertThat(stack.undo()).isFalse();
    }

    @Test
    void redo_emptyStack_returnsFalse() {
        assertThat(stack.redo()).isFalse();
    }

    @Test
    void clear_emptiesBothStacks() {
        Task modified = new Task("t1", "Modified", null,
            task1.startDate(), task1.endDate(), task1.durationDays(),
            task1.assignments(), task1.dependencyIds(), task1.status(), task1.color());

        stack.push(new EditTaskCommand(taskList, task1, modified, "Edit"));
        stack.clear();

        assertThat(stack.canUndo()).isFalse();
        assertThat(stack.canRedo()).isFalse();
        assertThat(stack.undoSize()).isEqualTo(0);
        assertThat(stack.redoSize()).isEqualTo(0);
    }

    @Test
    void peekUndoDescription_returnsDescription() {
        Task modified = new Task("t1", "Modified", null,
            task1.startDate(), task1.endDate(), task1.durationDays(),
            task1.assignments(), task1.dependencyIds(), task1.status(), task1.color());

        stack.push(new EditTaskCommand(taskList, task1, modified, "Edit task name"));

        assertThat(stack.peekUndoDescription()).isEqualTo("Edit task name");
    }

    @Test
    void peekRedoDescription_returnsDescriptionAfterUndo() {
        Task modified = new Task("t1", "Modified", null,
            task1.startDate(), task1.endDate(), task1.durationDays(),
            task1.assignments(), task1.dependencyIds(), task1.status(), task1.color());

        stack.push(new EditTaskCommand(taskList, task1, modified, "Edit task name"));
        stack.undo();

        assertThat(stack.peekRedoDescription()).isEqualTo("Edit task name");
    }

    @Test
    void onStateChanged_callbackFires() {
        final int[] callCount = {0};
        stack.setOnStateChanged(s -> callCount[0]++);

        Task modified = new Task("t1", "Modified", null,
            task1.startDate(), task1.endDate(), task1.durationDays(),
            task1.assignments(), task1.dependencyIds(), task1.status(), task1.color());

        stack.push(new EditTaskCommand(taskList, task1, modified, "Edit"));
        assertThat(callCount[0]).isEqualTo(1);

        stack.undo();
        assertThat(callCount[0]).isEqualTo(2);

        stack.redo();
        assertThat(callCount[0]).isEqualTo(3);
    }

    @Test
    void multipleUndoRedo_sequenceCorrect() {
        Task v1 = new Task("t1", "V1", null, task1.startDate(), task1.endDate(), 5, List.of(), List.of(), TaskStatus.PENDING, null);
        Task v2 = new Task("t1", "V2", null, task1.startDate(), task1.endDate(), 5, List.of(), List.of(), TaskStatus.IN_PROGRESS, null);
        Task v3 = new Task("t1", "V3", null, task1.startDate(), task1.endDate(), 5, List.of(), List.of(), TaskStatus.COMPLETED, null);

        stack.push(new EditTaskCommand(taskList, task1, v1, "V1"));
        stack.push(new EditTaskCommand(taskList, v1, v2, "V2"));
        stack.push(new EditTaskCommand(taskList, v2, v3, "V3"));

        assertThat(taskList.get(0).name()).isEqualTo("V3");

        stack.undo();
        assertThat(taskList.get(0).name()).isEqualTo("V2");

        stack.undo();
        assertThat(taskList.get(0).name()).isEqualTo("V1");

        stack.undo();
        assertThat(taskList.get(0).name()).isEqualTo("Task A");

        stack.redo();
        assertThat(taskList.get(0).name()).isEqualTo("V1");

        stack.redo();
        assertThat(taskList.get(0).name()).isEqualTo("V2");
    }
}
