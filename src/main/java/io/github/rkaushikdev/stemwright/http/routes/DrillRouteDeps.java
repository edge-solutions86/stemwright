package io.github.rkaushikdev.stemwright.http.routes;

import io.github.rkaushikdev.stemwright.app.services.DrillService;
import io.github.rkaushikdev.stemwright.domain.course.CourseScope;
import io.github.rkaushikdev.stemwright.http.AccountDirectory;
import java.util.Objects;
import java.util.function.Function;

/**
 * What the drill routes and the bulk answer route are built with. The two take the same three
 * things, so one record serves both.
 *
 * @param directory who each bearer token belongs to
 * @param drill the service that builds queues and marks answers
 * @param scopeOf the course a course key names together with its roster, which every permission
 *     check is made against; it throws for a course it cannot find
 */
public record DrillRouteDeps(AccountDirectory directory, DrillService drill, Function<String, CourseScope> scopeOf) {

    public DrillRouteDeps {
        Objects.requireNonNull(directory, "directory");
        Objects.requireNonNull(drill, "drill");
        Objects.requireNonNull(scopeOf, "scopeOf");
    }
}
