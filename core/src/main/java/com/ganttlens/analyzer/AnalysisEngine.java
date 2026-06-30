package com.ganttlens.analyzer;

import com.ganttlens.model.*;

import java.util.List;
import java.util.Map;

/**
 * Main analysis engine that orchestrates workload analysis, overload checking,
 * critical path analysis, and resource balancing.
 */
public class AnalysisEngine {

    private final WorkloadAnalyzer workloadAnalyzer;
    private final OverloadChecker overloadChecker;
    private final CriticalPathAnalyzer criticalPathAnalyzer;
    private final ResourceBalancer resourceBalancer;

    public AnalysisEngine() {
        this.workloadAnalyzer = new WorkloadAnalyzer();
        this.overloadChecker = new OverloadChecker(workloadAnalyzer);
        this.criticalPathAnalyzer = new CriticalPathAnalyzer();
        this.resourceBalancer = new ResourceBalancer();
    }

    /**
     * Analyzes the schedule and produces project statistics.
     */
    public ProjectStats analyze(GanttSchedule schedule) {
        Map<String, Double> personManDays = workloadAnalyzer.computePersonManDays(schedule);
        double totalManDays = personManDays.values().stream()
            .mapToDouble(Double::doubleValue)
            .sum();

        CriticalPathResult criticalPath = criticalPathAnalyzer.analyze(schedule);
        List<OverloadRecord> overloads = overloadChecker.check(schedule);
        List<BalanceSuggestion> suggestions = resourceBalancer.balance(schedule, overloads);

        return new ProjectStats(
            totalManDays,
            personManDays,
            workloadAnalyzer.analyzeDailyLoads(schedule),
            overloads,
            schedule.tasks(),
            criticalPath,
            suggestions
        );
    }
}
