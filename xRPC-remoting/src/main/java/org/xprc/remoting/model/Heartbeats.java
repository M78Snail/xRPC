package org.xprc.remoting.model;

import org.xprc.common.protocal.XrpcProtocol;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class Heartbeats {
	private static final ByteBuf HEARTBEAT_BUF;

	static {
		ByteBuf buf = Unpooled.buffer(XrpcProtocol.HEAD_LENGTH);
		buf.writeShort(XrpcProtocol.MAGIC);
		buf.writeByte(XrpcProtocol.HEARTBEAT);
		buf.writeByte(0);
		buf.writeLong(0);
		buf.writeInt(0);
		HEARTBEAT_BUF = Unpooled.unmodifiableBuffer(Unpooled.unreleasableBuffer(buf));
	}

	/**
	 * Returns the shared heartbeat content.
	 */
	public static ByteBuf heartbeatContent() {
		return HEARTBEAT_BUF.duplicate();
	}
}
