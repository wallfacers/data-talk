package com.datatalk.application.persistence;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecretVaultTest {

    private static final byte[] MASTER = new byte[32]; // 256-bit key, all zeros for test determinism

    @Test
    void roundTripsSecret() {
        SecretVault vault = new SecretVault(MASTER);
        byte[] cipher = vault.seal("hunter2");
        String plain = vault.open(cipher);
        assertThat(plain).isEqualTo("hunter2");
    }

    @Test
    void differentCiphertextsForSamePlaintext() {
        SecretVault vault = new SecretVault(MASTER);
        byte[] c1 = vault.seal("hunter2");
        byte[] c2 = vault.seal("hunter2");
        assertThat(c1).isNotEqualTo(c2);  // fresh nonce each call
    }

    @Test
    void rejectsTamperedCiphertext() {
        SecretVault vault = new SecretVault(MASTER);
        byte[] cipher = vault.seal("hunter2");
        cipher[cipher.length - 1] ^= 1;
        assertThatThrownBy(() -> vault.open(cipher))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsMasterKeyOfWrongLength() {
        assertThatThrownBy(() -> new SecretVault(new byte[16]))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
