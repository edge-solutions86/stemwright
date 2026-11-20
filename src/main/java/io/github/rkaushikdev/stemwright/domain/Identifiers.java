package io.github.rkaushikdev.stemwright.domain;

import io.github.rkaushikdev.stemwright.domain.errors.ValidationException;
import io.github.rkaushikdev.stemwright.domain.text.Text;
import java.text.Normalizer;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Ids, digests and slugs.
 *
 * <p>Ids are opaque strings with a kind prefix, such as {@code lex_9fx2ab}. They are plain strings
 * at runtime; {@link IdKind} names the prefix and these functions check the shape.
 */
public final class Identifiers {

    private static final Pattern BODY = Pattern.compile("^[0-9a-z]{6,32}$");
    private static final Pattern SLUG = Pattern.compile("^[a-z][a-z0-9]*(?:[-.][a-z0-9]+)*$");
    private static final Pattern MARKS = Pattern.compile("\\p{M}");
    private static final Pattern NOT_SLUG_CHARACTERS = Pattern.compile("[^a-z0-9]+");
    private static final Pattern EDGE_DASHES = Pattern.compile("^-+|-+$");

    /**
     * The separator between the parts of a digest. It is a real character rather than an empty
     * string so that {@code digest("ab", "c")} and {@code digest("a", "bc")} cannot collide, and it
     * is named by its number because it is invisible in an editor.
     */
    private static final String PART_SEPARATOR = Character.toString(0x1f);

    private Identifiers() {}

    public static boolean isId(IdKind kind, Object value) {
        if (!(value instanceof String text)) {
            return false;
        }
        String prefix = kind.prefix() + "_";
        if (!text.startsWith(prefix)) {
            return false;
        }
        return BODY.matcher(text.substring(prefix.length())).matches();
    }

    public static String asId(IdKind kind, String value) {
        if (!isId(kind, value)) {
            throw ValidationException.field(kind.key() + "Id", "is not a well formed " + kind.key() + " id: " + value);
        }
        return value;
    }

    /** Reads the kind back off an id, or nothing for an unknown prefix or a malformed body. */
    public static Optional<IdKind> kindOf(String value) {
        int underscore = value.indexOf('_');
        if (underscore <= 0) {
            return Optional.empty();
        }
        return IdKind.withPrefix(value.substring(0, underscore)).filter(kind -> isId(kind, value));
    }

    /**
     * A small non cryptographic hash, twelve lower alphanumeric characters long. It exists so a cell
     * id can be derived from the lexeme and the bundle rather than allocated, which keeps a re
     * expanded paradigm stable across runs and makes journal replay idempotent.
     */
    public static String digest(String... parts) {
        int h1 = 0x811c9dc5;
        int h2 = 0x01000193;
        String joined = String.join(PART_SEPARATOR, parts);
        for (int i = 0; i < joined.length(); i += 1) {
            int c = joined.charAt(i);
            h1 = (h1 ^ c) * 0x01000193;
            h2 = (h2 + c + i) * 0x85ebca6b;
        }
        String low = Text.padStart(Integer.toUnsignedString(h1, 36), 7, '0');
        String high = Text.padStart(Integer.toUnsignedString(h2, 36), 7, '0');
        return (low + high).substring(0, 12);
    }

    public static String derivedId(IdKind kind, String... parts) {
        if (parts.length == 0) {
            throw ValidationException.field("parts", "must name at least one component");
        }
        return kind.prefix() + "_" + digest(parts);
    }

    /** An allocator whose counter starts at zero. */
    public static IdAllocator createAllocator(String seed) {
        return createAllocator(seed, 0);
    }

    /**
     * Allocated ids come from a counter plus a run token so two processes writing the same journal
     * do not collide. Nothing depends on them being sortable.
     */
    public static IdAllocator createAllocator(String seed, long start) {
        return new IdAllocator(digest(seed).substring(0, 4), start);
    }

    /** Checks a human typed key such as an inflection class name or a feature value. */
    public static String asSlug(String path, String value) {
        String trimmed = Text.lower(Text.trim(value));
        if (!SLUG.matcher(trimmed).matches()) {
            throw ValidationException.field(path, "is not a slug: " + value);
        }
        return trimmed;
    }

    public static boolean isSlug(String value) {
        return SLUG.matcher(value).matches();
    }

    /** Folds any text into something {@link #isSlug} accepts. */
    public static String slugify(String value) {
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD);
        String lowered = Text.lower(MARKS.matcher(decomposed).replaceAll(""));
        String dashed = NOT_SLUG_CHARACTERS.matcher(lowered).replaceAll("-");
        String folded = EDGE_DASHES.matcher(dashed).replaceAll("");
        if (folded.isEmpty() || Character.isDigit(folded.charAt(0))) {
            return folded.isEmpty() ? "x" : "x-" + folded;
        }
        return folded;
    }
}
