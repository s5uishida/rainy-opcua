package io.github.s5uishida.iot.rainy.device.opcua;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaMonitoredItem;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaSubscription;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaSubscriptionManager;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.DateTime;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.ULong;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.s5uishida.iot.rainy.device.IDevice;
import io.github.s5uishida.iot.rainy.device.opcua.data.NodeData;
import io.github.s5uishida.iot.rainy.device.opcua.data.UaData;
import io.github.s5uishida.iot.rainy.sender.IDataSender;

/*
 * @author s5uishida
 *
 */
public class OPCUA implements IDevice {
	private static final Logger LOG = LoggerFactory.getLogger(OPCUA.class);
	private static final UaConfig uaConfig = UaConfig.getInstance();

	private final String clientID;
	private final List<IDataSender> senders = new ArrayList<IDataSender>();

	private BlockingQueue<NotificationData> queue = new LinkedBlockingQueue<NotificationData>();

	private Thread notificationProcessThread = null;

	public OPCUA(String clientID) throws IOException {
		this.clientID = clientID;

		for (String fileName : UaServerConfig.getFileNames()) {
			UaServerConfig uaServerConfig = UaServerConfig.get(fileName);
			uaServerConfig.setUaMonitor(new UaMonitor(UaConfig.getInstance(), uaServerConfig));
		}

		if (uaConfig.getInfluxDB()) {
			senders.add(new UaInfluxDBSender());
			LOG.info("registered sender - {}", UaInfluxDBSender.class.getSimpleName());
		}
		if (uaConfig.getMqtt()) {
			senders.add(new UaMqttSender());
			LOG.info("registered sender - {}", UaMqttSender.class.getSimpleName());
		}
	}

	public void start() {
		for (IDataSender sender : senders) {
			try {
				sender.connect();
			} catch (IOException e) {
				LOG.warn("caught - {}", e.toString());
			}
		}
		LOG.info("sensing OPC-UA started.");

		for (String fileName : UaServerConfig.getFileNames()) {
			try {
				UaServerConfig uaServerConfig = UaServerConfig.get(fileName);
				UaMonitor monitor = uaServerConfig.getUaMonitor();

				monitor.connect();
				monitor.syncNodes();
				monitor.setSubscriptionListener(new UaSubscriptionListener(uaServerConfig));
				monitor.setMonitoredItems(new UaNotificationListener(uaServerConfig, queue));
			} catch (IOException | InterruptedException | ExecutionException e) {
				LOG.warn("caught - {}", e.toString(), e);
				continue;
			}
		}

		notificationProcessThread = new UaNotificationThread(senders, queue, clientID);
		notificationProcessThread.setDaemon(true);
		notificationProcessThread.start();
	}

	public void stop() {
		for (String fileName : UaServerConfig.getFileNames()) {
			UaServerConfig uaServerConfig = UaServerConfig.get(fileName);
			try {
				uaServerConfig.getUaMonitor().disconnect();
			} catch (IOException e) {
				LOG.warn("caught - {}", e.toString(), e);
			}
		}
		if (notificationProcessThread != null) {
			notificationProcessThread.interrupt();
			notificationProcessThread = null;
		}
		for (IDataSender sender : senders) {
			try {
				sender.disconnect();
			} catch (IOException e) {
				LOG.warn("caught - {}", e.toString());
			}
		}
		LOG.info("sensing OPC-UA stopped.");
	}

	public static void main(String[] args) throws Exception {
		OPCUA opcua = new OPCUA("rainy01");
		opcua.start();

		Thread.sleep(60000);

		opcua.stop();
	}

	class NotificationData {
		public final UaServerConfig uaServerConfig;
		public final UaSubscription uaSubscription;
		public final List<NodeData> nodeDataList;
		public final DateTime publishTime;

		public NotificationData(UaServerConfig uaServerConfig, UaSubscription uaSubscription,
				List<NodeData> nodeDataList, DateTime publishTime) {
			this.uaServerConfig = uaServerConfig;
			this.uaSubscription = uaSubscription;
			this.nodeDataList = nodeDataList;
			this.publishTime = publishTime;
		}
	}

	class UaNotificationListener implements UaSubscription.NotificationListener {
		private final Logger LOG = LoggerFactory.getLogger(UaNotificationListener.class);

		private final UaServerConfig uaServerConfig;
		private final BlockingQueue<NotificationData> queue;

		public UaNotificationListener(UaServerConfig uaServerConfig, BlockingQueue<NotificationData> queue) {
			this.uaServerConfig = uaServerConfig;
			this.queue = queue;
		}

		@Override
		public void onDataChangeNotification(UaSubscription subscription,
				List<UaMonitoredItem> monitoredItems,
				List<DataValue> dataValues,
				DateTime publishTime) {
			String endpointUrl = uaServerConfig.getUaMonitor().getUaClient().getStackClient().getConfig().getEndpoint().getEndpointUrl();
			LOG.debug("received {} monitoredItems from endpoint - {}", monitoredItems.size(), endpointUrl);

			List<NodeData> nodeDataList = new ArrayList<NodeData>();
			for (int i = 0; i < monitoredItems.size(); i++) {
				NodeId nodeId = monitoredItems.get(i).getReadValueId().getNodeId();
				DataValue dataValue = dataValues.get(i);

				NodeData nodeData = UaMonitor.dataValueToNodeData(nodeId, dataValue);
				nodeDataList.add(nodeData);
				LOG.trace("{} {}[{}] {}", nodeData.nodeId.toString(), nodeData.type, nodeData.typeId, nodeData.value);
			}
			queue.offer(new NotificationData(uaServerConfig, subscription, nodeDataList, publishTime));
		}

		@Override
		public void onEventNotification(UaSubscription subscription,
				List<UaMonitoredItem> monitoredItems,
				List<Variant[]> eventFields,
				DateTime publishTime) {
			String endpointUrl = uaServerConfig.getUaMonitor().getUaClient().getStackClient().getConfig().getEndpoint().getEndpointUrl();
			LOG.debug("received {} monitoredItems from endpoint - {}", monitoredItems.size(), endpointUrl);
			if (LOG.isTraceEnabled()) {
				for (int i = 0; i < monitoredItems.size(); i++) {
					NodeId nodeId = monitoredItems.get(i).getReadValueId().getNodeId();

					StringBuffer sb = new StringBuffer();
					for (Variant data : eventFields.get(i)) {
						sb.append(data.toString());
						sb.append(" ");
					}
					LOG.trace("{} {}", nodeId.toString(), sb.toString().trim());
				}
			}
		}

		@Override
		public void onKeepAliveNotification(UaSubscription subscription, DateTime publishTime) {
			LOG.debug("subscriptionId:{} publishTime:{}", subscription.getSubscriptionId(), publishTime.getJavaDate());
		}

		@Override
		public void onStatusChangedNotification(UaSubscription subscription, StatusCode status) {
			LOG.debug("subscriptionId:{} statusCode:{}", subscription.getSubscriptionId(), status.toString());
		}
	}

	class UaNotificationThread extends Thread {
		private final Logger LOG = LoggerFactory.getLogger(UaNotificationThread.class);

		private final String dateFormat = "yyyy-MM-dd HH:mm:ss.SSS";
		private final SimpleDateFormat sdf = new SimpleDateFormat(dateFormat);

		private final List<IDataSender> senders;
		private final BlockingQueue<NotificationData> queue;
		private final String clientID;

		public UaNotificationThread(List<IDataSender> senders, BlockingQueue<NotificationData> queue, String clientID) {
			this.senders = senders;
			this.queue = queue;
			this.clientID = clientID;
		}

		private String getDeviceID(UaServerConfig uaServerConfig) {
			StringBuilder sb = new StringBuilder();
			sb.append(uaServerConfig.getEndpointIP());
			sb.append(":");
			sb.append(uaServerConfig.getEndpointPort());

			String serverName = uaServerConfig.getServerName();
			if (serverName != null && !serverName.isEmpty()) {
				sb.append("/").append(serverName);
			}
			return sb.toString().toLowerCase();
		}

		private Object getJavaData(Object object) {
			if (object instanceof UInteger) {
				return ((UInteger)object).longValue();
			} else if (object instanceof ULong) {
				return ((ULong)object).longValue();
			} else if (object instanceof UShort) {
				return ((UShort)object).longValue();
			} else {
				return object;
			}
		}

		@Override
		public void run() {
			String logPrefix = "";
			try {
				while (true) {
					logPrefix = "";
					try {
						NotificationData notificationData = queue.take();

						Date date = notificationData.publishTime.getJavaDate();
						String dateString = sdf.format(date);

						logPrefix = notificationData.uaServerConfig.getLogPrefix();

						UaData uaData = new UaData();

						uaData.clientID = clientID;
						uaData.deviceID = getDeviceID(notificationData.uaServerConfig);
						uaData.samplingDate = dateString;
						uaData.samplingTimeMillis = date.getTime();

						uaData.opcua = new HashMap<String, List<Map<String, Object>>>();

						for (NodeData nodeData : notificationData.nodeDataList) {
							if (nodeData.typeId == -1) {
								continue;
							}

							String key = nodeData.nodeId.getNamespaceIndex() + "," + nodeData.nodeId.getIdentifier();
							List<Map<String, Object>> list = uaData.opcua.get(key);
							if (list == null) {
								list = new ArrayList<Map<String, Object>>();
								uaData.opcua.put(key, list);
							}

							Map<String, Object> nodeDataMap = new HashMap<String, Object>();
							nodeDataMap.put("value", getJavaData(nodeData.value));
							nodeDataMap.put("sourceTime", sdf.format(nodeData.dataValue.getSourceTime().getJavaDate()));
							nodeDataMap.put("sourceTimeMillis", nodeData.dataValue.getSourceTime().getJavaTime());
							nodeDataMap.put("serverTime", sdf.format(nodeData.dataValue.getServerTime().getJavaDate()));
							nodeDataMap.put("serverTimeMillis", nodeData.dataValue.getServerTime().getJavaTime());

							list.add(nodeDataMap);

							LOG.trace(logPrefix + "key:{} value:{} sourceTime:{} serverTime:{}",
									key, nodeDataMap.get("value"), nodeDataMap.get("sourceTime"), nodeDataMap.get("serverTime"));
						}

						for (IDataSender sender : senders) {
							try {
								if (sender.isConnected()) {
									sender.send(uaData);
								}
							} catch (IOException e) {
								LOG.warn(logPrefix + "{} caught - {}", sender.getClass().getSimpleName(), e.toString());
							}
						}
					} catch (InterruptedException e) {
						throw e;
					} catch (Exception e) {
						LOG.warn(logPrefix + "caught - {}", e.toString());
					}
				}
			} catch (InterruptedException e) {
				LOG.info(logPrefix + "caught - {}", e.toString());
				return;
			}
		}
	}

	public class UaSubscriptionListener implements UaSubscriptionManager.SubscriptionListener {
		private final Logger LOG = LoggerFactory.getLogger(UaSubscriptionListener.class);

		private final UaServerConfig uaServerConfig;

		private final AtomicBoolean isBusy = new AtomicBoolean(false);

		public UaSubscriptionListener(UaServerConfig uaServerConfig) {
			this.uaServerConfig = uaServerConfig;
		}

		@Override
		public void onKeepAlive(UaSubscription subscription, DateTime publishTime) {
			LOG.debug("subscriptionId:{} publishTime:{}", subscription.getSubscriptionId(), publishTime.getJavaDate());
		}

		@Override
		public void onStatusChanged(UaSubscription subscription, StatusCode statusCode) {
			LOG.debug("subscriptionId:{} statusCode:{}", subscription.getSubscriptionId(), statusCode);
		}

		@Override
		public void onPublishFailure(UaException exception) {
			LOG.debug("{}", exception.toString());
		}

		@Override
		public void onNotificationDataLost(UaSubscription subscription) {
			LOG.debug("subscriptionId:{}", subscription.getSubscriptionId());
		}

		@Override
		public void onSubscriptionTransferFailed(UaSubscription subscription, StatusCode statusCode) {
			LOG.info("called onSubscriptionTransferFailed - subscriptionId:{} statusCode:{}", subscription.getSubscriptionId(), statusCode);
			if ((statusCode.getValue() == StatusCodes.Bad_SubscriptionIdInvalid) ||
					(statusCode.getValue() == StatusCodes.Bad_MessageNotAvailable)) {
				if (isBusy.compareAndSet(false, true)) {
					String endpointUrl = null;
					try {
						UaMonitor monitor = uaServerConfig.getUaMonitor();

						endpointUrl = uaServerConfig.getUaMonitor().getUaClient().getStackClient().getConfig().getEndpoint().getEndpointUrl();
						LOG.info("re-establishing the connection to {}...", endpointUrl);

						monitor.setMonitoredItems();

						LOG.info("re-established the connection to {}.", endpointUrl);
					} catch (InterruptedException | ExecutionException | IOException e) {
						LOG.warn("failed to re-establish the connection to {} - {}", endpointUrl, e.toString());
					} finally {
						isBusy.set(false);
					}
				}
			}
		}
	}
}
