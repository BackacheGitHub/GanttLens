package com.ganttlens.analyzer;

import com.ganttlens.model.CriticalPathResult;
import com.ganttlens.model.GanttSchedule;
import com.ganttlens.model.Task;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Analyzes the critical path in a project schedule.
 * The critical path is the longest sequence of dependent tasks,
 * determining the minimum project duration.
 */
public class CriticalPathAnalyzer {

    /**
     * Computes the critical path for the given schedule.
     */
    public CriticalPathResult analyze(GanttSchedule schedule) {
        List<Task> tasks = schedule.tasks();
        if (tasks.isEmpty()) {
            return new CriticalPathResult(List.of(), 0, Map.of());
        }

        // Build task map by name
        Map<String, Task> taskByName = new LinkedHashMap<>();
        for (Task task : tasks) {
            taskByName.put(task.name(), task);
        }

        // Compute earliest start (ES) and earliest finish (EF)
        Map<String, Integer> es = new LinkedHashMap<>();
        Map<String, Integer> ef = new LinkedHashMap<>();
        Map<String, Integer> taskIndex = new LinkedHashMap<>();
        for (int i = 0; i < tasks.size(); i++) {
            taskIndex.put(tasks.get(i).name(), i);
        }

        // Topological sort using Kahn's algorithm
        List<String> topoOrder = topologicalSort(tasks, taskByName);

        // Forward pass - compute ES and EF
        for (String taskName : topoOrder) {
            Task task = taskByName.get(taskName);
            int maxDepEf = 0;
            for (String dep : task.dependencyIds()) {
                if (ef.containsKey(dep)) {
                    maxDepEf = Math.max(maxDepEf, ef.get(dep));
                }
            }
            es.put(taskName, maxDepEf);
            ef.put(taskName, maxDepEf + task.durationDays());
        }

        // Compute project duration (max EF)
        int projectDuration = ef.values().stream()
            .mapToInt(Integer::intValue)
            .max()
            .orElse(0);

        // Backward pass - compute LF and LS
        Map<String, Integer> lf = new LinkedHashMap<>();
        Map<String, Integer> ls = new LinkedHashMap<>();

        // Initialize LF for tasks with no successors to project duration
        List<String> reversedTopo = new ArrayList<>(topoOrder);
        Collections.reverse(reversedTopo);

        for (String taskName : reversedTopo) {
            Task task = taskByName.get(taskName);
            // Find successors
            int minLs = projectDuration;
            boolean hasSuccessor = false;
            for (Task other : tasks) {
                if (other.dependencyIds().contains(taskName)) {
                    hasSuccessor = true;
                    minLs = Math.min(minLs, ls.getOrDefault(other.name(), projectDuration));
                }
            }
            if (!hasSuccessor) {
                lf.put(taskName, projectDuration);
            } else {
                lf.put(taskName, minLs);
            }
            ls.put(taskName, lf.get(taskName) - task.durationDays());
        }

        // Compute float for each task and identify critical tasks
        Map<String, Integer> taskFloats = new LinkedHashMap<>();
        List<String> criticalTaskIds = new ArrayList<>();

        for (Task task : tasks) {
            int float_val = ls.get(task.name()) - es.get(task.name());
            taskFloats.put(task.name(), float_val);
            if (float_val == 0) {
                criticalTaskIds.add(task.id());
            }
        }

        return new CriticalPathResult(criticalTaskIds, projectDuration, taskFloats);
    }

    /**
     * Performs topological sort on tasks based on dependencies.
     */
    private List<String> topologicalSort(List<Task> tasks, Map<String, Task> taskByName) {
        // Build adjacency list and in-degree count
        Map<String, List<String>> graph = new LinkedHashMap<>();
        Map<String, Integer> inDegree = new LinkedHashMap<>();

        for (Task task : tasks) {
            graph.put(task.name(), new ArrayList<>());
            inDegree.put(task.name(), 0);
        }

        for (Task task : tasks) {
            for (String dep : task.dependencyIds()) {
                if (taskByName.containsKey(dep)) {
                    graph.get(dep).add(task.name());
                    inDegree.put(task.name(), inDegree.get(task.name()) + 1);
                }
            }
        }

        // Kahn's algorithm
        Queue<String> queue = new LinkedList<>();
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        List<String> result = new ArrayList<>();
        while (!queue.isEmpty()) {
            String current = queue.poll();
            result.add(current);
            for (String neighbor : graph.get(current)) {
                int newDegree = inDegree.get(neighbor) - 1;
                inDegree.put(neighbor, newDegree);
                if (newDegree == 0) {
                    queue.add(neighbor);
                }
            }
        }

        // Handle circular dependencies (add remaining tasks)
        if (result.size() < tasks.size()) {
            for (Task task : tasks) {
                if (!result.contains(task.name())) {
                    result.add(task.name());
                }
            }
        }

        return result;
    }
}
