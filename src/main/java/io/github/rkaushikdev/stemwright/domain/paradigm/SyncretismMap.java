package io.github.rkaushikdev.stemwright.domain.paradigm;

import io.github.rkaushikdev.stemwright.domain.Grouping;
import io.github.rkaushikdev.stemwright.domain.Identifiers;
import io.github.rkaushikdev.stemwright.domain.errors.ProblemCollector;
import io.github.rkaushikdev.stemwright.domain.features.FeatureBundle;
import io.github.rkaushikdev.stemwright.domain.features.FeatureSchema;
import io.github.rkaushikdev.stemwright.domain.text.Normalise;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Syncretism is two grammatical cells wearing the same clothes. Latin {@code puellae} is genitive
 * singular, dative singular and nominative plural, and a learner who types it has answered all
 * three whichever one was asked.
 *
 * <p>The map is declared rather than inferred, because the two are not the same question. What the
 * profile happens to produce is an accident of the endings. What the language treats as one form is
 * a claim about the grammar, and a teacher may want a drill to insist on the distinction even where
 * the surface forms coincide. {@link #reconcileWith} reports where the two disagree so an editor can
 * decide which one is wrong.
 *
 * @param groups the groups, in the order they were declared
 * @param byBundle the key of the group each member bundle belongs to, by canonical bundle key
 */
public record SyncretismMap(List<SyncretismGroup> groups, Map<String, String> byBundle) {

    /** The map that declares nothing, so every cell stands alone. */
    public static final SyncretismMap NONE = new SyncretismMap(List.of(), Map.of());

    private static final int NOTE_LIMIT = 240;

    public SyncretismMap {
        groups = List.copyOf(groups);
        byBundle = Collections.unmodifiableMap(new LinkedHashMap<>(byBundle));
    }

    /**
     * Checks the declared groups, reporting every repeated group key, unreadable or incomplete
     * member, member listed twice, member another group already holds and group left with fewer than
     * two members at once. A group key or note that is not usable fails on the spot.
     */
    public static SyncretismMap of(FeatureSchema schema, List<SyncretismGroupInput> inputs) {
        ProblemCollector problems = new ProblemCollector();
        List<SyncretismGroup> groups = new ArrayList<>();
        Map<String, String> byBundle = new LinkedHashMap<>();
        Set<String> seenKeys = new HashSet<>();

        for (int index = 0; index < inputs.size(); index += 1) {
            SyncretismGroupInput input = inputs.get(index);
            String path = "groups." + index;
            String key = Identifiers.asSlug(path + ".key", input.key());
            if (!seenKeys.add(key)) {
                problems.add(path, "repeats the group key " + key);
                continue;
            }

            List<String> members = new ArrayList<>();
            for (String member : input.members()) {
                Optional<FeatureBundle> read =
                        problems.absorbFlat(path, () -> ParadigmProfile.readBundle(schema, member, path));
                if (read.isEmpty()) {
                    continue;
                }
                if (!read.get().isComplete(schema)) {
                    problems.add(path, "member does not fix every required dimension: " + member);
                    continue;
                }
                String canonical = read.get().key();
                if (members.contains(canonical)) {
                    problems.add(path, "lists " + canonical + " twice");
                    continue;
                }
                String owner = byBundle.get(canonical);
                if (owner != null) {
                    problems.add(path, "claims " + canonical + " which group " + owner + " already holds");
                    continue;
                }
                members.add(canonical);
            }

            if (members.size() < 2) {
                problems.add(path, "must hold at least two bundles to be a syncretism");
                continue;
            }
            for (String member : members) {
                byBundle.put(member, key);
            }
            groups.add(new SyncretismGroup(
                    key, members, input.note().map(note -> Normalise.requireText(path + ".note", note, NOTE_LIMIT))));
        }

        problems.throwIfAny("syncretism map is not usable");
        return new SyncretismMap(groups, byBundle);
    }

    public Optional<String> groupKeyOf(String bundle) {
        return Optional.ofNullable(byBundle.get(bundle));
    }

    public Optional<SyncretismGroup> groupOf(String bundle) {
        return groupKeyOf(bundle)
                .flatMap(key ->
                        groups.stream().filter(group -> group.key().equals(key)).findFirst());
    }

    /** The other members of the bundle's group, never including the bundle itself. */
    public List<String> twinsOf(String bundle) {
        return groupOf(bundle)
                .map(group -> group.members().stream()
                        .filter(member -> !member.equals(bundle))
                        .toList())
                .orElse(List.of());
    }

    /** True when two different bundles sit in one group. A bundle is not syncretic with itself. */
    public boolean areSyncretic(String first, String second) {
        if (first.equals(second)) {
            return false;
        }
        String key = byBundle.get(first);
        return key != null && key.equals(byBundle.get(second));
    }

    /** Every bundle that sits in some group, sorted. */
    public List<String> syncreticBundles() {
        return byBundle.keySet().stream().sorted().toList();
    }

    /**
     * The syncretism the profile actually produces: two slots that would build the same string from
     * the same stem. This is what the drill would see even if nobody declared anything. The groups
     * are keyed {@code derived-1}, {@code derived-2} and so on, in the order their first slot comes.
     */
    public static SyncretismMap derivedFrom(ParadigmProfile profile) {
        Map<String, List<ParadigmSlot>> buckets =
                Grouping.groupBy(profile.slots().values(), SyncretismMap::recipeOf);

        List<SyncretismGroup> groups = new ArrayList<>();
        Map<String, String> byBundle = new LinkedHashMap<>();
        int index = 0;
        for (List<ParadigmSlot> slots : buckets.values()) {
            if (slots.size() < 2) {
                continue;
            }
            List<String> members = slots.stream().map(ParadigmSlot::bundle).toList();
            index += 1;
            String key = "derived-" + index;
            groups.add(new SyncretismGroup(key, members, Optional.empty()));
            for (String member : members) {
                byBundle.put(member, key);
            }
        }
        return new SyncretismMap(groups, byBundle);
    }

    /** What a slot builds from any one stem: its literal, or its stem role and affix. */
    private static String recipeOf(ParadigmSlot slot) {
        return slot.literal()
                .map(literal -> "literal:" + literal)
                .orElseGet(() -> "affix:" + slot.stemRole() + ":" + slot.affix());
    }

    /**
     * Every pair of the profile's bundles that this declaration and the profile's endings disagree
     * about, in slot order.
     */
    public List<SyncretismDisagreement> reconcileWith(ParadigmProfile profile) {
        SyncretismMap produced = derivedFrom(profile);
        List<String> bundles = profile.slotBundles();
        List<SyncretismDisagreement> out = new ArrayList<>();

        for (int i = 0; i < bundles.size(); i += 1) {
            for (int j = i + 1; j < bundles.size(); j += 1) {
                String first = bundles.get(i);
                String second = bundles.get(j);
                boolean isDeclared = areSyncretic(first, second);
                boolean isProduced = produced.areSyncretic(first, second);
                if (isDeclared != isProduced) {
                    out.add(new SyncretismDisagreement(first, second, isDeclared, isProduced));
                }
            }
        }
        return out;
    }
}
