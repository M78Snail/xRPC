package org.xprc.remoting.netty.encode;

import org.xprc.common.protocal.XrpcProtocol;
import org.xprc.common.serialization.SerializerHolder;
import org.xprc.remoting.model.RemotingTransporter;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

/**
 * **************************************************************************************************
 * Protocol ┌ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─
 * ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┐ 2 │ 1 │ 1 │ 8 │ 4 │ ├ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─
 * ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┤ │ │ │ │
 * │ │ MAGIC Sign Status Invoke Id Body Length Body Content │ │ │ │ │ │ └ ─ ─ ─
 * ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─
 * ─ ─ ─ ─ ─ ┘
 *
 * 消息头16个字节定长 = 2 // MAGIC = (short) 0xbabe + 1 // 消息标志位, 用来表示消息类型 + 1 // 空 + 8
 * // 消息 id long 类型 + 4 // 消息体body长度, int类型
 */

@ChannelHandler.Sharable
public class RemotingTransporterEncoder extends MessageToByteEncoder<RemotingTransporter> {

	@Override
	protected void encode(ChannelHandlerContext arg0, RemotingTransporter msg, ByteBuf out) throws Exception {
		doEncodeRemotingTransporter(msg, out);
	}

	private void doEncodeRemotingTransporter(RemotingTransporter msg, ByteBuf out) {
		byte[] body = SerializerHolder.serializerImpl().writeObject(msg.getCustomHeader());

		// 协议头
		out.writeShort(XrpcProtocol.MAGIC);
		// 传输类型 sign 是请求还是响应
		out.writeByte(msg.getTransporterType());
		// 请求类型requestcode 表明主题信息的类型，也代表请求的类型
		out.writeByte(msg.getCode());
		// requestId
		out.writeLong(msg.getOpaque());
		// length
		out.writeInt(body.length);

		out.writeBytes(body);

	}

}
