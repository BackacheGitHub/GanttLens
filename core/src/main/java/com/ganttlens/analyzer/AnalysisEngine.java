package com.ganttlens.analyzer;

import com.ganttlens.model.GanttSchedule;
import com.ganttlens.model.ProjectStats;

import java.util.Map;

/**
 * Main analysis engine that orchestrates workload analysis and overload checking.
 */
public class AnalysisEngine {

    private final WorkloadAnalyzer workloadAnalyzer;
    private final OverloadChecker overloadChecker;

    public AnalysisEngine() {
        this.workloadAnalyzer = new WorkloadAnalyzer();
        this.overloadChecker = new OverloadChecker(workloadAnalyzer);
    }

    /**
     * Analyzes the schedule and produces project statistics.
     */
    public ProjectStats analyze(GanttSchedule schedule) {
        Map<String, Double> personManDays = workloadAnalyzer.computePersonManDays(schedule);
        double totalManDays = personManDays.values().stream()
            .mapToDouble(Double::doubleValue)
            .sum();

        return new ProjectStats(
            totalManDays,
            personManDays,
            workloadAnalyzer.analyzeDailyLoads(schedule),
            overloadChecker.check(schedule),
            schedule.tasks()
        );
    }
}
