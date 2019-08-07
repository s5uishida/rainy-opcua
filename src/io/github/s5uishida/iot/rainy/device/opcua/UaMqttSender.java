package io.github.s5uishida.iot.rainy.device.opcua;

import java.io.IOException;

import io.github.s5uishida.iot.rainy.device.opcua.data.UaData;
import io.github.s5uishida.iot.rainy.sender.IDataSender;
import io.github.s5uishida.iot.rainy.sender.mqtt.AbstractMqttSender;

/*
 * @author s5uishida
 *
 */
public class UaMqttSender extends AbstractMqttSender implements IDataSender {
	private static final UaConfig uaConfig = UaConfig.getInstance();

	private final boolean prettyPrinting;

	public UaMqttSender() throws IOException {
		super();
		prettyPrinting = uaConfig.getPrettyPrinting();
	}

	@Override
	public void send(Object object) throws IOException {
		if (!(object instanceof UaData)) {
			return;
		}

		UaData uaData = (UaData)object;
		String subTopic = formatSubTopic(uaData.deviceID);

		String data = mapper.writeValueAsString(uaData);
		if (LOG.isDebugEnabled() && prettyPrinting) {
			LOG.debug("OPC-UA JSON -\n{}", prettyMapper.writeValueAsString(uaData));
		}

		execute(subTopic, data);
	}
}