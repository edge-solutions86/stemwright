package io.github.rkaushikdev.stemwright.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.github.rkaushikdev.stemwright.domain.errors.PolicyException;
import io.github.rkaushikdev.stemwright.infra.json.Json;
import io.github.rkaushikdev.stemwright.support.TestMaps;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RateLimitTest {

    private static final RateLimit LIMIT = new RateLimit(1000, 3);

    private static HttpRequest request() {
        return request(Map.of());
    }

    private static HttpRequest request(Map<String, String> headers) {
        return HttpRequest.of(Method.GET, "/decks").withHeaders(headers);
    }

    @Nested
    class KeyFor {

        @Test
        void keysOnTheTokenWhenThereIsOne() {
            assertThat(RateLimit.keyFor(request(Map.of("authorization", "Bearer tok"))))
                    .isEqualTo("token:Bearer tok");
        }

        @Test
        void keysOnTheForwardedAddressWhenThereIsNoToken() {
            assertThat(RateLimit.keyFor(request(Map.of("x-forwarded-for", "10.0.0.1"))))
                    .isEqualTo("address:10.0.0.1");
        }

        @Test
        void fallsBackToAnUnknownAddress() {
            assertThat(RateLimit.keyFor(request())).isEqualTo("address:unknown");
        }

        @Test
        void keysTwoLearnersBehindOneAddressSeparately() {
            String first = RateLimit.keyFor(
                    request(TestMaps.ordered("authorization", "Bearer a", "x-forwarded-for", "10.0.0.1")));
            String second = RateLimit.keyFor(
                    request(TestMaps.ordered("authorization", "Bearer b", "x-forwarded-for", "10.0.0.1")));
            assertThat(first).isNotEqualTo(second);
        }
    }

    @Nested
    class TheLimiter {

        @Test
        void countsUpWithinAWindow() {
            Limiter limiter = Limiter.create(LIMIT);
            assertThat(limiter.check("a", 0).count()).isEqualTo(1);
            assertThat(limiter.check("a", 100).count()).isEqualTo(2);
            assertThat(limiter.check("a", 200).count()).isEqualTo(3);
            assertThat(limiter.check("a", 300).remaining()).isZero();
        }

        @Test
        void startsANewWindowOnceTheOldOneHasPassed() {
            Limiter limiter = Limiter.create(LIMIT);
            limiter.check("a", 0);
            limiter.check("a", 100);
            assertThat(limiter.check("a", 1000).count()).isEqualTo(1);
        }

        @Test
        void countsEachKeySeparately() {
            Limiter limiter = Limiter.create(LIMIT);
            limiter.check("a", 0);
            assertThat(limiter.check("b", 0).count()).isEqualTo(1);
            assertThat(limiter.keys()).containsExactly("a", "b");
        }

        @Test
        void neverReportsANegativeRemaining() {
            Limiter limiter = Limiter.create(LIMIT);
            for (int index = 0; index < 9; index += 1) {
                limiter.check("a", index);
            }
            assertThat(limiter.check("a", 10).remaining()).isZero();
        }

        @Test
        void forgetsEverythingWhenItIsReset() {
            Limiter limiter = Limiter.create(LIMIT);
            limiter.check("a", 0);
            limiter.reset();
            assertThat(limiter.keys()).isEmpty();
        }
    }

    @Nested
    class IsOver {

        @Test
        void isFalseUpToTheLimitAndTruePastIt() {
            assertThat(new LimitState("a", 3, 0, 0).isOver(LIMIT)).isFalse();
            assertThat(new LimitState("a", 4, 0, 0).isOver(LIMIT)).isTrue();
        }
    }

    @Nested
    class WithRateLimit {

        private final Handler ok =
                request -> HttpResponse.json(200, Json.object().put("ok", true).build());
        private final AtomicLong now = new AtomicLong();

        @Test
        void letsRequestsThroughUpToTheLimit() {
            Handler handler = RateLimit.withRateLimit(ok, Limiter.create(LIMIT), LIMIT, now::get);
            for (int index = 0; index < 3; index += 1) {
                now.set(index);
                assertThat(handler.handle(request()).status()).isEqualTo(200);
            }
        }

        @Test
        void refusesTheRequestPastTheLimit() {
            Handler handler = RateLimit.withRateLimit(ok, Limiter.create(LIMIT), LIMIT, now::get);
            for (int index = 0; index < 3; index += 1) {
                now.set(index);
                handler.handle(request());
            }
            now.set(4);
            assertThatThrownBy(() -> handler.handle(request())).isInstanceOf(PolicyException.class);
            assertThatThrownBy(() -> handler.handle(request())).hasMessageContaining("too many requests");
        }

        @Test
        void letsThemThroughAgainInTheNextWindow() {
            Handler handler = RateLimit.withRateLimit(ok, Limiter.create(LIMIT), LIMIT, now::get);
            for (int index = 0; index < 4; index += 1) {
                now.set(index);
                catchThrowable(() -> handler.handle(request()));
            }
            now.set(2000);
            assertThat(handler.handle(request()).status()).isEqualTo(200);
        }

        @Test
        void countsTwoTokensSeparately() {
            Handler handler = RateLimit.withRateLimit(ok, Limiter.create(LIMIT), LIMIT, now::get);
            for (int index = 0; index < 3; index += 1) {
                now.set(index);
                handler.handle(request(Map.of("authorization", "Bearer a")));
            }
            now.set(4);
            assertThat(handler.handle(request(Map.of("authorization", "Bearer b")))
                            .status())
                    .isEqualTo(200);
        }
    }

    @Nested
    class DescribeLimit {

        @Test
        void readsTheLimitOutInSeconds() {
            assertThat(RateLimit.DEFAULT_LIMIT.describe()).isEqualTo("120 requests every 60 seconds");
        }
    }
}
