package org.xprc.base.registry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xprc.common.exception.remoting.RemotingSendRequestException;
import org.xprc.common.exception.remoting.RemotingTimeoutException;
import org.xprc.common.rpc.RegisterMeta;
import org.xprc.common.rpc.RegisterMeta.Address;
import org.xprc.remoting.model.NettyChannelInactiveProcessor;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.AttributeKey;
import io.netty.util.internal.ConcurrentSet;

public class DefaultRegistryChannelInactiveProcessor implements NettyChannelInactiveProcessor {

	private static final Logger logger = LoggerFactory.getLogger(DefaultRegistryChannelInactiveProcessor.class);

	private DefaultRegistryServer defaultRegistryServer;

	public DefaultRegistryChannelInactiveProcessor(DefaultRegistryServer defaultRegistryServer) {
		this.defaultRegistryServer = defaultRegistryServer;
	}

	private static final AttributeKey<ConcurrentSet<RegisterMeta>> S_PUBLISH_KEY = AttributeKey
			.valueOf("server.published");

	@Override
	public void processChannelInactive(ChannelHandlerContext ctx)
			throws RemotingSendRequestException, RemotingTimeoutException, InterruptedException {
		// 获取到当前的channel，此时的channel应该是打过记号的
		Channel channel = ctx.channel();

		// 取消之前发布的所有服务
		ConcurrentSet<RegisterMeta> registerMetaSet = channel.attr(S_PUBLISH_KEY).get();

		// 如果该channel打过的记号是空，或者是空集合的话，直接返回
		if (registerMetaSet == null || registerMetaSet.isEmpty()) {
			logger.debug("registerMetaSet is empty");
			return;
		}

		// 接下来需要做两件事情
		// 1 修改当前注册中心该channel所提供的所有服务取消
		// 2 发送请求告之consumer该地址对应的所有服务下线

		Address address = null;
		for (RegisterMeta meta : registerMetaSet) {
			if (address == null) {
				address = meta.getAddress();
			}
			this.defaultRegistryServer.getProviderManager().handlePublishCancel(meta, channel);
		}
	}

}
