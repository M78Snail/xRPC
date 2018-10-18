package org.xprc.remoting.model;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.xprc.remoting.InvokeCallback;

/**
 * 消息转发代理类
 * 
 * @author duxiaoming
 *
 */
public class RemotingResponse {
	private volatile RemotingTransporter remotingTransporter;

	private volatile Throwable cause;

	private volatile boolean sendRequestOK = true;

	private final long opaque;

	// 默认的回调函数
	private final InvokeCallback invokeCallback;

	// 请求的默认超时时间
	private final long timeoutMillis;

	private final long beginTimestamp = System.currentTimeMillis();

	private final CountDownLatch countDownLatch = new CountDownLatch(1);

	public RemotingResponse(long opaque, long timeoutMillis, InvokeCallback invokeCallback) {
		this.invokeCallback = invokeCallback;
		this.opaque = opaque;
		this.timeoutMillis = timeoutMillis;
	}

	public void executeInvokeCallback() {
		if (invokeCallback != null) {
			invokeCallback.operationComplete(this);
		}
	}

	public boolean isSendRequestOK() {
		return sendRequestOK;
	}

	public void setSendRequestOK(boolean sendRequestOK) {
		this.sendRequestOK = sendRequestOK;
	}

	public long getOpaque() {
		return opaque;
	}

	public RemotingTransporter getRemotingTransporter() {
		return remotingTransporter;
	}

	public void setRemotingTransporter(RemotingTransporter remotingTransporter) {
		this.remotingTransporter = remotingTransporter;
	}

	public Throwable getCause() {
		return cause;
	}

	public void setCause(Throwable cause) {
		this.cause = cause;
	}

	public long getTimeoutMillis() {
		return timeoutMillis;
	}

	public long getBeginTimestamp() {
		return beginTimestamp;
	}

	public RemotingTransporter waitResponse() throws InterruptedException {
		this.countDownLatch.await(timeoutMillis, TimeUnit.MILLISECONDS);
		return this.remotingTransporter;
	}

	public void putResponse(final RemotingTransporter remotingTransporter) {
		setRemotingTransporter(remotingTransporter);
		// 接收到对应的消息之后需要countDown
		countDownLatch.countDown();
	}

}
