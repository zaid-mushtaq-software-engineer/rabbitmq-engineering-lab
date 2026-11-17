package com.example.notificationlab.contracts;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RecipientMaskerTest {

    @Test
    void masksEmailLocalPart() {
        assertThat(RecipientMasker.mask("alice@example.com")).isEqualTo("a***@example.com");
    }

    @Test
    void masksSmsE164() {
        assertThat(RecipientMasker.mask("+15550100099")).isEqualTo("+15***0099");
    }

    @Test
    void handlesBlank() {
        assertThat(RecipientMasker.mask("  ")).isEqualTo("***");
    }
}