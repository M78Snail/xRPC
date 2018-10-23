package org.xprc.client.provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xprc.common.protocal.XrpcProtocol;
import org.xprc.remoting.ConnectionUtils;
import org.xprc.remoting.model.NettyRequestProcessor;
import org.xprc.remoting.model.RemotingTransporter;

import io.netty.channel.ChannelHandlerContext;

public class DefaultProviderRegistryProcessor implements NettyRequestProcessor {

	private static final Logger logger = LoggerFactory.getLogger(DefaultProviderRegistryProcessor.class);

	private DefaultProvider defaultProvider;

	public DefaultProviderRegistryProcessor(DefaultProvider defaultProvider) {
		this.defaultProvider = defaultProvider;
	}

	@Override
	public RemotingTransporter processRequest(ChannelHandlerContext ctx, RemotingTransporter request) {
		if (logger.isDebugEnabled()) {
			logger.debug("receive request, {} {} {}", //
					request.getCode(), //
					ConnectionUtils.parseChannelRemoteAddr(ctx.channel()), //
					request);
		}
		switch (request.getCode()) {
		case XrpcProtocol.DEGRADE_SERVICE:
			// return this.defaultProvider.handlerDegradeServiceRequest(request,
			// ctx.channel(), XrpcProtocol.DEGRADE_SERVICE);
		case XrpcProtocol.AUTO_DEGRADE_SERVICE:
			// return this.defaultProvider.handlerDegradeServiceRequest(request,
			// ctx.channel(), XrpcProtocol.AUTO_DEGRADE_SERVICE);
		}
		return null;
	}

}
