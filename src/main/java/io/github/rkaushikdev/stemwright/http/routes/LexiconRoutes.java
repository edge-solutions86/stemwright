package io.github.rkaushikdev.stemwright.http.routes;

import io.github.rkaushikdev.stemwright.domain.course.AccountRole;
import io.github.rkaushikdev.stemwright.domain.errors.ValidationException;
import io.github.rkaushikdev.stemwright.domain.lexicon.Lexeme;
import io.github.rkaushikdev.stemwright.domain.lexicon.LexemeInput;
import io.github.rkaushikdev.stemwright.domain.lexicon.StemInput;
import io.github.rkaushikdev.stemwright.domain.lexicon.StemSet;
import io.github.rkaushikdev.stemwright.http.Auth;
import io.github.rkaushikdev.stemwright.http.AuthedHandler;
import io.github.rkaushikdev.stemwright.http.AuthedRequest;
import io.github.rkaushikdev.stemwright.http.HttpResponse;
import io.github.rkaushikdev.stemwright.http.Method;
import io.github.rkaushikdev.stemwright.http.Route;
import io.github.rkaushikdev.stemwright.infra.Codec;
import io.github.rkaushikdev.stemwright.infra.json.Json;
import io.github.rkaushikdev.stemwright.infra.json.JsonNull;
import io.github.rkaushikdev.stemwright.infra.json.JsonObject;
import io.github.rkaushikdev.stemwright.infra.json.JsonString;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Lexicon routes. Reading is open to anybody with an account, because a word list is not private
 * and every learner needs one. Writing is behind both a role check here and the permission check
 * the service does, which is deliberate belt and braces: the route check keeps a learner off the
 * endpoint entirely, and the service check is the one that would still hold if this file were wired
 * up wrong.
 */
public final class LexiconRoutes {

    private static final List<AccountRole> EDITORS = List.of(AccountRole.INSTRUCTOR, AccountRole.ADMIN);

    private LexiconRoutes() {}

    /**
     * A lexeme as the api writes it: its id, lemma, citation form, language, class, homograph,
     * glosses, tags and stems, with the inherent features as one {@code dimension=value;...} key.
     */
    public static JsonObject lexemeJson(Lexeme entry) {
        return Json.object()
                .put("id", entry.id())
                .put("lemma", entry.lemma())
                .put("citation", entry.citationForm())
                .put("language", entry.language())
                .put("classKey", entry.classKey())
                .put("homograph", entry.homograph())
                .put("glosses", Json.strings(entry.glosses()))
                .put("tags", Json.strings(entry.tags()))
                .put("stems", Codec.encodeStems(entry.stems()))
                .put("inherent", entry.inherentKey())
                .build();
    }

    /**
     * {@code GET /languages/:language/lexemes} (searching when the query has a {@code q}), {@code GET
     * /lexemes/:lexemeId}, and, for instructors and admins only, {@code POST
     * /languages/:language/lexemes} and {@code DELETE /lexemes/:lexemeId}, each behind {@link
     * Auth#withActor}.
     */
    public static List<Route> routes(LexiconRouteDeps deps) {
        AuthedHandler create = request -> create(deps, request);
        AuthedHandler remove = request -> remove(deps, request);
        return List.of(
                Route.of(
                        Method.GET,
                        "/languages/:language/lexemes",
                        Auth.withActor(deps.directory(), request -> list(deps, request))),
                Route.of(
                        Method.GET,
                        "/lexemes/:lexemeId",
                        Auth.withActor(deps.directory(), request -> read(deps, request))),
                Route.of(
                        Method.POST,
                        "/languages/:language/lexemes",
                        Auth.withActor(deps.directory(), Auth.requireRole(EDITORS, create))),
                Route.of(
                        Method.DELETE,
                        "/lexemes/:lexemeId",
                        Auth.withActor(deps.directory(), Auth.requireRole(EDITORS, remove))));
    }

    private static HttpResponse list(LexiconRouteDeps deps, AuthedRequest request) {
        String language = request.params().get("language");
        String term = request.query().get("q");
        List<Lexeme> entries = term == null
                ? List.copyOf(deps.lexicon().load(language).byId().values())
                : deps.lexicon().search(language, term);
        return HttpResponse.json(
                200, Json.array(entries.stream().map(LexiconRoutes::lexemeJson).toList()));
    }

    private static HttpResponse read(LexiconRouteDeps deps, AuthedRequest request) {
        return HttpResponse.json(
                200, lexemeJson(deps.lexicon().get(request.params().get("lexemeId"))));
    }

    private static HttpResponse create(LexiconRouteDeps deps, AuthedRequest request) {
        // A request with no body is refused exactly as a body of null is: neither is an object.
        JsonObject body = Codec.requireObject("body", request.body().orElse(Json.NULL));
        String lemma = Codec.readString(body, "body", "lemma");
        String classKey = Codec.readString(body, "body", "classKey");
        StemSet stems = stemsFrom(body);
        Map<String, String> inherent = inherentFrom(body);
        List<String> glosses = body.has("glosses") ? Codec.readStringArray(body, "body", "glosses") : List.of();
        List<String> tags = body.has("tags") ? Codec.readStringArray(body, "body", "tags") : List.of();

        Lexeme built = deps.lexicon()
                .add(
                        request.actor(),
                        request.params().get("language"),
                        LexemeInput.builder(lemma, classKey, stems)
                                .inherent(inherent)
                                .glosses(glosses)
                                .tags(tags)
                                .build());
        return HttpResponse.json(201, lexemeJson(built));
    }

    private static HttpResponse remove(LexiconRouteDeps deps, AuthedRequest request) {
        deps.lexicon().remove(request.actor(), request.params().get("lexemeId"));
        return HttpResponse.noContent();
    }

    /**
     * The {@code stems} of a body, each row an object with a string {@code role} and {@code form}, an
     * optional string {@code note}, and {@code suppletive} only when it is {@code true}.
     */
    private static StemSet stemsFrom(JsonObject body) {
        List<Json> rows = Codec.readArray(body, "body", "stems");
        List<StemInput> inputs = new ArrayList<>(rows.size());
        for (int index = 0; index < rows.size(); index += 1) {
            String path = "body.stems." + index;
            JsonObject row = Codec.requireObject(path, rows.get(index));
            Optional<String> note = Codec.readOptionalString(row, path, "note");
            String role = Codec.readString(row, path, "role");
            String form = Codec.readString(row, path, "form");
            boolean suppletive = row.get("suppletive").flatMap(Json::asBoolean).orElse(false);
            inputs.add(new StemInput(role, form, suppletive, note));
        }
        if (inputs.isEmpty()) {
            throw ValidationException.field("body.stems", "must hold at least one stem");
        }
        return StemSet.of(inputs);
    }

    /** The {@code inherent} features of a body, none when it is missing or {@code null}. */
    private static Map<String, String> inherentFrom(JsonObject body) {
        Optional<Json> raw = body.get("inherent");
        if (raw.isEmpty() || raw.get() instanceof JsonNull) {
            return Map.of();
        }
        JsonObject row = Codec.requireObject("body.inherent", raw.get());
        Map<String, String> features = new LinkedHashMap<>();
        for (Map.Entry<String, Json> entry : row.fields().entrySet()) {
            if (!(entry.getValue() instanceof JsonString value)) {
                throw ValidationException.field("body.inherent." + entry.getKey(), "is not a string");
            }
            features.put(entry.getKey(), value.value());
        }
        return Collections.unmodifiableMap(features);
    }
}
