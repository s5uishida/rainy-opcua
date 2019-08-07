package io.github.s5uishida.iot.rainy.device.opcua.data;

import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;

/*
 * @author s5uishida
 *
 */
public class NodeData {
	public NodeId nodeId;
	public DataValue dataValue;
	public int typeId = -1;
	public String type;
	public Object value;
}
