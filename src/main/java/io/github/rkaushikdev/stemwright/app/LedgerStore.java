package io.github.rkaushikdev.stemwright.app;

import io.github.rkaushikdev.stemwright.domain.mastery.MasteryLedger;
import java.util.Optional;

/** The mastery ledger of each learner, keyed by learner id. */
public interface LedgerStore {

    /** The learner's ledger, absent for a learner who has never answered anything. */
    Optional<MasteryLedger> get(String learnerId);

    /** Stores the ledger under its learner, in place of the ledger stored there before. */
    void put(MasteryLedger ledger);
}
