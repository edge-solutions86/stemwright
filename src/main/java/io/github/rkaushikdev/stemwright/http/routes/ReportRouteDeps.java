package io.github.rkaushikdev.stemwright.http.routes;

import io.github.rkaushikdev.stemwright.app.services.ReportService;
import io.github.rkaushikdev.stemwright.domain.course.CourseScope;
import io.github.rkaushikdev.stemwright.http.AccountDirectory;
import java.util.Objects;
import java.util.function.Function;

/**
 * What the report routes are built with.
 *
 * @param directory who each bearer token belongs to
 * @param reports the service that builds the reports and checks who may read them
 * @param scopeOf the course a course key names together with its roster, which every report checks
 *     the caller against; it throws for a course it cannot find
 */
public record ReportRouteDeps(
        AccountDirectory directory, ReportService reports, Function<String, CourseScope> scopeOf) {

    public ReportRouteDeps {
        Objects.requireNonNull(directory, "directory");
        Objects.requireNonNull(reports, "reports");
        Objects.requireNonNull(scopeOf, "scopeOf");
    }
}
