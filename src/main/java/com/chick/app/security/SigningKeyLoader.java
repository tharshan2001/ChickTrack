package com.chick.app.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.RSAKey;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.KeyPair;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;

/**
 * Loads the RSA key that signs access tokens. A persistent key keeps tokens valid across
 * restarts and across several API instances.
 */
final class SigningKeyLoader {

	private static final Logger log = LoggerFactory.getLogger(SigningKeyLoader.class);

	private SigningKeyLoader() {
	}

	/**
	 * @param privateKey PKCS#8 PEM resource ({@code -----BEGIN PRIVATE KEY-----}); may be null
	 * @param keyId key id published in the JWK set; derived from the key when blank
	 * @param allowGenerated whether a throwaway key may be generated when no key is configured
	 */
	static RSAKey load(Resource privateKey, String keyId, boolean allowGenerated) {
		if (privateKey != null && privateKey.exists()) {
			return fromPem(privateKey, keyId);
		}
		if (!allowGenerated) {
			throw new IllegalStateException(
					"No JWT signing key configured. Set app.oauth.jwk.private-key (OAUTH_JWK_PRIVATE_KEY) to a PKCS#8 PEM file.");
		}
		log.warn("Using a generated JWT signing key. Tokens become invalid on restart; configure "
				+ "app.oauth.jwk.private-key before any shared deployment.");
		return generated();
	}

	private static RSAKey fromPem(Resource resource, String keyId) {
		try (InputStream in = resource.getInputStream()) {
			String pem = new String(in.readAllBytes(), StandardCharsets.US_ASCII);
			if (pem.contains("BEGIN RSA PRIVATE KEY")) {
				throw new IllegalStateException("JWT signing key must be PKCS#8 (BEGIN PRIVATE KEY). Convert with: "
						+ "openssl pkcs8 -topk8 -nocrypt -in key.pem -out key-pkcs8.pem");
			}
			String body = pem.replaceAll("-----(BEGIN|END) PRIVATE KEY-----", "").replaceAll("\\s", "");
			KeyFactory factory = KeyFactory.getInstance("RSA");
			RSAPrivateCrtKey privateKey = (RSAPrivateCrtKey) factory.generatePrivate(
					new PKCS8EncodedKeySpec(Base64.getDecoder().decode(body)));
			RSAPublicKey publicKey = (RSAPublicKey) factory.generatePublic(
					new RSAPublicKeySpec(privateKey.getModulus(), privateKey.getPublicExponent()));
			RSAKey.Builder builder = new RSAKey.Builder(publicKey).privateKey(privateKey);
			RSAKey key = (keyId == null || keyId.isBlank())
					? builder.keyIDFromThumbprint().build()
					: builder.keyID(keyId).build();
			log.info("Loaded JWT signing key '{}'", key.getKeyID());
			return key;
		}
		catch (IOException | GeneralSecurityException | JOSEException | IllegalArgumentException | ClassCastException ex) {
			throw new IllegalStateException("Unable to load JWT signing key from " + resource, ex);
		}
	}

	private static RSAKey generated() {
		try {
			KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
			generator.initialize(2048);
			KeyPair pair = generator.generateKeyPair();
			return new RSAKey.Builder((RSAPublicKey) pair.getPublic())
					.privateKey(pair.getPrivate())
					.keyID(UUID.randomUUID().toString())
					.build();
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException("Unable to generate RSA key", ex);
		}
	}
}
