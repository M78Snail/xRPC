package org.xprc.client.consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xprc.common.protocal.XrpcProtocol;
import org.xprc.remoting.ConnectionUtils;
import org.xprc.remoting.model.NettyRequestProcessor;
import org.xprc.remoting.model.RemotingTransporter;

import io.netty.channel.ChannelHandlerContext;

public class DefaultConsumerRegistryProcessor implements NettyRequestProcessor {

	private static final Logger logger = LoggerFactory.getLogger(DefaultConsumerRegistryProcessor.class);

	private DefaultConsumer defaultConsumer;

	public DefaultConsumerRegistryProcessor(DefaultConsumer defaultConsumer) {
		this.defaultConsumer = defaultConsumer;
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
		case XrpcProtocol.SUBCRIBE_RESULT:
			// 回复ack信息
			// 这个也要保持幂等性，因为有可能在consumer消费成功之后发送ack信息到registry信息丢失，registry回重新发送订阅结果信息
			return this.defaultConsumer.getConsumerManager().handlerSubcribeResult(request, ctx.channel());
		case XrpcProtocol.SUBCRIBE_SERVICE_CANCEL:
			// 回复ack信息
			return this.defaultConsumer.getConsumerManager().handlerSubscribeResultCancel(request, ctx.channel());
		case XrpcProtocol.CHANGE_LOADBALANCE:
			// 回复ack信息
			return this.defaultConsumer.getConsumerManager().handlerServiceLoadBalance(request, ctx.channel());
		}

		return null;
	}

}
