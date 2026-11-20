package io.github.rkaushikdev.stemwright.domain.errors;

/** A record was addressed by an id that no store holds. */
public class NotFoundException extends StemwrightException {

    private static final long serialVersionUID = 1L;

    private final String kind;
    private final String id;

    public NotFoundException(String kind, String id) {
        super(ErrorCode.NOT_FOUND, kind + " " + id + " was not found");
        this.kind = kind;
        this.id = id;
    }

    public String kind() {
        return kind;
    }

    public String id() {
        return id;
    }
}
