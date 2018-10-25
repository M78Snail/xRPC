package org.xprc.base.registry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xprc.common.exception.remoting.RemotingSendRequestException;
import org.xprc.common.exception.remoting.RemotingTimeoutException;
import org.xprc.common.protocal.XrpcProtocol;
import org.xprc.remoting.ConnectionUtils;
import org.xprc.remoting.model.NettyRequestProcessor;
import org.xprc.remoting.model.RemotingTransporter;

import io.netty.channel.ChannelHandlerContext;

public class DefaultRegistryProcessor implements NettyRequestProcessor {

	private static final Logger logger = LoggerFactory.getLogger(DefaultRegistryProcessor.class);

	private DefaultRegistryServer defaultRegistryServer;

	public DefaultRegistryProcessor(DefaultRegistryServer defaultRegistryServer) {
		this.defaultRegistryServer = defaultRegistryServer;
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

		// 处理服务提供者provider推送的服务信息
		case XrpcProtocol.PUBLISH_SERVICE:
			// 要保持幂等性，同一个实例重复发布同一个服务的时候对于注册中心来说是无影响的
			return this.defaultRegistryServer.getProviderManager().handlerRegister(request, ctx.channel());

		// 处理服务提供者provider推送的服务取消的信息
		case XrpcProtocol.PUBLISH_CANCEL_SERVICE:
			return this.defaultRegistryServer.getProviderManager().handlerRegisterCancel(request, ctx.channel());

		// 处理服务消费者consumer订阅服务的请求
		case XrpcProtocol.SUBSCRIBE_SERVICE:
			return this.defaultRegistryServer.getProviderManager().handleSubscribe(request, ctx.channel());

		// 处理管理者发送过来的服务管理服务
		case XrpcProtocol.MANAGER_SERVICE:
			return this.defaultRegistryServer.getProviderManager().handleManager(request, ctx.channel());
		}

		return null;
	}
}
