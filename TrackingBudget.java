package dev.leadshot;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Select a bounded tracking set from entities already sorted by distance.
 * Keep a live, explicitly selected target even when it is beyond the nearest-N
 * set in a dense mob farm. Do not increase the fixed work budget.
 */
public final class TrackingBudget {
    private TrackingBudget() {}

    public static <T> List<T> choose(List<T> sorted, Predicate<T> selected, int limit) {
        if (sorted == null || selected == null || limit <= 0) return List.of();
        int end = Math.min(limit, sorted.size());
        if (end == 0) return List.of();
        List<T> result = new ArrayList<>(sorted.subList(0, end));
        for (T current : result) if (selected.test(current)) return result;
        // This preserves the nearest limit-1 candidates plus the pinned target.
        for (int i = end; i < sorted.size(); i++) {
            T candidate = sorted.get(i);
            if (selected.test(candidate)) {
                result.set(end - 1, candidate);
                break;
            }
        }
        return result;
    }
}
