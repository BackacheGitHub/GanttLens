package com.ganttlens.model;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;

/**
 * Manages an undo/redo stack of Commands.
 * Pure Java — no JavaFX dependency.
 */
public class CommandStack {

    private final Deque<Command> undoStack = new ArrayDeque<>();
    private final Deque<Command> redoStack = new ArrayDeque<>();
    private final int maxStackSize;
    private Consumer<CommandStack> onStateChanged;

    public CommandStack() {
        this(100);
    }

    public CommandStack(int maxStackSize) {
        this.maxStackSize = maxStackSize;
    }

    /**
     * Sets a callback that fires whenever the stack state changes
     * (push, undo, redo, clear).
     */
    public void setOnStateChanged(Consumer<CommandStack> callback) {
        this.onStateChanged = callback;
    }

    /**
     * Executes a command and pushes it onto the undo stack.
     * Clears the redo stack.
     */
    public void push(Command command) {
        command.execute();
        undoStack.push(command);
        redoStack.clear();

        // Trim if over max size
        while (undoStack.size() > maxStackSize) {
            // Remove oldest (bottom of stack)
            ArrayDeque<Command> temp = new ArrayDeque<>(undoStack);
            temp.pollLast(); // remove oldest
            undoStack.clear();
            undoStack.addAll(temp);
        }

        notifyStateChanged();
    }

    /**
     * Undoes the most recent command.
     *
     * @return true if undo was performed, false if stack is empty
     */
    public boolean undo() {
        if (undoStack.isEmpty()) return false;

        Command cmd = undoStack.pop();
        cmd.undo();
        redoStack.push(cmd);

        notifyStateChanged();
        return true;
    }

    /**
     * Redoes the most recently undone command.
     *
     * @return true if redo was performed, false if stack is empty
     */
    public boolean redo() {
        if (redoStack.isEmpty()) return false;

        Command cmd = redoStack.pop();
        cmd.execute();
        undoStack.push(cmd);

        notifyStateChanged();
        return true;
    }

    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    /**
     * Returns the description of the next command to undo, or null.
     */
    public String peekUndoDescription() {
        Command cmd = undoStack.peek();
        return cmd != null ? cmd.description() : null;
    }

    /**
     * Returns the description of the next command to redo, or null.
     */
    public String peekRedoDescription() {
        Command cmd = redoStack.peek();
        return cmd != null ? cmd.description() : null;
    }

    /**
     * Clears both undo and redo stacks.
     */
    public void clear() {
        undoStack.clear();
        redoStack.clear();
        notifyStateChanged();
    }

    public int undoSize() {
        return undoStack.size();
    }

    public int redoSize() {
        return redoStack.size();
    }

    private void notifyStateChanged() {
        if (onStateChanged != null) {
            onStateChanged.accept(this);
        }
    }
}
