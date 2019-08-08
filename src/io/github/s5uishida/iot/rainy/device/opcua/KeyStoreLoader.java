package io.github.s5uishida.iot.rainy.device.opcua;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 * @author s5uishida
 *
 */
public final class KeyStoreLoader {
	private static final Logger LOG = LoggerFactory.getLogger(KeyStoreLoader.class);

	private final String keyStoreType;
	private final String keyStoreAlias;
	private final String certificate;
	private final char[] password;

	private X509Certificate x509Certificate;
	private KeyPair keyPair;

	public KeyStoreLoader(String keyStoreType, String keyStoreAlias, String certificate, String password) {
		this.keyStoreType = keyStoreType;
		this.keyStoreAlias = keyStoreAlias;
		this.certificate = certificate;
		this.password = password.toCharArray();
	}

	public X509Certificate getCertificate() {
		return x509Certificate;
	}

	public KeyPair getKeyPair() {
		return keyPair;
	}

	public KeyStoreLoader load() throws Exception {
		try {
			KeyStore keyStore = KeyStore.getInstance(keyStoreType);

			LOG.info("loading {}...", certificate);
			keyStore.load(Files.newInputStream(Paths.get(certificate)), password);
			LOG.info("loaded {}.", certificate);

			Key privateKey = keyStore.getKey(keyStoreAlias, password);

			if (privateKey instanceof PrivateKey) {
				x509Certificate = (X509Certificate)keyStore.getCertificate(keyStoreAlias);
				PublicKey publicKey = x509Certificate.getPublicKey();
				keyPair = new KeyPair(publicKey, (PrivateKey)privateKey);
			}

			return this;
		} catch (Exception e) {
			LOG.warn("caught - {}", e.toString());
			LOG.warn("failed to load {}.", certificate);
			throw e;
		}
	}
}
