package com.ganttlens.model;

import java.util.ArrayList;

/**
 * A command that replaces one Task with another in a mutable task list.
 * Used for all property edits (name, dates, progress, etc.).
 * Pure Java — no JavaFX dependency.
 */
public class EditTaskCommand implements Command {

    private final ArrayList<Task> taskList;
    private final Task oldTask;
    private final Task newTask;
    private final String description;

    /**
     * @param taskList  the mutable task list to operate on
     * @param oldTask   the task before the edit
     * @param newTask   the task after the edit
     * @param description  human-readable description (e.g. "Edit task name")
     */
    public EditTaskCommand(ArrayList<Task> taskList, Task oldTask, Task newTask, String description) {
        this.taskList = taskList;
        this.oldTask = oldTask;
        this.newTask = newTask;
        this.description = description;
    }

    @Override
    public void execute() {
        int index = taskList.indexOf(oldTask);
        if (index >= 0) {
            taskList.set(index, newTask);
        }
    }

    @Override
    public void undo() {
        int index = taskList.indexOf(newTask);
        if (index >= 0) {
            taskList.set(index, oldTask);
        }
    }

    @Override
    public String description() {
        return description;
    }

    public Task getOldTask() {
        return oldTask;
    }

    public Task getNewTask() {
        return newTask;
    }
}
