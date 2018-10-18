package org.xprc.remoting.netty;

import java.util.concurrent.ExecutorService;

import org.xprc.common.exception.remoting.RemotingSendRequestException;
import org.xprc.common.exception.remoting.RemotingTimeoutException;
import org.xprc.common.utils.Pair;
import org.xprc.remoting.model.NettyChannelInactiveProcessor;
import org.xprc.remoting.model.NettyRequestProcessor;
import org.xprc.remoting.model.RemotingTransporter;

import io.netty.channel.Channel;

public interface RemotingServer extends BaseRemotingService {
	void registerProecessor(final byte requestCode, final NettyRequestProcessor processor,
			final ExecutorService executor);

	void registerChannelInactiveProcessor(final NettyChannelInactiveProcessor processor,
			final ExecutorService executor);

	void registerDefaultProcessor(final NettyRequestProcessor processor, final ExecutorService executor);

	Pair<NettyRequestProcessor, ExecutorService> getProcessorPair(final int requestCode);

	RemotingTransporter invokeSync(final Channel channel, final RemotingTransporter request, final long timeoutMillis)
			throws InterruptedException, RemotingSendRequestException, RemotingTimeoutException;
}
