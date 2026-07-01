package com.ganttlens.model;

/**
 * Command pattern interface for undoable operations on the task list.
 * Pure Java — no JavaFX dependency.
 */
public interface Command {

    /**
     * Executes the command, applying the change to the task list.
     */
    void execute();

    /**
     * Reverses the command, restoring the previous state.
     */
    void undo();

    /**
     * Returns a human-readable description of this command.
     */
    String description();
}
