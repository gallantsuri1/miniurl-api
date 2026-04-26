package com.miniurl.identity;

import com.miniurl.identity.service.KeyService;
import com.nimbusds.jose.jwk.JWKSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.PrivateKey;
import java.security.PublicKey;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("KeyService Tests")
class KeyServiceTest {

    private KeyService keyService;

    @BeforeEach
    void setUp() {
        keyService = new KeyService();
        keyService.init();
    }

    @Test
    @DisplayName("init generates RSA key pair")
    void initGeneratesKeys() {
        assertNotNull(keyService.getPrivateKey());
        assertNotNull(keyService.getPublicKey());
    }

    @Test
    @DisplayName("getPublicJWKSet returns consistent key across calls")
    void getPublicJWKSetReturnsConsistentKey() {
        JWKSet firstCall = keyService.getPublicJWKSet();
        JWKSet secondCall = keyService.getPublicJWKSet();
        assertEquals(
            firstCall.getKeys().get(0).getKeyID(),
            secondCall.getKeys().get(0).getKeyID(),
            "JWK set should return the same key ID on every call"
        );
        assertEquals(
            firstCall.toJSONObject(false).toString(),
            secondCall.toJSONObject(false).toString(),
            "JWK set JSON representation should be identical across calls"
        );
    }

    @Test
    @DisplayName("getPrivateKey returns a usable PrivateKey")
    void getPrivateKeyReturnsUsableKey() {
        PrivateKey privateKey = keyService.getPrivateKey();
        assertNotNull(privateKey);
        assertEquals("RSA", privateKey.getAlgorithm());
    }

    @Test
    @DisplayName("getPublicKey returns a usable PublicKey")
    void getPublicKeyReturnsUsableKey() {
        PublicKey publicKey = keyService.getPublicKey();
        assertNotNull(publicKey);
        assertEquals("RSA", publicKey.getAlgorithm());
    }
}
