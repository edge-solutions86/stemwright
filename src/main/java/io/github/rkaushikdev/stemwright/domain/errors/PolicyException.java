package io.github.rkaushikdev.stemwright.domain.errors;

/** The request was well formed but a configured rule refused it. */
public class PolicyException extends StemwrightException {

    private static final long serialVersionUID = 1L;

    private final String rule;

    public PolicyException(String rule, String message) {
        super(ErrorCode.POLICY, message);
        this.rule = rule;
    }

    /** The dotted name of the rule that refused, such as {@code daily.new.budget}. */
    public String rule() {
        return rule;
    }
}
