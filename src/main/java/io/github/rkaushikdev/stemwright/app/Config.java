package io.github.rkaushikdev.stemwright.app;

import io.github.rkaushikdev.stemwright.domain.Identifiers;
import io.github.rkaushikdev.stemwright.domain.drill.DrillPolicy;
import io.github.rkaushikdev.stemwright.domain.drill.DrillPolicyPatch;
import io.github.rkaushikdev.stemwright.domain.errors.ErrorDetail;
import io.github.rkaushikdev.stemwright.domain.errors.ProblemCollector;
import io.github.rkaushikdev.stemwright.domain.errors.ValidationException;
import io.github.rkaushikdev.stemwright.domain.text.Numbers;
import io.github.rkaushikdev.stemwright.domain.text.Text;
import io.github.rkaushikdev.stemwright.infra.Codec;
import io.github.rkaushikdev.stemwright.infra.json.Json;
import io.github.rkaushikdev.stemwright.infra.json.JsonBoolean;
import io.github.rkaushikdev.stemwright.infra.json.JsonNull;
import io.github.rkaushikdev.stemwright.infra.json.JsonNumber;
import io.github.rkaushikdev.stemwright.infra.json.JsonObject;
import io.github.rkaushikdev.stemwright.infra.json.JsonSyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.ObjIntConsumer;

/**
 * Configuration, read from one object rather than from the environment directly, so that a
 * deployment can put it in a file, a secret store or the environment and nothing downstream has to
 * know which.
 *
 * <p>Every field has a default and every default is a working one. A process that refuses to start
 * because a tuning parameter is absent is a process that will be started with a wrong value instead.
 *
 * @param dataDir where the journal and the snapshots are kept
 * @param port the port the http server listens on
 * @param version the version the server reports
 * @param defaultLanguage the language a request that names none is about, a slug
 * @param policy the deployment's adjustment of the default drilling policy
 * @param snapshotEvery how many journal entries pass between snapshots
 * @param bodyLimit the largest request body the server reads, in bytes
 */
public record Config(
        String dataDir,
        int port,
        String version,
        String defaultLanguage,
        DrillPolicyPatch policy,
        int snapshotEvery,
        int bodyLimit) {

    /** The configuration of a process given nothing at all. */
    public static final Config DEFAULT =
            new Config(".stemwright-data", 8080, "dev", "latin", DrillPolicyPatch.EMPTY, 500, 1_000_000);

    private static final int MAX_PORT = 65535;

    /** The counts the policy block may set, in the order the policy checks them. */
    private static final List<Count> COUNTS = List.of(
            new Count("newPerDay", DrillPolicyPatch.Builder::newPerDay),
            new Count("reviewsPerDay", DrillPolicyPatch.Builder::reviewsPerDay),
            new Count("perLexemeCap", DrillPolicyPatch.Builder::perLexemeCap),
            new Count("rowSpacing", DrillPolicyPatch.Builder::rowSpacing));

    public Config {
        Objects.requireNonNull(dataDir, "dataDir");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(defaultLanguage, "defaultLanguage");
        Objects.requireNonNull(policy, "policy");
    }

    /**
     * Reads a configuration object, taking the default for every field it leaves out or sets to
     * {@code null} and ignoring every field it does not recognise.
     *
     * <p>A port, snapshot interval or body limit out of range, and a policy block that is not an
     * object, holds a field of the wrong type or that the policy rules refuse, are reported
     * together. Something that is not an object, a field of the wrong type, and a default language
     * that is not a slug fail on the spot.
     *
     * @throws ValidationException naming every problem
     */
    public static Config read(Json raw) {
        ProblemCollector problems = new ProblemCollector();
        JsonObject row = Codec.requireObject("config", raw);

        String dataDir = Codec.readOptionalString(row, "config", "dataDir").orElse(DEFAULT.dataDir);
        int port = readPort(problems, Codec.readOptionalNumber(row, "config", "port"));
        String version = Codec.readOptionalString(row, "config", "version").orElse(DEFAULT.version);
        String defaultLanguage = row.has("defaultLanguage")
                ? Identifiers.asSlug(
                        "defaultLanguage",
                        Codec.readOptionalString(row, "config", "defaultLanguage")
                                .orElse(""))
                : DEFAULT.defaultLanguage;
        DrillPolicyPatch policy = readPolicy(problems, row.get("policy"));
        int snapshotEvery = readPositive(
                problems,
                "snapshotEvery",
                Codec.readOptionalNumber(row, "config", "snapshotEvery"),
                DEFAULT.snapshotEvery);
        int bodyLimit = readPositive(
                problems, "bodyLimit", Codec.readOptionalNumber(row, "config", "bodyLimit"), DEFAULT.bodyLimit);

        problems.throwIfAny("the configuration is not usable");
        return new Config(dataDir, port, version, defaultLanguage, policy, snapshotEvery, bodyLimit);
    }

    /**
     * Reads the text of a configuration file. A file that is empty or only spacing is the defaults.
     *
     * @throws ValidationException for text that is not json, or a configuration {@link #read}
     *     refuses
     */
    public static Config parse(String text) {
        if (Text.trim(text).isEmpty()) {
            return DEFAULT;
        }
        Json parsed;
        try {
            parsed = Json.parse(text);
        } catch (JsonSyntaxException error) {
            throw new ValidationException("the configuration file is not valid json", List.of(), error);
        }
        return read(parsed);
    }

    /** {@link #fromEnvironment(Map)} over this process's environment. */
    public static Config fromEnvironment() {
        return fromEnvironment(System.getenv());
    }

    /**
     * Reads the few settings a container is likely to pass as variables: {@code
     * STEMWRIGHT_DATA_DIR}, {@code STEMWRIGHT_PORT}, {@code STEMWRIGHT_VERSION} and {@code
     * STEMWRIGHT_LANGUAGE}. The port is read as a number the way the cli reads one, so text that is
     * not a number is refused as one that is not finite.
     *
     * @throws ValidationException for a setting {@link #read} refuses
     */
    public static Config fromEnvironment(Map<String, String> env) {
        JsonObject.Builder row = Json.object();
        whenSet(env, "STEMWRIGHT_DATA_DIR", value -> row.put("dataDir", value));
        whenSet(env, "STEMWRIGHT_PORT", value -> row.put("port", Numbers.parse(value)));
        whenSet(env, "STEMWRIGHT_VERSION", value -> row.put("version", value));
        whenSet(env, "STEMWRIGHT_LANGUAGE", value -> row.put("defaultLanguage", value));
        return read(row.build());
    }

    /** The settings, one line each, for a startup banner or a doctor report. */
    public List<String> describe() {
        return List.of(
                "data directory " + dataDir,
                "port " + port,
                "version " + version,
                "default language " + defaultLanguage,
                "snapshot every " + snapshotEvery + " journal entries");
    }

    private static void whenSet(Map<String, String> env, String name, Consumer<String> use) {
        String value = env.get(name);
        if (value != null) {
            use.accept(value);
        }
    }

    private static int readPort(ProblemCollector problems, Optional<Double> value) {
        if (value.isEmpty()) {
            return DEFAULT.port;
        }
        double port = value.get();
        if (!Numbers.isInteger(port) || port < 0 || port > MAX_PORT) {
            problems.add("port", "is not a port number: " + Numbers.format(port));
            return DEFAULT.port;
        }
        return (int) port;
    }

    /**
     * A whole number of at least one. A number past {@link Integer#MAX_VALUE} is refused with the
     * same message, because the field cannot hold it.
     */
    private static int readPositive(ProblemCollector problems, String name, Optional<Double> value, int fallback) {
        if (value.isEmpty()) {
            return fallback;
        }
        double count = value.get();
        if (!Numbers.isInteger(count) || count < 1 || count > Integer.MAX_VALUE) {
            problems.add(name, "must be a whole number above zero: " + Numbers.format(count));
            return fallback;
        }
        return (int) count;
    }

    /**
     * Reads the policy block into a patch and has the policy rules check it, reporting what they
     * refuse under {@code policy}. A field the block leaves out stays out of the patch; one that is
     * present, {@code null} included, must have the right type.
     *
     * <p>The patch holds its counts as ints and json holds doubles. The original handed a fraction
     * to the policy, which refused it as {@code must be a whole number: 2.5} at that count's place
     * among its problems. So a count an int cannot hold, a fraction or a number past the int range,
     * goes into the patch as a stand-in the policy is certain to refuse under that count's name and
     * at that count's place, and the refusal is then put in the words the original gave the number
     * actually written. A stand-in is never zero, so it cannot trip the rule about a policy that asks
     * for no drilling, which a written fraction or huge number could not trip either.
     */
    private static DrillPolicyPatch readPolicy(ProblemCollector problems, Optional<Json> raw) {
        if (raw.isEmpty() || raw.get() instanceof JsonNull) {
            return DrillPolicyPatch.EMPTY;
        }
        if (!(raw.get() instanceof JsonObject block)) {
            problems.add("policy", "is not an object");
            return DrillPolicyPatch.EMPTY;
        }

        DrillPolicyPatch.Builder patch = DrillPolicyPatch.builder();
        Map<String, Double> unheld = new HashMap<>();
        for (Count count : COUNTS) {
            Optional<Json> value = block.get(count.name());
            if (value.isEmpty()) {
                continue;
            }
            if (!(value.get() instanceof JsonNumber number)) {
                problems.add("policy." + count.name(), "is not a number");
                continue;
            }
            double written = number.value();
            if (fitsAnInt(written)) {
                count.setter().accept(patch, (int) written);
            } else {
                unheld.put(count.name(), written);
                count.setter().accept(patch, written < 0 ? Integer.MIN_VALUE : Integer.MAX_VALUE);
            }
        }
        Optional<Json> buryTwins = block.get("buryTwins");
        if (buryTwins.isPresent()) {
            if (buryTwins.get() instanceof JsonBoolean flag) {
                patch.buryTwins(flag.value());
            } else {
                problems.add("policy.buryTwins", "is not a boolean");
            }
        }

        DrillPolicyPatch built = patch.build();
        try {
            DrillPolicy.of(built);
        } catch (ValidationException refusal) {
            for (ErrorDetail detail : refusal.details()) {
                Double written = unheld.get(detail.path());
                String message = written == null ? detail.message() : refusalOf(written, detail.message());
                problems.add("policy", detail.path() + " " + message);
            }
        }
        return built;
    }

    private static boolean fitsAnInt(double value) {
        return Numbers.isInteger(value) && value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE;
    }

    /**
     * What the original policy said of a count it was given as written. A fraction is not a whole
     * number; a whole number past the int range is refused as its stand-in was, every such refusal
     * ending in the number refused, which is swapped for the one written.
     */
    private static String refusalOf(double written, String standInRefusal) {
        if (!Numbers.isInteger(written)) {
            return "must be a whole number: " + numberText(written);
        }
        return standInRefusal.substring(0, standInRefusal.lastIndexOf(' ') + 1) + Numbers.format(written);
    }

    /** JavaScript's {@code String(n)}, which, unlike {@link Numbers#format}, prints a NaN or an infinity. */
    private static String numberText(double value) {
        if (Double.isNaN(value)) {
            return "NaN";
        }
        if (Double.isInfinite(value)) {
            return value > 0 ? "Infinity" : "-Infinity";
        }
        return Numbers.format(value);
    }

    /** A count of the policy block, and how it is set on a patch. */
    private record Count(String name, ObjIntConsumer<DrillPolicyPatch.Builder> setter) {}
}
