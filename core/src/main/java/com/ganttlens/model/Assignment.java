package com.ganttlens.model;

/**
 * Represents a person assigned to a task with a specific load ratio.
 */
public record Assignment(
    String person,
    double ratio
) {
    public Assignment {
        if (ratio < 0.0 || ratio > 1.0) {
            throw new IllegalArgumentException("Ratio must be between 0.0 and 1.0, got: " + ratio);
        }
    }

    /**
     * Creates an assignment with 100% load (ratio = 1.0).
     */
    public static Assignment fullLoad(String person) {
        return new Assignment(person, 1.0);
    }

    /**
     * Creates an assignment with a percentage load (e.g. 50 -> 0.5).
     */
    public static Assignment withPercent(String person, int percent) {
        return new Assignment(person, percent / 100.0);
    }
}
