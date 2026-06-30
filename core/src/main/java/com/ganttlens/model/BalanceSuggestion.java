package com.ganttlens.model;

import java.time.LocalDate;

/**
 * A suggestion for balancing resource overload.
 */
public record BalanceSuggestion(
    String person,
    String suggestTask,
    LocalDate suggestStart,
    String reason
) {}
