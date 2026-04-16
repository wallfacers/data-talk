package com.datatalk.adapter.config;

import com.datatalk.application.persistence.SecretVault;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HexFormat;

@Configuration
public class SecretVaultConfig {
    @Bean
    public SecretVault secretVault(@Value("${datatalk.master-key-hex:0000000000000000000000000000000000000000000000000000000000000000}") String hex) {
        byte[] key = HexFormat.of().parseHex(hex);
        return new SecretVault(key);
    }
}
