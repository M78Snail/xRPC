package org.xprc.remoting.model;

import io.netty.channel.ChannelHandlerContext;

public interface NettyRequestProcessor {
	RemotingTransporter processRequest(ChannelHandlerContext ctx, RemotingTransporter request);
}
