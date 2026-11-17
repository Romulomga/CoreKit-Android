package io.github.romulomga.corekit.network

import org.junit.Assert.assertEquals
import org.junit.Test

/** Os mesmos casos do `URLRedactionTests` do iOS: a query e os segmentos de dado digitado não saem numa URL de log. */
class UrlRedactionTest {
    private val redaction = UrlRedaction(sensitiveParents = setOf("phone-number", "code", "name"), routeWords = setOf("check"))

    @Test
    fun theQueryAndTheFragmentNeverStay() {
        assertEquals(
            "https://api.example.com/v1/users/exists",
            UrlRedaction().maskUrl("https://api.example.com/v1/users/exists?email=a@b.com#top")
        )
    }

    @Test
    fun theSegmentAfterASensitiveParentIsMasked() {
        assertEquals(
            "https://api.example.com/v1/auth/phone-number/***/code/***",
            redaction.maskUrl("https://api.example.com/v1/auth/phone-number/5521999990000/code/123456")
        )
    }

    @Test
    fun routeWordsAndIdsStay() {
        assertEquals(
            "https://api.example.com/v1/verification/code/check/42",
            redaction.maskUrl("https://api.example.com/v1/verification/code/check/42")
        )
        assertEquals("https://api.example.com/v1/users/42/posts", redaction.maskUrl("https://api.example.com/v1/users/42/posts"))
    }

    @Test
    fun parentsMatchWhateverTheirCase() {
        assertEquals("https://api.example.com/users/Name/***", redaction.maskUrl("https://api.example.com/users/Name/Maria"))
    }

    @Test
    fun aUrlWithoutAPathKeepsItsHost() {
        assertEquals("https://api.example.com", redaction.maskUrl("https://api.example.com?token=1"))
    }

    @Test
    fun everyUrlOfALogLineIsMasked() {
        assertEquals(
            "<-- 200 https://api.example.com/v1/users/exists (12ms)",
            redaction.maskUrlsIn("<-- 200 https://api.example.com/v1/users/exists?email=a%40b.com (12ms)")
        )
        assertEquals("Content-Type: application/json", redaction.maskUrlsIn("Content-Type: application/json"))
    }

    @Test
    fun aPathAloneIsMaskedToo() {
        assertEquals("/v1/users/name/{value}/42", redaction.maskPath("/v1/users/name/ana/42", mask = "{value}"))
    }
}
