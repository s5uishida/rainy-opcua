package io.github.s5uishida.iot.rainy.device.opcua;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.milo.opcua.sdk.client.api.identity.AnonymousProvider;
import org.eclipse.milo.opcua.sdk.client.api.identity.IdentityProvider;
import org.eclipse.milo.opcua.sdk.client.api.identity.UsernameProvider;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.s5uishida.iot.rainy.device.opcua.data.NodeIdDepth;
import io.github.s5uishida.iot.rainy.util.AbstractConfig;
import io.github.s5uishida.iot.rainy.util.ConfigParams;

/*
 * @author s5uishida
 *
 */
public class UaServerConfig extends AbstractConfig {
	private static final Logger LOG = LoggerFactory.getLogger(UaServerConfig.class);
	private static final UaConfig uaConfig = UaConfig.getInstance();

	public static final String USE_KEY						= "use";
	public static final String SERVER_NAME_KEY				= "serverName";
	public static final String ENDPOINT_IP_KEY				= "endpointIP";
	public static final String ENDPOINT_PORT_KEY			= "endpointPort";
	public static final String SECURITY_POLICY_KEY			= "securityPolicy";
	public static final String SECURITY_MODE_KEY			= "securityMode";
	public static final String USER_NAME_KEY					= "userName";
	public static final String PASSWORD_KEY					= "password";
	public static final String REQUEST_TIMEOUT_KEY			= "requestTimeout";
	public static final String SESSION_TIMEOUT_KEY			= "sessionTimeout";
	public static final String PUBLISHING_INTERVAL_KEY		= "publishingInterval";
	public static final String SAMPLING_INTERVAL_KEY		= "samplingInterval";
	public static final String QUEUE_SIZE_KEY				= "queueSize";
	public static final String DATA_CHANGE_TRIGGER_KEY		= "dataChangeTrigger";
	public static final String NODE_IDS_KEY					= "nodeIDs";

	private static final Map<String, UaServerConfig> uaServerConfigMap = new HashMap<String, UaServerConfig>();

	private final List<NodeIdDepth> nodeIdDepths = new ArrayList<NodeIdDepth>();

	private final String logPrefix;

	private UaMonitor monitor;

	static {
		FilenameFilter filter = new FilenameFilter() {
			public boolean accept(File file, String fileName){
				if(fileName.endsWith(".properties")) {
					return true;
				} else {
					return false;
				}
			}
		};

		File[] files = new File(uaConfig.getDir() + "/" + UaConfig.SERVER_DIR).listFiles(filter);
		if (files != null) {
			for (File file : files) {
				if (file.isFile()) {
					UaServerConfig.put(file.getName());
				}
			}
		}
	}

	private void loadNodeIds() {
		String[] list = getConfig(NODE_IDS_KEY, "").split("\\s+");

		for (String line : list) {
			String[] items = line.split("[,\\s]+");
			if (items == null || items.length < 3) {
				continue;
			}

			int ns = -1;
			try {
				ns = Integer.parseInt(items[0]);
			} catch (NumberFormatException  e) {
				LOG.warn(logPrefix + "caught - {}", e.toString());
				continue;
			}

			int id = -1;
			try {
				id = Integer.parseInt(items[1]);
			} catch (NumberFormatException  e) {
			}

			int depth = -1;
			try {
				depth = Integer.parseInt(items[2]);
			} catch (NumberFormatException  e) {
				LOG.warn(logPrefix + "caught - {}", e.toString());
				continue;
			}

			if (id >= 0) {
				NodeIdDepth nodeIdDepth = new NodeIdDepth(new NodeId(ns, id), depth);
				nodeIdDepths.add(nodeIdDepth);
			} else {
				NodeIdDepth nodeIdDepth  = new NodeIdDepth(new NodeId(ns, items[1]), depth);
				nodeIdDepths.add(nodeIdDepth);
			}
		}
	}

	public UaServerConfig(String dirParam, String fileName) {
		super(dirParam, fileName);
		logPrefix = "[" + getServerName() + "] " + getEndpointIP() + ":" + getEndpointPort() + " ";
		loadNodeIds();
	}

	public String getLogPrefix() {
		return logPrefix;
	}

	public static UaServerConfig put(String fileName) {
		if (!uaServerConfigMap.containsKey(fileName)) {
			UaServerConfig uaServerConfig = new UaServerConfig(ConfigParams.CONFIG_DIR_PARAM, UaConfig.SERVER_DIR + "/" + fileName);
			if (!uaServerConfig.getUse()) {
				return null;
			}
			uaServerConfigMap.put(fileName, uaServerConfig);

			LOG.info("{} registered [{}] {}:{}",
					fileName, uaServerConfig.getServerName(), uaServerConfig.getEndpointIP(), uaServerConfig.getEndpointPort());
			return uaServerConfig;
		}
		return null;
	}

	public static UaServerConfig get(String fileName) {
		return uaServerConfigMap.get(fileName);
	}

	public static Set<String> getFileNames() {
		return uaServerConfigMap.keySet();
	}

	public static Map<String, UaServerConfig> getUaServerConfigMap() {
		return uaServerConfigMap;
	}

	public boolean getUse() {
		return getConfig(USE_KEY, false);
	}

	public String getServerName() {
		return getConfig(SERVER_NAME_KEY, "");
	}

	public String getEndpointIP() {
		return getConfig(ENDPOINT_IP_KEY, "127.0.0.1");
	}

	public int getEndpointPort() {
		return getConfig(ENDPOINT_PORT_KEY, 4840);
	}

	public SecurityPolicy getSecurityPolicy() {
		String securityPolicy = getConfig(SECURITY_POLICY_KEY, "None");
		if (securityPolicy.equals("Basic128Rsa15")) {
			return SecurityPolicy.Basic128Rsa15;
		} else if (securityPolicy.equals("Basic256")) {
			return SecurityPolicy.Basic256;
		} else if (securityPolicy.equals("Basic256Sha256")) {
			return SecurityPolicy.Basic256Sha256;
		} else if (securityPolicy.equals("None")) {
			return SecurityPolicy.None;
		} else {
			return SecurityPolicy.None;
		}
	}

	public MessageSecurityMode getSecurityMode() {
		String securityMode = getConfig(SECURITY_MODE_KEY, "None");
		if (securityMode.equals("None")) {
			return MessageSecurityMode.None;
		} else if (securityMode.equals("Sign")) {
			return MessageSecurityMode.Sign;
		} else if (securityMode.equals("SignAndEncrypt")) {
			return MessageSecurityMode.SignAndEncrypt;
		} else {
			return MessageSecurityMode.None;
		}
	}

	public String getUserName() {
		return getConfig(USER_NAME_KEY, null);
	}

	public String getPassword() {
		return getConfig(PASSWORD_KEY, null);
	}

	public IdentityProvider getIdentityProvider() {
		IdentityProvider identityProvider;
		final String userName = getUserName();
		final String password = getPassword();
		if ((userName == null || userName.isEmpty()) && (password == null || password.isEmpty())) {
			identityProvider = new AnonymousProvider();
		} else {
			identityProvider = new UsernameProvider(userName, password);
		}
		return identityProvider;
	}

	public int getRequestTimeout() {
		return getConfig(REQUEST_TIMEOUT_KEY, 10000);
	}

	public int getSessionTimeout() {
		return getConfig(SESSION_TIMEOUT_KEY, 10000);
	}

	public int getPublishingInterval() {
		return getConfig(PUBLISHING_INTERVAL_KEY, 1000);
	}

	public int getSamplingInterval() {
		return getConfig(SAMPLING_INTERVAL_KEY, 500);
	}

	public int getQueueSize() {
		return getConfig(QUEUE_SIZE_KEY, 10);
	}

	public int getDataChangeTrigger() {
		return getConfig(DATA_CHANGE_TRIGGER_KEY, 1);
	}

	public List<NodeIdDepth> getNodeIdDepths() {
		return nodeIdDepths;
	}

	public void setUaMonitor(UaMonitor monitor) {
		this.monitor = monitor;
	}

	public UaMonitor getUaMonitor() {
		return monitor;
	}

	@Override
	public String toString() {
		return logPrefix +
				SECURITY_POLICY_KEY + ":" + getSecurityPolicy() + " " +
				SECURITY_MODE_KEY + ":" + getSecurityMode() + " " +
				USER_NAME_KEY + ":" + getUserName() + " " +
				REQUEST_TIMEOUT_KEY + ":" + getRequestTimeout() + " " +
				SESSION_TIMEOUT_KEY + ":" + getSessionTimeout() + " " +
				PUBLISHING_INTERVAL_KEY + ":" + getPublishingInterval() + " " +
				SAMPLING_INTERVAL_KEY + ":" + getSamplingInterval() + " " +
				QUEUE_SIZE_KEY + ":" + getQueueSize() + " " +
				DATA_CHANGE_TRIGGER_KEY + ":" + getDataChangeTrigger() + " " +
				NODE_IDS_KEY + ":" + getNodeIdDepths().toString();
	}
}
