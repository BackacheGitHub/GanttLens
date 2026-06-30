package com.ganttlens.model;

import java.util.List;
import java.util.Map;

/**
 * Result of critical path analysis.
 */
public record CriticalPathResult(
    List<String> criticalTaskIds,
    int totalDurationDays,
    Map<String, Integer> taskFloats
) {}
