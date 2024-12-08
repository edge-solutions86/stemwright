/**
 * The two grouping shapes that kept being written out by hand. Five modules had
 * their own copy of "push into a map of arrays", and two of them differed in
 * whether the buckets came back sorted, which is exactly the kind of difference
 * that shows up as an ordering bug in a report months later.
 */
export function groupBy<T>(items: Iterable<T>, key: (item: T) => string): Map<string, T[]> {
  const groups = new Map<string, T[]>();
  for (const item of items) {
    const bucket = key(item);
    const existing = groups.get(bucket);
    if (existing === undefined) {
      groups.set(bucket, [item]);
    } else {
      existing.push(item);
    }
  }
  return groups;
}

/** Groups and sorts each bucket, which is what every index in the domain wants. */
export function groupSorted<T>(
  items: Iterable<T>,
  key: (item: T) => string,
  compare: (a: T, b: T) => number,
): Map<string, T[]> {
  const groups = groupBy(items, key);
  for (const bucket of groups.values()) {
    bucket.sort(compare);
  }
  return groups;
}

export function countBy<T>(items: Iterable<T>, key: (item: T) => string): Map<string, number> {
  const counts = new Map<string, number>();
  for (const item of items) {
    const bucket = key(item);
    counts.set(bucket, (counts.get(bucket) ?? 0) + 1);
  }
  return counts;
}

export function distinct<T>(items: Iterable<T>, key: (item: T) => string): T[] {
  const seen = new Set<string>();
  const out: T[] = [];
  for (const item of items) {
    const bucket = key(item);
    if (!seen.has(bucket)) {
      seen.add(bucket);
      out.push(item);
    }
  }
  return out;
}

export function indexBy<T>(items: Iterable<T>, key: (item: T) => string): Map<string, T> {
  const index = new Map<string, T>();
  for (const item of items) {
    index.set(key(item), item);
  }
  return index;
}
