package com.ganttlens.analyzer;

import com.ganttlens.model.GanttSchedule;
import com.ganttlens.model.OverloadRecord;
import com.ganttlens.model.PersonDailyLoad;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Checks for overload situations where a person's daily load exceeds 100%.
 */
public class OverloadChecker {

    private final WorkloadAnalyzer workloadAnalyzer;

    public OverloadChecker(WorkloadAnalyzer workloadAnalyzer) {
        this.workloadAnalyzer = workloadAnalyzer;
    }

    /**
     * Detects all overload records from the schedule.
     */
    public List<OverloadRecord> check(GanttSchedule schedule) {
        List<PersonDailyLoad> dailyLoads = workloadAnalyzer.analyzeDailyLoads(schedule);

        return dailyLoads.stream()
            .filter(load -> load.totalLoad() > 1.0)
            .map(load -> new OverloadRecord(
                load.person(),
                load.date(),
                load.totalLoad(),
                load.tasks()
            ))
            .sorted((a, b) -> {
                int cmp = a.date().compareTo(b.date());
                return cmp != 0 ? cmp : a.person().compareTo(b.person());
            })
            .collect(Collectors.toList());
    }
}
