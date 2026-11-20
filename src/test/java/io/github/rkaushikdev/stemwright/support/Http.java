package io.github.rkaushikdev.stemwright.support;

import static io.github.rkaushikdev.stemwright.support.Course.MICHAELMAS;

import io.github.rkaushikdev.stemwright.app.Clock;
import io.github.rkaushikdev.stemwright.app.RecordingBus;
import io.github.rkaushikdev.stemwright.app.Stores;
import io.github.rkaushikdev.stemwright.app.services.DrillService;
import io.github.rkaushikdev.stemwright.domain.course.CourseScope;
import io.github.rkaushikdev.stemwright.domain.course.Enrolment;
import io.github.rkaushikdev.stemwright.domain.course.EnrolmentInput;
import io.github.rkaushikdev.stemwright.domain.course.Roster;
import io.github.rkaushikdev.stemwright.domain.drill.Deck;
import io.github.rkaushikdev.stemwright.domain.drill.DeckInput;
import io.github.rkaushikdev.stemwright.domain.text.Text;
import io.github.rkaushikdev.stemwright.http.AccountDirectory;
import io.github.rkaushikdev.stemwright.http.AccountDirectory.Account;
import io.github.rkaushikdev.stemwright.http.HttpRequest;
import io.github.rkaushikdev.stemwright.http.Method;
import io.github.rkaushikdev.stemwright.infra.json.Json;
import io.github.rkaushikdev.stemwright.infra.json.JsonArray;
import io.github.rkaushikdev.stemwright.infra.json.JsonObject;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * The fixture the route and api tests build on: three accounts behind bearer tokens, requests as a
 * route sees them, the seeded stores with a deck and an enrolment on top, and readers for the json a
 * route answers with.
 */
public final class Http {

    private Http() {}

    /**
     * {@code tok-student} is the learner {@code lrn_one}, enrolled on {@link Course#MICHAELMAS} by
     * {@link #httpHarness()}; {@code tok-other} is the learner {@code lrn_two}, who is not; and
     * {@code tok-teacher} is {@code lrn_teacher}, an instructor.
     */
    public static final AccountDirectory DIRECTORY = AccountDirectory.inMemory(Map.of(
            "tok-student", new Account("lrn_one", "learner"),
            "tok-other", new Account("lrn_two", "learner"),
            "tok-teacher", new Account("lrn_teacher", "instructor")));

    /** A request with no token, query or body. */
    public static HttpRequest makeRequest(Method method, String path) {
        return makeRequest(method, path, Options.NONE);
    }

    /** A request carrying what the options give: a bearer token, a query and a body. */
    public static HttpRequest makeRequest(Method method, String path, Options options) {
        return new HttpRequest(
                method,
                path,
                Map.of(),
                options.query(),
                options.token()
                        .map(token -> Map.of("authorization", "Bearer " + token))
                        .orElse(Map.of()),
                options.body());
    }

    /** Options carrying the bearer token. */
    public static Options token(String token) {
        return Options.NONE.withToken(token);
    }

    /**
     * What a request made by {@link #makeRequest(Method, String, Options)} carries.
     *
     * @param token the bearer token, sent as {@code authorization: Bearer <token>}
     * @param query the query, empty when there is none
     * @param body the body, as a route reads it
     */
    public record Options(Optional<String> token, Map<String, String> query, Optional<Json> body) {

        /** No token, no query and no body. */
        public static final Options NONE = new Options(Optional.empty(), Map.of(), Optional.empty());

        public Options {
            Objects.requireNonNull(token, "token");
            query = Map.copyOf(query);
            Objects.requireNonNull(body, "body");
        }

        public Options withToken(String value) {
            return new Options(Optional.of(value), query, body);
        }

        public Options withQuery(Map<String, String> value) {
            return new Options(token, value, body);
        }

        public Options withBody(Json value) {
            return new Options(token, query, Optional.of(value));
        }
    }

    /**
     * {@link App#seededStores()} with the deck {@code all} over {@link Profiles#LATIN_NOUN}, {@link
     * Course#MICHAELMAS} and {@code lrn_one} enrolled on it, and a drill service over them on the
     * harness's bus and clock.
     */
    public static Harness httpHarness() {
        Stores stores = App.seededStores();
        stores.decks().put(Deck.of(Profiles.LATIN_NOUN, DeckInput.of("all")));
        stores.courses().put(MICHAELMAS);
        stores.courses().putEnrolment(Enrolment.of(MICHAELMAS, EnrolmentInput.of("lrn_one")));

        App.Harness harness = App.testHarness();
        DrillService drill = new DrillService(stores, harness.bus(), harness.clock());

        Function<String, CourseScope> scopeOf = courseKey -> stores.courses()
                .get(courseKey)
                .map(course -> new CourseScope(
                        course, Roster.of(course, stores.courses().enrolments(courseKey))))
                .orElseThrow(() -> new NoSuchElementException("no course " + courseKey));

        return new Harness(stores, harness.bus(), harness.clock(), drill, scopeOf);
    }

    /**
     * What the route tests are built with.
     *
     * @param stores the stores the service reads and writes
     * @param bus the bus the service publishes to
     * @param clock the clock the service reads the day from
     * @param drill the drill service
     * @param scopeOf the stored course of a key with its roster, throwing for a course not stored
     */
    public record Harness(
            Stores stores, RecordingBus bus, Clock clock, DrillService drill, Function<String, CourseScope> scopeOf) {

        public Harness {
            Objects.requireNonNull(stores, "stores");
            Objects.requireNonNull(bus, "bus");
            Objects.requireNonNull(clock, "clock");
            Objects.requireNonNull(drill, "drill");
            Objects.requireNonNull(scopeOf, "scopeOf");
        }
    }

    /**
     * The value at a dotted path into a body, each step a key of an object or an index into an
     * array, such as {@code cells.0.id}.
     *
     * @throws AssertionError naming the path when a step finds nothing
     */
    public static Json at(Json value, String path) {
        Json current = value;
        for (String step : Text.split(path, ".")) {
            current = step(current, step).orElseThrow(() -> new AssertionError("nothing at " + path + " in " + value));
        }
        return current;
    }

    /** The string at the path. */
    public static String textAt(Json value, String path) {
        return at(value, path).asString().orElseThrow(() -> new AssertionError(path + " is not a string"));
    }

    /** The number at the path. */
    public static double numberAt(Json value, String path) {
        return at(value, path).asNumber().orElseThrow(() -> new AssertionError(path + " is not a number"));
    }

    /** The items of the array at the path. */
    public static List<Json> itemsAt(Json value, String path) {
        return at(value, path)
                .asArray()
                .map(JsonArray::items)
                .orElseThrow(() -> new AssertionError(path + " is not an array"));
    }

    /** The items of a body that is itself an array. */
    public static List<Json> items(Json value) {
        return value.asArray().map(JsonArray::items).orElseThrow(() -> new AssertionError(value + " is not an array"));
    }

    private static Optional<Json> step(Json current, String step) {
        if (current instanceof JsonObject object) {
            return object.get(step);
        }
        if (current instanceof JsonArray array && step.matches("\\d+") && Integer.parseInt(step) < array.size()) {
            return Optional.of(array.get(Integer.parseInt(step)));
        }
        return Optional.empty();
    }
}
