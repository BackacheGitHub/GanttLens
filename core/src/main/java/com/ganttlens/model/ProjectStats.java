package com.ganttlens.model;

import java.util.List;
import java.util.Map;

/**
 * Aggregated analysis results for a project schedule.
 */
public record ProjectStats(
    double totalManDays,
    Map<String, Double> personManDays,
    List<PersonDailyLoad> dailyLoads,
    List<OverloadRecord> overloads,
    List<Task> tasks,
    CriticalPathResult criticalPath,
    List<BalanceSuggestion> balanceSuggestions
) {}
