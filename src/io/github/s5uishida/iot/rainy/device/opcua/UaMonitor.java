package io.github.s5uishida.iot.rainy.device.opcua;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.api.config.OpcUaClientConfig;
import org.eclipse.milo.opcua.sdk.client.api.config.OpcUaClientConfigBuilder;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaMonitoredItem;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaSubscription;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaSubscriptionManager;
import org.eclipse.milo.opcua.stack.client.DiscoveryClient;
import org.eclipse.milo.opcua.stack.core.AttributeId;
import org.eclipse.milo.opcua.stack.core.BuiltinDataType;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.types.builtin.ByteString;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.ExtensionObject;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned;
import org.eclipse.milo.opcua.stack.core.types.enumerated.BrowseDirection;
import org.eclipse.milo.opcua.stack.core.types.enumerated.BrowseResultMask;
import org.eclipse.milo.opcua.stack.core.types.enumerated.DataChangeTrigger;
import org.eclipse.milo.opcua.stack.core.types.enumerated.DeadbandType;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MonitoringMode;
import org.eclipse.milo.opcua.stack.core.types.enumerated.NodeClass;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.eclipse.milo.opcua.stack.core.types.structured.BrowseDescription;
import org.eclipse.milo.opcua.stack.core.types.structured.BrowseResult;
import org.eclipse.milo.opcua.stack.core.types.structured.DataChangeFilter;
import org.eclipse.milo.opcua.stack.core.types.structured.EndpointDescription;
import org.eclipse.milo.opcua.stack.core.types.structured.MonitoredItemCreateRequest;
import org.eclipse.milo.opcua.stack.core.types.structured.MonitoringParameters;
import org.eclipse.milo.opcua.stack.core.types.structured.ReadValueId;
import org.eclipse.milo.opcua.stack.core.types.structured.ReferenceDescription;
import org.eclipse.milo.opcua.stack.core.util.ConversionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.s5uishida.iot.rainy.device.opcua.data.NodeData;
import io.github.s5uishida.iot.rainy.device.opcua.data.NodeIdDepth;

/*
 * @author s5uishida
 *
 */
public class UaMonitor {
	private static final Logger LOG = LoggerFactory.getLogger(UaMonitor.class);

	private final UaConfig uaConfig;
	private final UaServerConfig uaServerConfig;
	private final String logPrefix;

	private final AtomicBoolean isBusy = new AtomicBoolean();
	private final AtomicLong clientHandle = new AtomicLong(1L);

	private OpcUaClient client;

	private ExtensionObject dataChangeFilterStatus;
	private ExtensionObject dataChangeFilterStatusValue;
	private ExtensionObject dataChangeFilterStatusValueTimestamp;

	private UaSubscription.NotificationListener notificationListener;
	private UaSubscriptionManager.SubscriptionListener subscriptionListener;

	private final List<NodeId> nodeIds = new ArrayList<NodeId>();

	public UaMonitor(UaConfig uaConfig, UaServerConfig uaServerConfig) {
		this.uaConfig = uaConfig;
		this.uaServerConfig = uaServerConfig;
		this.logPrefix = "[" + uaServerConfig.getServerName() + "] " + uaServerConfig.getEndpointIP() + ":" + uaServerConfig.getEndpointPort() + " ";
	}

	public UaConfig getUaConfig() {
		return uaConfig;
	}

	public UaServerConfig getUaServerConfig() {
		return uaServerConfig;
	}

	private void setDataChangeFilter() {
		if (client == null) {
			dataChangeFilterStatus = null;
			dataChangeFilterStatusValue = null;
			dataChangeFilterStatusValueTimestamp = null;
		} else {
			dataChangeFilterStatus = ExtensionObject.encode(client.getSerializationContext(),
					new DataChangeFilter(DataChangeTrigger.Status,	Unsigned.uint(DeadbandType.None.getValue()), 0.0));
			dataChangeFilterStatusValue = ExtensionObject.encode(client.getSerializationContext(),
					new DataChangeFilter(DataChangeTrigger.StatusValue, Unsigned.uint(DeadbandType.None.getValue()), 0.0));
			dataChangeFilterStatusValueTimestamp = ExtensionObject.encode(client.getSerializationContext(),
					new DataChangeFilter(DataChangeTrigger.StatusValueTimestamp, Unsigned.uint(DeadbandType.None.getValue()), 0.0));
		}
	}

	public void setSubscriptionListener(UaSubscriptionManager.SubscriptionListener subscriptionListener) {
		this.subscriptionListener = Objects.requireNonNull(subscriptionListener);
		if (client == null) {
			throw new IllegalStateException(logPrefix + "client not ready yet.");
		}
		client.getSubscriptionManager().addSubscriptionListener(this.subscriptionListener);
	}

	public OpcUaClient getUaClient() {
		return client;
	}

	private <U> U runSafe(Future<U> future) throws ExecutionException, InterruptedException, TimeoutException {
		try {
			return future.get(uaServerConfig.getRequestTimeout(), TimeUnit.MILLISECONDS);
		} catch (ExecutionException | InterruptedException | TimeoutException e) {
			future.cancel(true);
			throw e;
		}
	}

	private void shutdown(OpcUaClient client) throws IOException {
		if (client == null) {
			return;
		}
		try {
			runSafe(client.disconnect());
		} catch (ExecutionException | InterruptedException | TimeoutException e) {
			throw new IOException(logPrefix + "failed to disconnect.", e);
		}
	}

	public void connect() throws IOException {
		if (!isBusy.compareAndSet(false, true)) {
			throw new IOException(logPrefix + "busy.");
		}

		OpcUaClient newClient = null;
		EndpointDescription endpoint = null;
		try {
			String serverName = uaServerConfig.getServerName();

			StringBuilder endpointBuilder = new StringBuilder();
			endpointBuilder.append("opc.tcp://").append(uaServerConfig.getEndpointIP());
			endpointBuilder.append(":").append(uaServerConfig.getEndpointPort());

			if (!(serverName == null || serverName.isEmpty())) {
				endpointBuilder.append("/").append(serverName);
			}

			String endpointString = endpointBuilder.toString();
			LOG.info(logPrefix + "connecting to {}...", endpointString);

			List<EndpointDescription> endpoints = runSafe(DiscoveryClient.getEndpoints(endpointString));
			LOG.info(logPrefix + "connected to {}.", endpointString);

			if (LOG.isTraceEnabled()) {
				LOG.trace(logPrefix + "Endpoint descriptions.");
				for (EndpointDescription ep : endpoints) {
					LOG.trace(logPrefix + "  {}", ep.toString());
				}
			}

			Optional<EndpointDescription> optEndpoint = endpoints.stream().filter(
					e -> (e.getEndpointUrl().equals(endpointString) &&
							e.getSecurityPolicyUri().equals(uaServerConfig.getSecurityPolicy().getUri()) &&
							e.getSecurityMode().equals(uaServerConfig.getSecurityMode())))
					.findFirst();
			if (optEndpoint.isPresent()) {
				endpoint = optEndpoint.get();
			} else {
				endpoint = endpoints.stream().filter(
						e -> (e.getSecurityPolicyUri().equals(uaServerConfig.getSecurityPolicy().getUri()) &&
								e.getSecurityMode().equals(uaServerConfig.getSecurityMode())))
						.findFirst().orElseThrow(() -> new IOException(logPrefix + "failed to connect."));
			}

			KeyStoreLoader keyStoreLoader = null;
			String keyStoreType = uaConfig.getKeyStoreType();
			String keyStoreAlias = uaConfig.getKeyStoreAlias();
			String keyStorePassword = uaConfig.getKeyStorePassword();
			String certificate = uaConfig.getCertificate();
			if (keyStoreType != null && keyStoreAlias != null && keyStorePassword != null && certificate != null) {
				keyStoreLoader = new KeyStoreLoader(keyStoreType, keyStoreAlias, certificate, keyStorePassword);
				keyStoreLoader.load();
			}

			OpcUaClientConfigBuilder configBuilder = OpcUaClientConfig.builder();

			configBuilder.setEndpoint(endpoint)
			.setApplicationName(LocalizedText.english(uaConfig.getName()))
			.setApplicationUri(uaConfig.getUri())
			.setRequestTimeout(UInteger.valueOf(uaServerConfig.getRequestTimeout()))
			.setSessionTimeout(UInteger.valueOf(uaServerConfig.getSessionTimeout()));

			if (keyStoreLoader != null) {
				configBuilder.setIdentityProvider(uaServerConfig.getIdentityProvider()).setKeyPair(keyStoreLoader.getKeyPair())
				.setCertificate(keyStoreLoader.getCertificate()).build();
			}

			if (LOG.isDebugEnabled()) {
				LOG.debug(logPrefix + "connecting to {}...", endpoint.toString());
			} else {
				LOG.info(logPrefix + "connecting...");
			}
			newClient = OpcUaClient.create(configBuilder.build());
			client = (OpcUaClient)runSafe(newClient.connect());
			setDataChangeFilter();
			if (LOG.isDebugEnabled()) {
				LOG.debug(logPrefix + "connected to {}.", endpoint.toString());
			} else {
				LOG.info(logPrefix + "connected.");
			}
		} catch (Exception e) {
			client = null;
			setDataChangeFilter();
			shutdown(newClient);
			throw new IOException(logPrefix + "failed to connect.", e);
		} finally {
			isBusy.set(false);
		}
	}

	public void disconnect() throws IOException {
		if (!isBusy.compareAndSet(false, true)) {
			throw new IOException(logPrefix + "busy.");
		}
		try {
			LOG.info(logPrefix + "disconnecting....");
			shutdown(client);
			LOG.info(logPrefix + "disconnected.");
		} catch (IOException e) {
			throw e;
		} finally {
			client = null;
			isBusy.set(false);
		}
	}

	private String getIndentOfLog(int depth) {
		StringBuffer sb = new StringBuffer();
		for (int i = 0; i < depth; i++) {
			sb.append(" ");
		}
		return sb.toString();
	}

	private List<NodeId> browseNode(NodeId root, int current, int depth) throws IOException {
		List<NodeId> nodeIds = new ArrayList<NodeId>();

		++current;

		if (depth >= 0 && current > depth) {
			return nodeIds;
		}

		String indent = getIndentOfLog(current * 2);

		BrowseDescription browseDesc = new BrowseDescription(
				root,
				BrowseDirection.Forward,
				Identifiers.HierarchicalReferences,
				true,
				Unsigned.uint(NodeClass.Object.getValue() | NodeClass.Variable.getValue()),
				Unsigned.uint(BrowseResultMask.All.getValue())
				);

		try {
			BrowseResult browseResult = (BrowseResult)runSafe(client.browse(browseDesc));

			List<ReferenceDescription> referenceDescList = new CopyOnWriteArrayList<>();
			referenceDescList.addAll(ConversionUtil.toList(browseResult.getReferences()));

			ByteString continuationPoint = browseResult.getContinuationPoint();

			while (continuationPoint != null && continuationPoint.isNotNull()) {
				BrowseResult nextResult = (BrowseResult)runSafe(client.browseNext(false, continuationPoint));
				referenceDescList.addAll(ConversionUtil.toList(nextResult.getReferences()));
				continuationPoint = nextResult.getContinuationPoint();
			}

			for (ReferenceDescription referenceDesc : referenceDescList) {
				if (!referenceDesc.getNodeId().local(client.getNamespaceTable()).isPresent()) {
					continue;
				}
				NodeId nodeId = referenceDesc.getNodeId().local(client.getNamespaceTable()).get();
				if (referenceDesc.getNodeClass().equals(NodeClass.Variable)) {
					LOG.trace(logPrefix + indent + "added {}", nodeId.toString());
					nodeIds.add(nodeId);
					continue;
				}
				nodeIds.addAll(browseNode(nodeId, current, depth));
			}
		} catch (ExecutionException | InterruptedException | TimeoutException e) {
			throw new IOException(logPrefix + "browsing " + root.toString(), e);
		}

		return nodeIds;
	}

	public void syncNodes() throws IOException {
		nodeIds.clear();
		if (client == null) {
			throw new IllegalStateException(logPrefix + "client not ready yet.");
		}
		for (NodeIdDepth nodeIdDepth : uaServerConfig.getNodeIdDepths()) {
			for (NodeId nodeId : browseNode(nodeIdDepth.nodeId, 0, nodeIdDepth.depth)) {
				if (!nodeIds.contains(nodeId)) {
					LOG.debug(logPrefix + "added {}", nodeId.toString());
					nodeIds.add(nodeId);
				}
			}
			if (!nodeIds.contains(nodeIdDepth.nodeId)) {
				LOG.debug(logPrefix + "added {}", nodeIdDepth.nodeId.toString());
				nodeIds.add(nodeIdDepth.nodeId);
			}
		}
	}

	public void setMonitoredItems() throws InterruptedException, ExecutionException, IOException {
		setMonitoredItems(notificationListener);
	}

	public void setMonitoredItems(UaSubscription.NotificationListener notificationListener)
			throws InterruptedException, ExecutionException, IOException {
		this.notificationListener = Objects.requireNonNull(notificationListener);
		if (client == null) {
			throw new IllegalStateException(logPrefix + "client not ready yet.");
		}
		if (nodeIds.size() == 0) {
			LOG.warn(logPrefix + "NodeIds is empty.");
			return;
		}

		UaSubscription subscription = client.getSubscriptionManager().createSubscription(uaServerConfig.getPublishingInterval()).get();
		subscription.addNotificationListener(this.notificationListener);

		LOG.debug(logPrefix + "subscriptionId:{} publishingInterval:{}->{}", subscription.getSubscriptionId(),
				subscription.getRequestedPublishingInterval(), subscription.getRevisedPublishingInterval());

		List<MonitoredItemCreateRequest> itemRequests = new ArrayList<MonitoredItemCreateRequest>();

		ExtensionObject dataChangeFilter = null;
		DataChangeTrigger trigger = DataChangeTrigger.from(uaServerConfig.getDataChangeTrigger());
		if (trigger != null) {
			if (trigger.equals(DataChangeTrigger.Status)) {
				dataChangeFilter = dataChangeFilterStatus;
			} else if (trigger.equals(DataChangeTrigger.StatusValue)) {
				dataChangeFilter = dataChangeFilterStatusValue;
			} else if (trigger.equals(DataChangeTrigger.StatusValueTimestamp)) {
				dataChangeFilter = dataChangeFilterStatusValueTimestamp;
			}
		} else {
			dataChangeFilter = dataChangeFilterStatusValue;
		}

		for (NodeId nodeId : nodeIds) {
			ReadValueId readValueId = new ReadValueId(nodeId, AttributeId.Value.uid(), null, QualifiedName.NULL_VALUE);
			MonitoringParameters parameters = new MonitoringParameters(
					Unsigned.uint(clientHandle.getAndIncrement()),
					(double)uaServerConfig.getSamplingInterval(),
					dataChangeFilter,
					Unsigned.uint(uaServerConfig.getQueueSize()),
					true
					);
			itemRequests.add(new MonitoredItemCreateRequest(readValueId, MonitoringMode.Reporting, parameters));
		}

		List<UaMonitoredItem> items = subscription.createMonitoredItems(
				TimestampsToReturn.Both,
				itemRequests,
				(item, id) -> item.setValueConsumer(this::onSubscriptionValue)
				).get();

		for (UaMonitoredItem item : items) {
			if (item.getStatusCode().isGood()) {
				String triggerStatus = null;
				Object object = item.getMonitoringFilter().decode(client.getSerializationContext());
				if (object instanceof DataChangeFilter) {
					triggerStatus = ((DataChangeFilter)object).getTrigger().toString();
				}
				LOG.debug(logPrefix + "{} monitoredItemId:{} samplingInterval:{}->{} queueSize:{}->{} dataChangeTrigger:{}",
						item.getReadValueId().getNodeId(),
						item.getMonitoredItemId(),
						item.getRequestedSamplingInterval(), item.getRevisedSamplingInterval(),
						item.getRequestedQueueSize(), item.getRevisedQueueSize(),
						triggerStatus);
			} else {
				nodeIds.remove(item.getReadValueId().getNodeId());
			}
		}
	}

	public static NodeData dataValueToNodeData(NodeId nodeId, DataValue dataValue) {
		NodeData nodeData = new NodeData();
		nodeData.nodeId = nodeId;
		nodeData.dataValue = dataValue;
		Optional<NodeId> optional = nodeData.dataValue.getValue().getDataType();
		if (optional.equals(Optional.empty())) {
			return nodeData;
		}
		NodeId type = optional.get();
		nodeData.typeId = ((UInteger)type.getIdentifier()).intValue();
		nodeData.type = BuiltinDataType.getBackingClass(nodeData.typeId).getSimpleName();
		nodeData.value = nodeData.dataValue.getValue().getValue();
		return nodeData;
	}

	private void onSubscriptionValue(UaMonitoredItem item, DataValue value) {
	}
}
