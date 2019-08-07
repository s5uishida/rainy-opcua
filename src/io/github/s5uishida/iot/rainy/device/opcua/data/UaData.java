package io.github.s5uishida.iot.rainy.device.opcua.data;

import java.util.List;
import java.util.Map;

import io.github.s5uishida.iot.rainy.data.CommonData;

/*
 * @author s5uishida
 *
 */
public class UaData extends CommonData {
	public Map<String, List<Map<String, Object>>> opcua;
}
