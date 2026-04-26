package com.miniurl.identity.service;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.KeyUse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

/**
 * Service for managing RSA key pairs for RS256 JWT signing.
 * Handles key generation, persistence, and JWK conversion.
 */
@Service
public class KeyService {
    private static final Logger logger = LoggerFactory.getLogger(KeyService.class);
    private static final String PRIVATE_KEY_FILE = "private_key.pem";
    private static final String PUBLIC_KEY_FILE = "public_key.pem";

    private KeyPair keyPair;

    @PostConstruct
    public void init() {
        try {
            this.keyPair = loadOrGenerateKeyPair();
            logger.info("RSA KeyPair initialized successfully");
        } catch (Exception e) {
            logger.error("Failed to initialize RSA KeyPair", e);
            throw new RuntimeException("Critical failure: Could not initialize security keys", e);
        }
    }

    private KeyPair loadOrGenerateKeyPair() throws Exception {
        if (Files.exists(Paths.get(PRIVATE_KEY_FILE)) && Files.exists(Paths.get(PUBLIC_KEY_FILE))) {
            logger.info("Loading existing RSA keys from disk...");
            return loadKeyPairFromDisk();
        }

        logger.info("Generating new RSA key pair...");
        RSAKeyGenerator gen = new RSAKeyGenerator(2048);
        RSAKey rsaKey = gen.generate();

        // Generate standard KeyPair from RSAKey
        java.security.PublicKey pubKey = java.security.KeyPairGenerator.getInstance("RSA")
            .generateKeyPair().getPublic();
        java.security.PrivateKey privKey = java.security.KeyPairGenerator.getInstance("RSA")
            .generateKeyPair().getPrivate();
        KeyPair pair = new KeyPair(pubKey, privKey);
        saveKeyPairToDisk(pair);
        return pair;
    }

    private void saveKeyPairToDisk(KeyPair pair) throws IOException {
        Files.write(Paths.get(PRIVATE_KEY_FILE), pair.getPrivate().getEncoded());
        Files.write(Paths.get(PUBLIC_KEY_FILE), pair.getPublic().getEncoded());
        logger.info("RSA keys saved to disk");
    }

    private KeyPair loadKeyPairFromDisk() throws Exception {
        byte[] privateKeyBytes = Files.readAllBytes(Paths.get(PRIVATE_KEY_FILE));
        byte[] publicKeyBytes = Files.readAllBytes(Paths.get(PUBLIC_KEY_FILE));

        KeyFactory kf = KeyFactory.getInstance("RSA");
        PrivateKey privateKey = kf.generatePrivate(new PKCS8EncodedKeySpec(privateKeyBytes));
        PublicKey publicKey = kf.generatePublic(new X509EncodedKeySpec(publicKeyBytes));

        return new KeyPair(publicKey, privateKey);
    }

    public PrivateKey getPrivateKey() {
        return keyPair.getPrivate();
    }

    public PublicKey getPublicKey() {
        return keyPair.getPublic();
    }

    /**
     * Returns the public key as a JWKSet for the JWKS endpoint.
     */
    public JWKSet getPublicJWKSet() {
        // Use the Nimbus RSAKeyGenerator to create a new JWK
        try {
            RSAKeyGenerator gen = new RSAKeyGenerator(2048);
            RSAKey key = gen.generate();
            return new JWKSet(key);
        } catch (Exception e) {
            logger.error("Failed to generate JWK", e);
            throw new RuntimeException("Failed to generate JWK", e);
        }
    }
}
