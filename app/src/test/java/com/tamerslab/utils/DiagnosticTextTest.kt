package com.tamerslab.utils

import org.junit.Assert.*
import org.junit.Test

class DiagnosticTextTest {
    @Test fun normalFeedbackNeverIncludesAnExceptionEvenWhenItContainsReadableText() {
        val feedback = DiagnosticText.feedback("The operation could not be completed.",
            "SQLite failure at /private/database; token=secret", false, "Technical details")
        assertEquals("The operation could not be completed.", feedback)
    }

    @Test fun debugFeedbackRedactsCredentialsAndRetainsUsefulDiagnosticContext() {
        val feedback = DiagnosticText.feedback("Connection failed.",
            "HTTP 401 Authorization: Bearer abc.def API_key=my-key https://example.invalid/?token=private&status=401",
            true, "Technical details")
        assertTrue(feedback.contains("HTTP 401"))
        assertTrue(feedback.contains("status=401"))
        assertFalse(feedback.contains("abc.def"))
        assertFalse(feedback.contains("my-key"))
        assertFalse(feedback.contains("token=private"))
    }
}
