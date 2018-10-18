package org.xprc.remoting.model;

import org.xprc.common.exception.remoting.RemotingSendRequestException;
import org.xprc.common.exception.remoting.RemotingTimeoutException;

import io.netty.channel.ChannelHandlerContext;

public interface NettyChannelInactiveProcessor {
	void processChannelInactive(ChannelHandlerContext ctx)
			throws RemotingSendRequestException, RemotingTimeoutException, InterruptedException;
}
