package com.ganttlens.analyzer;

import com.ganttlens.model.Assignment;
import com.ganttlens.model.GanttSchedule;
import com.ganttlens.model.PersonDailyLoad;
import com.ganttlens.model.ScheduleConfig;
import com.ganttlens.model.Task;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Analyzes workload distribution across people and tasks.
 */
public class WorkloadAnalyzer {

    /**
     * Computes daily load for each person based on the schedule.
     */
    public List<PersonDailyLoad> analyzeDailyLoads(GanttSchedule schedule) {
        // Build a quick-lookup set of person-off days
        Set<ScheduleConfig.PersonOffEntry> personOffDays = schedule.config().personOffDays();

        Map<String, Map<LocalDate, List<PersonDailyLoad.TaskLoad>>> personDailyMap = new LinkedHashMap<>();

        for (Task task : schedule.tasks()) {
            if (task.startDate() == null || task.endDate() == null) continue;

            for (Assignment assignment : task.assignments()) {
                String person = assignment.person();
                personDailyMap
                    .computeIfAbsent(person, k -> new LinkedHashMap<>());

                LocalDate current = task.startDate();
                while (!current.isAfter(task.endDate())) {
                    // Skip person-off days
                    if (!personOffDays.isEmpty() &&
                        personOffDays.contains(new ScheduleConfig.PersonOffEntry(person, current))) {
                        current = current.plusDays(1);
                        continue;
                    }
                    personDailyMap.get(person)
                        .computeIfAbsent(current, k -> new ArrayList<>())
                        .add(new PersonDailyLoad.TaskLoad(
                            task.id(),
                            task.name(),
                            assignment.ratio()
                        ));
                    current = current.plusDays(1);
                }
            }
        }

        List<PersonDailyLoad> result = new ArrayList<>();
        for (Map.Entry<String, Map<LocalDate, List<PersonDailyLoad.TaskLoad>>> personEntry : personDailyMap.entrySet()) {
            String person = personEntry.getKey();
            for (Map.Entry<LocalDate, List<PersonDailyLoad.TaskLoad>> dayEntry : personEntry.getValue().entrySet()) {
                LocalDate date = dayEntry.getKey();
                List<PersonDailyLoad.TaskLoad> tasks = dayEntry.getValue();
                double totalLoad = tasks.stream()
                    .mapToDouble(PersonDailyLoad.TaskLoad::ratio)
                    .sum();
                result.add(new PersonDailyLoad(person, date, totalLoad, tasks));
            }
        }

        return result.stream()
            .sorted(Comparator.comparing(PersonDailyLoad::person)
                .thenComparing(PersonDailyLoad::date))
            .collect(Collectors.toList());
    }

    /**
     * Computes total man-days per person.
     */
    public Map<String, Double> computePersonManDays(GanttSchedule schedule) {
        Map<String, Double> result = new LinkedHashMap<>();
        for (Task task : schedule.tasks()) {
            for (Assignment assignment : task.assignments()) {
                result.merge(assignment.person(), (double) task.durationDays() * assignment.ratio(), Double::sum);
            }
        }
        return result;
    }
}
