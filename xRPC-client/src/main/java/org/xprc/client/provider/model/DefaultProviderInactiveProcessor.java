package org.xprc.client.provider.model;

import org.xprc.client.provider.DefaultProvider;
import org.xprc.remoting.model.NettyChannelInactiveProcessor;

import io.netty.channel.ChannelHandlerContext;

public class DefaultProviderInactiveProcessor implements NettyChannelInactiveProcessor {
	private DefaultProvider defaultProvider;

	public DefaultProviderInactiveProcessor(DefaultProvider defaultProvider) {
		this.defaultProvider = defaultProvider;
	}

	@Override
	public void processChannelInactive(ChannelHandlerContext ctx) {
		defaultProvider.setProviderStateIsHealthy(false);
	}
}
