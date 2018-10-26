package org.xprc.client.consumer;

import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xprc.common.exception.remoting.RemotingSendRequestException;
import org.xprc.common.exception.remoting.RemotingTimeoutException;
import org.xprc.common.protocal.XrpcProtocol;
import org.xprc.common.transport.body.SubscribeRequestCustomBody;
import org.xprc.remoting.model.RemotingTransporter;

import io.netty.channel.Channel;

public class DefaultConsumerRegistry {
	private static final Logger logger = LoggerFactory.getLogger(DefaultConsumerRegistry.class);
	private DefaultConsumer defaultConsumer;

	private ConcurrentHashMap<String, NotifyListener> serviceMatchedNotifyListener = new ConcurrentHashMap<String, NotifyListener>();

	private long timeout;

	public DefaultConsumerRegistry(DefaultConsumer defaultConsumer) {
		this.defaultConsumer = defaultConsumer;
		this.timeout = this.defaultConsumer.getConsumerConfig().getRegistryTimeout();
	}

	public void subcribeService(String serviceName, NotifyListener listener) {

		if (listener != null) {
			//添加回调
			serviceMatchedNotifyListener.put(serviceName, listener);
		}

		if (this.defaultConsumer.getRegistyChannel() == null) {
			this.defaultConsumer.getOrUpdateHealthyChannel();
		} else {
			logger.info("registry center channel is [{}]", this.defaultConsumer.getRegistyChannel());
			SubscribeRequestCustomBody body = new SubscribeRequestCustomBody();
			body.setServiceName(serviceName);

			RemotingTransporter remotingTransporter = RemotingTransporter
					.createRequestTransporter(XrpcProtocol.SUBSCRIBE_SERVICE, body);

			try {
				RemotingTransporter request = sendKernelSubscribeInfo(this.defaultConsumer.getRegistyChannel(), remotingTransporter, timeout);
				RemotingTransporter ackTransporter = this.defaultConsumer.getConsumerManager().handlerSubcribeResult(request,
						this.defaultConsumer.getRegistyChannel());
				this.defaultConsumer.getRegistyChannel().writeAndFlush(ackTransporter);
			} catch (Exception e) {
				logger.warn("registry failed [{}]", e.getMessage());
			}
		}
	}

	private RemotingTransporter sendKernelSubscribeInfo(Channel registyChannel, RemotingTransporter remotingTransporter,
			long timeout) throws RemotingTimeoutException, RemotingSendRequestException, InterruptedException {
		return this.defaultConsumer.getRegistryNettyRemotingClient()
				.invokeSyncImpl(this.defaultConsumer.getRegistyChannel(), remotingTransporter, timeout);
	}

	public ConcurrentHashMap<String, NotifyListener> getServiceMatchedNotifyListener() {
		return serviceMatchedNotifyListener;
	}

	public void setServiceMatchedNotifyListener(
			ConcurrentHashMap<String, NotifyListener> serviceMatchedNotifyListener) {
		this.serviceMatchedNotifyListener = serviceMatchedNotifyListener;
	}
}
