package io.github.rkaushikdev.stemwright.domain.paradigm;

/**
 * The profile revision a class is graded against on a day, with the class whose binding supplied
 * it.
 *
 * @param revision the revision in force on the day
 * @param boundThrough the class whose binding was used, which may be an ancestor
 */
public record ResolvedProfile(ProfileRevision revision, String boundThrough) {}
