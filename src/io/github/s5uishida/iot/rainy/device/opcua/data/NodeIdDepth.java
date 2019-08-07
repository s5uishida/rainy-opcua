package io.github.s5uishida.iot.rainy.device.opcua.data;

import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;

/*
 * @author s5uishida
 *
 */
public class NodeIdDepth {
	public final NodeId nodeId;
	public final int depth;

	public NodeIdDepth(NodeId nodeId, int depth) {
		this.nodeId = nodeId;
		this.depth = depth;
	}

	@Override
	public String toString() {
		return nodeId.toString() + " depth:" + depth;
	}
}
