package io.github.rkaushikdev.stemwright.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * The grouping shapes that kept being written out by hand.
 *
 * <p>Every map returned here keeps its keys in the order they were first seen, and every bucket
 * keeps its items in the order they arrived unless the caller asked for them sorted. That ordering
 * is part of the contract: a report built from a group comes out the same way every time.
 */
public final class Grouping {

    private Grouping() {}

    public static <T, K> Map<K, List<T>> groupBy(Iterable<? extends T> items, Function<? super T, ? extends K> key) {
        Map<K, List<T>> groups = new LinkedHashMap<>();
        for (T item : items) {
            groups.computeIfAbsent(key.apply(item), ignored -> new ArrayList<>())
                    .add(item);
        }
        return groups;
    }

    /** Groups and sorts each bucket, which is what every index in the domain wants. */
    public static <T, K> Map<K, List<T>> groupSorted(
            Iterable<? extends T> items, Function<? super T, ? extends K> key, Comparator<? super T> compare) {
        Map<K, List<T>> groups = groupBy(items, key);
        for (List<T> bucket : groups.values()) {
            bucket.sort(compare);
        }
        return groups;
    }

    public static <T, K> Map<K, Integer> countBy(Iterable<? extends T> items, Function<? super T, ? extends K> key) {
        Map<K, Integer> counts = new LinkedHashMap<>();
        for (T item : items) {
            counts.merge(key.apply(item), 1, Integer::sum);
        }
        return counts;
    }

    /** Keeps the first item seen for each key. */
    public static <T, K> List<T> distinct(Iterable<? extends T> items, Function<? super T, ? extends K> key) {
        Set<K> seen = new HashSet<>();
        List<T> out = new ArrayList<>();
        for (T item : items) {
            if (seen.add(key.apply(item))) {
                out.add(item);
            }
        }
        return out;
    }

    /** Keys each item by the function, a later item winning a repeated key. */
    public static <T, K> Map<K, T> indexBy(Iterable<? extends T> items, Function<? super T, ? extends K> key) {
        Map<K, T> index = new LinkedHashMap<>();
        for (T item : items) {
            index.put(key.apply(item), item);
        }
        return index;
    }
}
