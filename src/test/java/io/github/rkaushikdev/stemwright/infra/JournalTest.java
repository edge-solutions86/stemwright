package io.github.rkaushikdev.stemwright.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.stemwright.domain.errors.ConflictException;
import io.github.rkaushikdev.stemwright.domain.errors.IntegrityException;
import io.github.rkaushikdev.stemwright.domain.errors.ValidationException;
import io.github.rkaushikdev.stemwright.domain.time.Instants;
import io.github.rkaushikdev.stemwright.infra.json.Json;
import io.github.rkaushikdev.stemwright.infra.json.JsonObject;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class JournalTest {

    private static final Instant AT = Instant.parse("2025-03-03T09:00:00Z");

    private static Journal seeded() {
        Journal journal = Journal.inMemory();
        journal.append(
                List.of(
                        new JournalAppend(JournalKind.LEXEME_PUT, id("lex_a")),
                        new JournalAppend(JournalKind.ATTEMPT_APPENDED, id("att_a"))),
                AT);
        return journal;
    }

    private static JsonObject id(String value) {
        return Json.object().put("id", value).build();
    }

    private static JournalEntry entry(long seq, Json payload) {
        return new JournalEntry(seq, Instants.toIsoString(AT), JournalKind.LEXEME_PUT, payload);
    }

    @Nested
    class IsJournalKind {

        @Test
        void acceptsADeclaredKindAndNothingElse() {
            assertThat(JournalKind.fromKey("lexeme.put")).contains(JournalKind.LEXEME_PUT);
            assertThat(JournalKind.fromKey("lexeme.exploded")).isEmpty();
        }
    }

    @Nested
    class Append {

        @Test
        void assignsSequenceNumbersFromOne() {
            Journal journal = seeded();
            assertThat(journal.read()).extracting(JournalEntry::seq).containsExactly(1L, 2L);
        }

        @Test
        void carriesOnNumberingAcrossCalls() {
            Journal journal = seeded();
            List<JournalEntry> written =
                    journal.append(List.of(new JournalAppend(JournalKind.LEDGER_PUT, JsonObject.EMPTY)), AT);
            assertThat(written.get(0).seq()).isEqualTo(3);
            assertThat(journal.size()).isEqualTo(3);
            assertThat(journal.lastSeq()).isEqualTo(3);
        }

        @Test
        void stampsEveryEntryOfOneCallWithTheSameInstant() {
            List<JournalEntry> entries = seeded().read();
            assertThat(entries.get(0).at()).isEqualTo("2025-03-03T09:00:00.000Z");
            assertThat(entries.get(1).at()).isEqualTo(entries.get(0).at());
        }

        @Test
        void writesNothingForAnEmptyAppend() {
            Journal journal = Journal.inMemory();
            assertThat(journal.append(List.of(), AT)).isEmpty();
            assertThat(journal.size()).isZero();
            assertThat(journal.lastSeq()).isZero();
        }
    }

    @Nested
    class Read {

        @Test
        void readsFromASequenceOnwards() {
            Journal journal = seeded();
            assertThat(journal.read(2)).extracting(JournalEntry::seq).containsExactly(2L);
            assertThat(journal.read(9)).isEmpty();
        }

        @Test
        void refusesASequenceBelowOne() {
            Journal journal = seeded();
            assertThatThrownBy(() -> journal.read(0)).isInstanceOf(ConflictException.class);
        }
    }

    @Nested
    class EncodeAndDecode {

        @Test
        void roundTripsOneEntry() {
            JournalEntry entry = entry(1, Json.object().put("a", 1).build());
            assertThat(JournalEntry.decode(entry.encode(), 1)).isEqualTo(entry);
        }

        @Test
        void roundTripsAWholeJournal() {
            List<JournalEntry> entries = seeded().read();
            assertThat(Journal.decode(Journal.encode(entries))).isEqualTo(entries);
        }

        @Test
        void encodesAnEmptyJournalAsAnEmptyString() {
            assertThat(Journal.encode(List.of())).isEmpty();
            assertThat(Journal.decode("")).isEmpty();
        }

        @Test
        void endsTheEncodedJournalWithANewlineSoAnAppendStartsClean() {
            assertThat(Journal.encode(seeded().read())).endsWith("\n");
        }

        @Test
        void ignoresBlankLinesWhenDecoding() {
            JournalEntry entry = entry(1, JsonObject.EMPTY);
            assertThat(Journal.decode(entry.encode() + "\n\n")).hasSize(1);
        }
    }

    @Nested
    class ADamagedJournal {

        @Test
        void namesTheLineThatIsNotValidJson() {
            assertThatThrownBy(() -> JournalEntry.decode("{\"seq\":1,", 7)).isInstanceOf(IntegrityException.class);
            assertThatThrownBy(() -> JournalEntry.decode("{\"seq\":1,", 7))
                    .hasMessageContaining("journal line 7 is not valid json");
        }

        @Test
        void refusesALineThatParsesToSomethingOtherThanAnObject() {
            assertThatThrownBy(() -> JournalEntry.decode("[1,2]", 3))
                    .hasMessageContaining("journal line 3 is not an object");
        }

        @Test
        void refusesAnUnknownKind() {
            String line = Json.stringify(Json.object()
                    .put("seq", 1)
                    .put("at", Instants.toIsoString(AT))
                    .put("kind", "nope")
                    .put("payload", JsonObject.EMPTY)
                    .build());
            assertThatThrownBy(() -> JournalEntry.decode(line, 1)).hasMessageContaining("unknown kind: nope");
        }

        @Test
        void refusesAnEntryWithNoPayload() {
            String line = Json.stringify(Json.object()
                    .put("seq", 1)
                    .put("at", Instants.toIsoString(AT))
                    .put("kind", "lexeme.put")
                    .build());
            assertThatThrownBy(() -> JournalEntry.decode(line, 1)).isInstanceOf(ValidationException.class);
        }

        @Test
        void refusesAJournalWhoseSequenceNumbersSkip() {
            JournalEntry first = entry(1, JsonObject.EMPTY);
            JournalEntry third = entry(3, JsonObject.EMPTY);
            assertThatThrownBy(() -> Journal.decode(Journal.encode(List.of(first, third))))
                    .hasMessageContaining("out of order at line 2, expected sequence 2 and found 3");
        }

        @Test
        void refusesAJournalThatStartsAtTheWrongSequence() {
            JournalEntry entry = entry(5, JsonObject.EMPTY);
            assertThatThrownBy(() -> Journal.decode(Journal.encode(List.of(entry))))
                    .isInstanceOf(IntegrityException.class);
        }
    }

    @Nested
    class EntriesOfKindAndPayloadObject {

        @Test
        void picksOutOneKind() {
            List<JournalEntry> entries = seeded().read();
            assertThat(Journal.entriesOfKind(entries, JournalKind.LEXEME_PUT)).hasSize(1);
            assertThat(Journal.entriesOfKind(entries, JournalKind.DECK_PUT)).isEmpty();
        }

        @Test
        void readsAPayloadAsAnObjectAndRefusesOneThatIsNot() {
            List<JournalEntry> entries = seeded().read();
            assertThat(entries.get(0).payloadObject()).isEqualTo(id("lex_a"));
            JournalEntry first = entries.get(0);
            JournalEntry broken = new JournalEntry(first.seq(), first.at(), first.kind(), Json.of("nope"));
            assertThatThrownBy(broken::payloadObject).hasMessageContaining("journal.1.payload is not an object");
        }
    }

    @Nested
    class ASeededJournal {

        @Test
        void carriesOnNumberingAfterTheEntriesItWasHanded() {
            Journal journal = Journal.inMemory(List.of(entry(1, JsonObject.EMPTY)));
            List<JournalEntry> written =
                    journal.append(List.of(new JournalAppend(JournalKind.LEDGER_PUT, JsonObject.EMPTY)), AT);
            assertThat(written.get(0).seq()).isEqualTo(2);
        }
    }
}
