package io.github.rkaushikdev.stemwright.infra;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.stemwright.app.Stores;
import io.github.rkaushikdev.stemwright.domain.errors.ValidationException;
import io.github.rkaushikdev.stemwright.infra.json.JsonArray;
import io.github.rkaushikdev.stemwright.infra.json.JsonString;
import io.github.rkaushikdev.stemwright.support.Problems;
import org.junit.jupiter.api.Test;

/**
 * How a snapshot document is read when it is not quite what {@link Snapshot#encode} writes. A
 * learner that is not a string is read as text, and a missing or {@code null} payload field as an
 * empty list, so every snapshot already on disk still loads. A sequence is a whole number, as a
 * journal entry's is.
 */
class SnapshotDecodingTest {

    @Test
    void readsALearnerThatIsNotAStringAsText() {
        Snapshot snapshot = Snapshot.decode(
                "{\"seq\":1,\"at\":\"x\",\"learners\":[\"lrn_one\",2,1.5,true,null,[1,null,[2,3]],{}]}");
        assertThat(snapshot.learners())
                .containsExactly("lrn_one", "2", "1.5", "true", "null", "1,,2,3", "[object Object]");
    }

    @Test
    void readsAMissingOrNullStateAsEmptyAndPassesOverOneThatIsNotAList() {
        Snapshot snapshot = Snapshot.decode("{\"seq\":1,\"at\":\"x\",\"learners\":[],\"ledgers\":null}");
        assertThat(snapshot.payload().get("ledgers")).contains(JsonArray.EMPTY);
        assertThat(snapshot.payload().get("attempts")).contains(JsonArray.EMPTY);

        Snapshot odd = Snapshot.decode("{\"seq\":1,\"at\":\"x\",\"learners\":[],\"ledgers\":\"none\",\"attempts\":7}");
        assertThat(odd.payload().get("ledgers")).contains(new JsonString("none"));
        Stores stores = MemoryStores.create();
        odd.restore(stores);
        assertThat(stores.ledgers().get("lrn_one")).isEmpty();
        assertThat(stores.attempts().listByLearner("lrn_one")).isEmpty();
    }

    @Test
    void refusesASequenceThatIsNotAWholeNumber() {
        ValidationException failure =
                Problems.validationFailureOf(() -> Snapshot.decode("{\"seq\":1.5,\"at\":\"x\",\"learners\":[]}"));
        assertThat(failure).hasMessage("snapshot.seq must be a whole number: 1.5");
    }
}
