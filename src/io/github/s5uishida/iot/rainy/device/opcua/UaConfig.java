package io.github.s5uishida.iot.rainy.device.opcua;

import io.github.s5uishida.iot.rainy.util.AbstractConfig;
import io.github.s5uishida.iot.rainy.util.ConfigParams;

/*
 * @author s5uishida
 *
 */
public class UaConfig extends AbstractConfig {
	public static final String NAME							= "rainy opc-ua client";
	public static final String URI							= "urn:eclipse:milo:rainy:client";
	public static final String VERSION						= "0.1.3";
	public static final String SERVER_DIR					= "opcua";
	public static final String INFLUXDB_KEY					= "influxDB";
	public static final String MQTT_KEY						= "mqtt";
	public static final String PRETTY_PRINTING_KEY			= "prettyPrinting";
	public static final String KEY_STORE_TYPE_KEY			= "keyStoreType";
	public static final String KEY_STORE_ALIAS_KEY 			= "keyStoreAlias";
	public static final String KEY_STORE_PASSWORD_KEY		= "keyStorePassword";
	public static final String CERTIFICATE_KEY 				= "certificate";

	private static UaConfig config = null;

	private UaConfig(String dirParam, String fileName) {
		super(dirParam, fileName);
	}

	public static UaConfig getInstance() {
		if (config == null) {
			config = new UaConfig(ConfigParams.CONFIG_DIR_PARAM, ConfigParams.OPCUA_CONFIG_FILE);
		}
		return config;
	}

	public String getName() {
		return NAME;
	}

	public String getUri() {
		return URI;
	}

	public String getVersion() {
		return VERSION;
	}

	public boolean getInfluxDB() {
		return getConfig(INFLUXDB_KEY, false);
	}

	public boolean getMqtt() {
		return getConfig(MQTT_KEY, false);
	}

	public boolean getPrettyPrinting() {
		return getConfig(PRETTY_PRINTING_KEY, false);
	}

	public String getKeyStoreType() {
		return getConfig(KEY_STORE_TYPE_KEY, null);
	}

	public String getKeyStoreAlias() {
		return getConfig(KEY_STORE_ALIAS_KEY, null);
	}

	public String getKeyStorePassword() {
		return getConfig(KEY_STORE_PASSWORD_KEY, null);
	}

	public String getCertificate() {
		return getConfig(CERTIFICATE_KEY, null);
	}
}
