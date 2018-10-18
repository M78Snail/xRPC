package org.xprc.remoting.netty.decode;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xprc.common.exception.remoting.RemotingContextException;
import org.xprc.common.protocal.XrpcProtocol;
import org.xprc.remoting.model.RemotingTransporter;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ReplayingDecoder;

public class RemotingTransporterDecoder extends ReplayingDecoder<RemotingTransporterDecoder.State> {

	private static final Logger logger = LoggerFactory.getLogger(RemotingTransporterDecoder.class);

	private static final int MAX_BODY_SIZE = 1024 * 1024 * 5;

	private final XrpcProtocol header = new XrpcProtocol();

	public RemotingTransporterDecoder() {
		// 设置(下文#state()的默认返回对象)
		super(State.HEADER_MAGIC);
	}

	enum State {
		HEADER_MAGIC, HEADER_TYPE, HEADER_SIGN, HEADER_ID, HEADER_BODY_LENGTH, BODY
	}

	@Override
	protected void decode(ChannelHandlerContext arg0, ByteBuf in, List<Object> out) throws Exception {
		switch (state()) {
		case HEADER_MAGIC:
			checkMagic(in.readShort());
			checkpoint(State.HEADER_TYPE);
		case HEADER_TYPE:
			header.type(in.readByte());
			checkpoint(State.HEADER_SIGN);
		case HEADER_SIGN:
			header.sign(in.readByte());
			checkpoint(State.HEADER_ID);
		case HEADER_ID:
			header.id(in.readLong()); // 消息id
			checkpoint(State.HEADER_BODY_LENGTH);
		case HEADER_BODY_LENGTH:
			header.bodyLength(in.readInt()); // 消息体长度
			checkpoint(State.BODY);
		case BODY:
			int bodyLength = checkBodyLength(header.bodyLength());
			byte[] bytes = new byte[bodyLength];
			in.readBytes(bytes);
			out.add(RemotingTransporter.newInstance(header.id(), header.sign(), header.type(), bytes));
			break;

		default:
			break;
		}

	}

	private int checkBodyLength(int bodyLength) throws RemotingContextException {
		if (bodyLength > MAX_BODY_SIZE) {
			throw new RemotingContextException("body of request is bigger than limit value " + MAX_BODY_SIZE);
		}
		return bodyLength;
	}

	private void checkMagic(short magic) throws RemotingContextException {
		if (XrpcProtocol.MAGIC != magic) {
			logger.error("Magic is not match");
			throw new RemotingContextException("magic value is not equal " + XrpcProtocol.MAGIC);
		}
	}

}
