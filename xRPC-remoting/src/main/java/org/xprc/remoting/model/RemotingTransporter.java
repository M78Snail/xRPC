package org.xprc.remoting.model;

import java.util.concurrent.atomic.AtomicLong;

import org.xprc.common.protocal.XrpcProtocol;
import org.xprc.common.transport.body.CommonCustomBody;

public class RemotingTransporter extends ByteHolder {
	private static final AtomicLong requestId = new AtomicLong(0l);

	private byte code;

	private transient long timestamp;

	private transient CommonCustomBody customHeader;

	/**
	 * 请求的id
	 */
	private long opaque = requestId.getAndIncrement();

	/**
	 * 定义该传输对象是请求还是响应信息
	 */
	private byte transporterType;

	protected RemotingTransporter() {

	}

	public static RemotingTransporter createRequestTransporter(byte code, CommonCustomBody commonCustomHeader) {
		RemotingTransporter remotingTransporter = new RemotingTransporter();
		remotingTransporter.setCode(code);
		remotingTransporter.customHeader = commonCustomHeader;
		remotingTransporter.transporterType = XrpcProtocol.REQUEST_REMOTING;

		return remotingTransporter;
	}

	public static RemotingTransporter createResponseTransporter(byte code, CommonCustomBody commonCustomHeader,
			long opaque) {
		RemotingTransporter remotingTransporter = new RemotingTransporter();
		remotingTransporter.setCode(code);
		remotingTransporter.customHeader = commonCustomHeader;
		remotingTransporter.setOpaque(opaque);
		remotingTransporter.transporterType = XrpcProtocol.RESPONSE_REMOTING;

		return remotingTransporter;
	}

	public byte getCode() {
		return code;
	}

	public void setCode(byte code) {
		this.code = code;
	}

	public long getTimestamp() {
		return timestamp;
	}

	public void setTimestamp(long timestamp) {
		this.timestamp = timestamp;
	}

	public CommonCustomBody getCustomHeader() {
		return customHeader;
	}

	public void setCustomHeader(CommonCustomBody customHeader) {
		this.customHeader = customHeader;
	}

	public long getOpaque() {
		return opaque;
	}

	public void setOpaque(long opaque) {
		this.opaque = opaque;
	}

	public byte getTransporterType() {
		return transporterType;
	}

	public void setTransporterType(byte transporterType) {
		this.transporterType = transporterType;
	}

	public static RemotingTransporter newInstance(long id, byte sign, byte type, byte[] bytes) {
		RemotingTransporter remotingTransporter = new RemotingTransporter();
		remotingTransporter.setCode(sign);
		remotingTransporter.setTransporterType(type);
		remotingTransporter.setOpaque(id);
		remotingTransporter.bytes(bytes);
		return remotingTransporter;
	}

	@Override
	public String toString() {
		return ">>>>>>>>>>>>>>>RemotingTransporter [code=" + code + ", customHeader=" + customHeader + ", timestamp=" + timestamp
				+ ", opaque=" + opaque + ", transporterType=" + transporterType + "]";
	}

}
