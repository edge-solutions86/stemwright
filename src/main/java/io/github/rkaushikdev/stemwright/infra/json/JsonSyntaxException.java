package io.github.rkaushikdev.stemwright.infra.json;

/**
 * Text handed to {@link Json#parse} that is not json.
 *
 * <p>This is deliberately not a domain failure. The parser cannot know what the text was, so it
 * cannot say what the failure means: a torn last line of the journal is an integrity problem, a
 * broken request body is the client's mistake, and a broken configuration file is the operator's.
 * Each caller catches this and raises the failure that fits, as the original did with {@code
 * JSON.parse}'s {@code SyntaxError}.
 */
public final class JsonSyntaxException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int position;

    public JsonSyntaxException(String message, int position) {
        super(message + " at position " + position);
        this.position = position;
    }

    /** The utf-16 offset into the text where reading stopped. */
    public int position() {
        return position;
    }
}
