package com.miniurl.identity.service;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.security.PrivateKey;
import java.security.PublicKey;

/**
 * Service for managing RSA key pairs for RS256 JWT signing.
 * Generates a single key pair at startup and caches it in-memory.
 * The public key is exposed via the JWKS endpoint for API Gateway validation.
 */
@Service
public class KeyService {
    private static final Logger logger = LoggerFactory.getLogger(KeyService.class);

    private RSAKey rsaKey;

    @PostConstruct
    public void init() {
        try {
            this.rsaKey = new RSAKeyGenerator(2048)
                    .keyID("miniurl-rsa-key-1")
                    .generate();
            logger.info("RSA KeyPair initialized successfully with key ID: miniurl-rsa-key-1");
        } catch (Exception e) {
            logger.error("Failed to initialize RSA KeyPair", e);
            throw new RuntimeException("Critical failure: Could not initialize security keys", e);
        }
    }

    public PrivateKey getPrivateKey() {
        try {
            return rsaKey.toPrivateKey();
        } catch (Exception e) {
            throw new RuntimeException("Failed to get private key", e);
        }
    }

    public PublicKey getPublicKey() {
        try {
            return rsaKey.toPublicKey();
        } catch (Exception e) {
            throw new RuntimeException("Failed to get public key", e);
        }
    }

    /**
     * Returns the public key as a JWKSet for the JWKS endpoint.
     * Always returns the same stored key — NOT a freshly generated one.
     */
    public JWKSet getPublicJWKSet() {
        try {
            RSAKey publicJWK = rsaKey.toPublicJWK();
            return new JWKSet(publicJWK);
        } catch (Exception e) {
            logger.error("Failed to generate JWK set", e);
            throw new RuntimeException("Failed to generate JWK set", e);
        }
    }
}
