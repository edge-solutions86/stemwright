package io.github.rkaushikdev.stemwright.bin;

import io.github.rkaushikdev.stemwright.app.Application;
import io.github.rkaushikdev.stemwright.domain.errors.ValidationException;
import io.github.rkaushikdev.stemwright.domain.text.Numbers;
import io.github.rkaushikdev.stemwright.http.AccountDirectory;
import io.github.rkaushikdev.stemwright.http.AccountDirectory.Account;
import io.github.rkaushikdev.stemwright.http.Api;
import io.github.rkaushikdev.stemwright.http.ApiOptions;
import io.github.rkaushikdev.stemwright.http.Handler;
import io.github.rkaushikdev.stemwright.http.Server;
import io.github.rkaushikdev.stemwright.infra.Codec;
import io.github.rkaushikdev.stemwright.infra.MemoryStores;
import io.github.rkaushikdev.stemwright.infra.json.Json;
import io.github.rkaushikdev.stemwright.infra.json.JsonObject;
import io.github.rkaushikdev.stemwright.infra.json.JsonSyntaxException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The server entry point. It reads what it needs from the environment and does nothing else, so
 * that everything it wires is testable without starting it.
 *
 * <p>The account directory here is a placeholder that reads a token map out of the environment. A
 * deployment replaces it with whatever the institution already runs, and the rest of the process
 * does not change.
 */
public final class Serve {

    private static final String PORT = "STEMWRIGHT_PORT";
    private static final String VERSION = "STEMWRIGHT_VERSION";
    private static final String ACCOUNTS = "STEMWRIGHT_ACCOUNTS";
    private static final String DEFAULT_PORT = "8080";
    private static final String DEFAULT_VERSION = "dev";
    private static final int LARGEST_PORT = 65_535;

    private Serve() {}

    /**
     * Reads the configuration out of the environment: {@code STEMWRIGHT_PORT}, 8080 when unset;
     * {@code STEMWRIGHT_VERSION}, {@code dev} when unset; and {@code STEMWRIGHT_ACCOUNTS}, a json
     * object from bearer token to an object with a string {@code id} and {@code role}, no accounts
     * when unset.
     *
     * <p>The port is read the way a number flag of the cli is: white space around it is ignored, a
     * {@code 0x}, {@code 0o} or {@code 0b} prefix is honoured, and a value that is empty or only
     * white space reads as 0.
     *
     * @throws IllegalArgumentException {@code STEMWRIGHT_PORT is not a port number: <value>} for a
     *     port that is not a whole number from 0 to 65535
     * @throws JsonSyntaxException when the accounts are not json
     * @throws ValidationException naming the part that is wrong when the accounts are json of
     *     another shape
     */
    public static ServeConfig readConfig(Map<String, String> env) {
        String givenPort = Optional.ofNullable(env.get(PORT)).orElse(DEFAULT_PORT);
        double port = Numbers.parse(givenPort);
        if (!Numbers.isInteger(port) || port < 0 || port > LARGEST_PORT) {
            throw new IllegalArgumentException(PORT + " is not a port number: " + givenPort);
        }

        String raw = env.get(ACCOUNTS);
        Map<String, Account> accounts = raw == null ? Map.of() : readAccounts(raw);

        return new ServeConfig((int) port, Optional.ofNullable(env.get(VERSION)).orElse(DEFAULT_VERSION), accounts);
    }

    /**
     * A stopped server over a fresh application on memory stores, whose accounts are the configured
     * ones. A failure the client is only shown as a bare 500 is written to the error stream with its
     * stack, so an operator still sees what the client does not.
     */
    public static Server buildServer(ServeConfig config) {
        Application app = Application.create(MemoryStores.create());
        Handler handler = Api.createApi(
                app,
                ApiOptions.builder(AccountDirectory.inMemory(config.accounts()))
                        .version(config.version())
                        .onError(Serve::reportFailure)
                        .build());
        return Server.create(handler, Server.Options.DEFAULT.withOnError(Serve::reportFailure));
    }

    /**
     * Reads the configuration from the process environment, listens on the configured port, and says
     * so on the error stream. The server keeps the process running from then on.
     *
     * @param args ignored; everything is read from the environment
     */
    public static void main(String[] args) {
        ServeConfig config = readConfig(System.getenv());
        buildServer(config).listen(config.port());
        System.err.println("stemwright " + config.version() + " listening on " + config.port());
    }

    private static Map<String, Account> readAccounts(String raw) {
        JsonObject tokens = Codec.requireObject(ACCOUNTS, Json.parse(raw));
        Map<String, Account> accounts = new LinkedHashMap<>();
        tokens.fields().forEach((token, value) -> {
            String path = ACCOUNTS + "." + token;
            JsonObject account = Codec.requireObject(path, value);
            accounts.put(
                    token, new Account(Codec.readString(account, path, "id"), Codec.readString(account, path, "role")));
        });
        return accounts;
    }

    /** Writes the failure and its stack as one piece, so two failing requests never interleave. */
    private static void reportFailure(Throwable error) {
        StringWriter trace = new StringWriter();
        error.printStackTrace(new PrintWriter(trace));
        System.err.print("unhandled failure " + trace);
    }
}
