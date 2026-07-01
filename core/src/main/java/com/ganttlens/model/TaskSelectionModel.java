package com.ganttlens.model;

import java.util.*;
import java.util.function.Consumer;

/**
 * Manages task selection state for the Gantt chart.
 * Supports single-select and multi-select modes.
 * Pure Java — no JavaFX dependency.
 */
public class TaskSelectionModel {

    private final Set<String> selectedIds = new LinkedHashSet<>();
    private List<Task> allTasks = List.of();
    private Consumer<Task> onSelectionChanged;

    public void setTasks(List<Task> tasks) {
        this.allTasks = tasks != null ? List.copyOf(tasks) : List.of();
    }

    /**
     * Sets a callback that fires whenever the selection changes.
     */
    public void setOnSelectionChanged(Consumer<Task> callback) {
        this.onSelectionChanged = callback;
    }

    /**
     * Selects a single task (clears previous selection).
     */
    public void selectTask(String taskId) {
        selectedIds.clear();
        if (taskId != null) {
            selectedIds.add(taskId);
        }
        notifySelectionChanged();
    }

    /**
     * Adds a task to the selection (multi-select).
     */
    public void addToSelection(String taskId) {
        if (taskId != null) {
            selectedIds.add(taskId);
            notifySelectionChanged();
        }
    }

    /**
     * Removes a task from the selection.
     */
    public void deselectTask(String taskId) {
        selectedIds.remove(taskId);
        notifySelectionChanged();
    }

    /**
     * Clears all selections.
     */
    public void clearSelection() {
        selectedIds.clear();
        notifySelectionChanged();
    }

    /**
     * Returns the set of currently selected task IDs.
     */
    public Set<String> getSelectedIds() {
        return Collections.unmodifiableSet(selectedIds);
    }

    /**
     * Returns true if the given task is selected.
     */
    public boolean isSelected(String taskId) {
        return selectedIds.contains(taskId);
    }

    /**
     * Returns the first selected task, or null if none selected.
     */
    public Task getSelectedTask() {
        if (selectedIds.isEmpty()) return null;
        String firstId = selectedIds.iterator().next();
        return allTasks.stream()
            .filter(t -> t.id().equals(firstId))
            .findFirst()
            .orElse(null);
    }

    /**
     * Returns all selected tasks.
     */
    public List<Task> getSelectedTasks() {
        return allTasks.stream()
            .filter(t -> selectedIds.contains(t.id()))
            .toList();
    }

    /**
     * Returns true if any task is selected.
     */
    public boolean hasSelection() {
        return !selectedIds.isEmpty();
    }

    private void notifySelectionChanged() {
        if (onSelectionChanged != null) {
            onSelectionChanged.accept(getSelectedTask());
        }
    }
}
