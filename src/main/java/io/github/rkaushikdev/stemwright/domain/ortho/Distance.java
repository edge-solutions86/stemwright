package io.github.rkaushikdev.stemwright.domain.ortho;

import io.github.rkaushikdev.stemwright.domain.text.Normalise;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * How far apart two written forms are.
 *
 * <p>Distances are counted over mark clusters rather than code points. A Greek vowel carrying a
 * breathing and an accent is one thing a learner typed wrong, not three, and counting it as three
 * makes every accented word look further from the answer than it is.
 */
public final class Distance {

    private Distance() {}

    /** The edit distance over mark clusters, so an accented vowel counts as one unit. */
    public static int clusterDistance(String a, String b) {
        return levenshtein(Normalise.markClusters(a), Normalise.markClusters(b));
    }

    /** The edit distance over code points, as the text is written. */
    public static int pointDistance(String a, String b) {
        return levenshtein(Normalise.codePoints(a), Normalise.codePoints(b));
    }

    private static int levenshtein(List<String> left, List<String> right) {
        if (left.isEmpty()) {
            return right.size();
        }
        if (right.isEmpty()) {
            return left.size();
        }

        int[] previous = new int[right.size() + 1];
        for (int j = 0; j <= right.size(); j += 1) {
            previous[j] = j;
        }
        for (int i = 1; i <= left.size(); i += 1) {
            int[] current = new int[right.size() + 1];
            current[0] = i;
            for (int j = 1; j <= right.size(); j += 1) {
                int substitution = previous[j - 1] + (left.get(i - 1).equals(right.get(j - 1)) ? 0 : 1);
                int deletion = previous[j] + 1;
                int insertion = current[j - 1] + 1;
                current[j] = Math.min(substitution, Math.min(deletion, insertion));
            }
            previous = current;
        }
        return previous[right.size()];
    }

    /** True when the two differ only by swapping one adjacent pair of clusters. */
    public static boolean isTransposition(String a, String b) {
        List<String> left = Normalise.markClusters(a);
        List<String> right = Normalise.markClusters(b);
        if (left.size() != right.size() || left.size() < 2) {
            return false;
        }
        List<Integer> differing = new ArrayList<>();
        for (int index = 0; index < left.size(); index += 1) {
            if (!left.get(index).equals(right.get(index))) {
                differing.add(index);
            }
        }
        if (differing.size() != 2) {
            return false;
        }
        int first = differing.get(0);
        int second = differing.get(1);
        return second == first + 1
                && left.get(first).equals(right.get(second))
                && left.get(second).equals(right.get(first));
    }

    /** The first cluster where the two forms part company, or nothing when they agree throughout. */
    public static Optional<Divergence> firstDivergence(String expected, String given) {
        List<String> left = Normalise.markClusters(expected);
        List<String> right = Normalise.markClusters(given);
        int limit = Math.max(left.size(), right.size());
        for (int index = 0; index < limit; index += 1) {
            Optional<String> wanted = clusterAt(left, index);
            Optional<String> typed = clusterAt(right, index);
            if (!wanted.equals(typed)) {
                return Optional.of(new Divergence(index, wanted, typed));
            }
        }
        return Optional.empty();
    }

    private static Optional<String> clusterAt(List<String> clusters, int index) {
        return index < clusters.size() ? Optional.of(clusters.get(index)) : Optional.empty();
    }

    /** How many clusters the two forms share from the left. */
    public static int sharedPrefixLength(String a, String b) {
        List<String> left = Normalise.markClusters(a);
        List<String> right = Normalise.markClusters(b);
        int shared = 0;
        while (shared < left.size() && shared < right.size() && left.get(shared).equals(right.get(shared))) {
            shared += 1;
        }
        return shared;
    }

    /** How many clusters the two forms share from the right. */
    public static int sharedSuffixLength(String a, String b) {
        List<String> left = Normalise.markClusters(a);
        List<String> right = Normalise.markClusters(b);
        int shared = 0;
        while (shared < left.size()
                && shared < right.size()
                && left.get(left.size() - 1 - shared).equals(right.get(right.size() - 1 - shared))) {
            shared += 1;
        }
        return shared;
    }

    /**
     * A similarity in nought to one, used to rank candidates when a learner's answer matches no cell
     * exactly and the drill wants to say what they probably meant. Identical strings score one and
     * strings sharing nothing score zero.
     */
    public static double similarity(String a, String b) {
        int longest = Math.max(
                Normalise.markClusters(a).size(), Normalise.markClusters(b).size());
        if (longest == 0) {
            return 1;
        }
        return 1 - (double) clusterDistance(a, b) / longest;
    }
}
