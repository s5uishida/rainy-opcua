package io.github.s5uishida.iot.rainy.device.opcua;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.influxdb.dto.BatchPoints;
import org.influxdb.dto.Point.Builder;

import io.github.s5uishida.iot.rainy.device.opcua.data.UaData;
import io.github.s5uishida.iot.rainy.sender.IDataSender;
import io.github.s5uishida.iot.rainy.sender.influxdb.AbstractInfluxDBSender;

/*
 * @author s5uishida
 *
 */
public class UaInfluxDBSender extends AbstractInfluxDBSender implements IDataSender {
	private String formatKey(String key) {
		return key.replaceAll("[:;,\\.\\-/\\(\\)\\[\\]]", "_");
	}

	@Override
	public void send(Object object) throws IOException {
		if (!(object instanceof UaData)) {
			return;
		}

		UaData uaData = (UaData)object;
		String dbName = formatDBName(uaData.deviceID);

		Iterator<Entry<String, List<Map<String, Object>>>> it = uaData.opcua.entrySet().iterator();
		while (it.hasNext()) {
			Entry<String, List<Map<String, Object>>> entry = it.next();
			String key = formatKey(entry.getKey());
			List<Map<String, Object>> list = entry.getValue();

			for (Map<String, Object> nodeDataMap : list) {
				UaData data = new UaData();
				data.deviceID = uaData.deviceID;
				data.clientID = uaData.clientID;
				data.samplingDate = (String)nodeDataMap.get("sourceTime");
				data.samplingTimeMillis = (Long)nodeDataMap.get("sourceTimeMillis");

				BatchPoints batchPoints = BatchPoints.database(dbName).tag("async", "true").retentionPolicy(retentionPolicy).build();
				Builder builder = setCommonFields("opcua", data);

				String valueKey = key + "_value";
				Object value = nodeDataMap.get("value");

				if (value instanceof Boolean) {
					builder.addField(valueKey, (boolean)value);
				} else if (value instanceof Double) {
					builder.addField(valueKey, (double)value);
				} else if (value instanceof Float) {
					builder.addField(valueKey, ((Float)value).doubleValue());
				} else if (value instanceof Long) {
					builder.addField(valueKey, (long)value);
				} else if (value instanceof Integer) {
					builder.addField(valueKey, ((Integer)value).longValue());
				} else if (value instanceof Short) {
					builder.addField(valueKey, ((Short)value).longValue());
				} else if (value instanceof String) {
					builder.addField(valueKey, (String)value);
				} else {
					continue;
				}

				batchPoints.point(builder.build());

				execute(dbName, batchPoints);
			}
		}

	}
}
