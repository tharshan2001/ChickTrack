package com.chick.app.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.jwk.RSAKey;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

class SigningKeyLoaderTest {

	@Test
	void loadsTheSameKeyEveryTimeFromPem() throws Exception {
		KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
		generator.initialize(2048);
		byte[] der = generator.generateKeyPair().getPrivate().getEncoded();
		String pem = "-----BEGIN PRIVATE KEY-----\n"
				+ Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(der)
				+ "\n-----END PRIVATE KEY-----\n";
		ByteArrayResource resource = new ByteArrayResource(pem.getBytes(StandardCharsets.US_ASCII));

		RSAKey first = SigningKeyLoader.load(resource, "", false);
		RSAKey second = SigningKeyLoader.load(resource, "", false);

		assertThat(first.isPrivate()).isTrue();
		assertThat(first.getKeyID()).isEqualTo(second.getKeyID());
		assertThat(first.toRSAPublicKey()).isEqualTo(second.toRSAPublicKey());
	}

	@Test
	void refusesToGenerateWhenNotAllowed() {
		assertThatThrownBy(() -> SigningKeyLoader.load(null, null, false))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("No JWT signing key configured");
	}

	@Test
	void generatesAKeyForLocalDevelopment() {
		assertThat(SigningKeyLoader.load(null, null, true).isPrivate()).isTrue();
	}
}
