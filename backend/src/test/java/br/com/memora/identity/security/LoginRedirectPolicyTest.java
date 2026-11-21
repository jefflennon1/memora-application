package br.com.memora.identity.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LoginRedirectPolicyTest {

    @ParameterizedTest
    @ValueSource(strings = {"/", "/events/123", "/invites/abc?from=qr", "/a/b#c"})
    void acceptsRelativeFrontendPaths(String path) {
        assertThat(LoginRedirectPolicy.sanitizePath(path)).contains(path);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://evil.example", "http://localhost:3000/x", "//evil.example", "///evil.example",
            "/\\evil.example", "\\\\evil.example", "javascript:alert(1)", "events", "/a b", "/a\nb", "/a\tb", ""})
    void rejectsAbsoluteOrAmbiguousTargets(String target) {
        assertThat(LoginRedirectPolicy.sanitizePath(target)).isEmpty();
    }

    @org.junit.jupiter.api.Test
    void rejectsNullAndOverlongTargets() {
        assertThat(LoginRedirectPolicy.sanitizePath(null)).isEmpty();
        assertThat(LoginRedirectPolicy.sanitizePath("/" + "a".repeat(LoginRedirectPolicy.MAX_LENGTH))).isEmpty();
    }
}
