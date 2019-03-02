package org.zalando.riptide;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ConditionalIdempotentMethodDetectorTest {

    private final MethodDetector unit = new ConditionalIdempotentMethodDetector();

    @ParameterizedTest
    @CsvSource({
            "If-Match,xyzzy",
            "if-match,xyzzy",
            "If-None-Match,*",
            "if-none-match,*",
            "If-Unmodified-Since,Sat, 29 Oct 1994 19:43:31 GMT",
            "if-unmodified-since,Sat, 29 Oct 1994 19:43:31 GMT",
    })
    void shouldBeIdempotent(final String name, final String value) {
        assertTrue(unit.test(arguments(name, value)));
    }

    @ParameterizedTest
    @CsvSource({
            "If-Modified-Since,Sat, 29 Oct 1994 19:43:31 GMT",
            "If-None-Match,xyzzy",
            "Date,Sat, 29 Oct 1994 19:43:31 GMT"
    })
    void shouldNotBeIdempotent(final String name, final String value) {
        assertFalse(unit.test(arguments(name, value)));
    }

    RequestArguments arguments(final String name, final String value) {
        return RequestArguments.create()
                .withHeader(name, value);
    }

}
