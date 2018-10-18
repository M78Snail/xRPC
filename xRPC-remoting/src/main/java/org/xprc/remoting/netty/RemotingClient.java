package org.xprc.remoting.netty;

import java.util.concurrent.ExecutorService;

import org.xprc.common.exception.remoting.RemotingException;
import org.xprc.common.exception.remoting.RemotingSendRequestException;
import org.xprc.common.exception.remoting.RemotingTimeoutException;
import org.xprc.remoting.model.NettyChannelInactiveProcessor;
import org.xprc.remoting.model.NettyRequestProcessor;
import org.xprc.remoting.model.RemotingTransporter;

public interface RemotingClient extends BaseRemotingService {

	public RemotingTransporter invokeSync(final String addr, final RemotingTransporter request,
			final long timeoutMillis)
			throws RemotingTimeoutException, RemotingSendRequestException, InterruptedException, RemotingException;

	void registerProcessor(final byte requestCode, final NettyRequestProcessor processor,
			final ExecutorService executor);

	void registerChannelInactiveProcessor(NettyChannelInactiveProcessor processor, ExecutorService executor);

	/**
	 * 某个地址的长连接的channel是否可写
	 * 
	 * @param addr
	 * @return
	 */
	boolean isChannelWriteable(final String addr);

	/**
	 * 当与server的channel inactive的时候，是否主动重连netty的server端
	 * 
	 * @param isReconnect
	 */
	void setreconnect(boolean isReconnect);
}
