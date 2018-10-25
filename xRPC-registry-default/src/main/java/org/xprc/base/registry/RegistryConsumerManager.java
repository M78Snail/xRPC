package org.xprc.base.registry;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xprc.common.exception.remoting.RemotingSendRequestException;
import org.xprc.common.exception.remoting.RemotingTimeoutException;
import org.xprc.common.loadbalance.LoadBalanceStrategy;
import org.xprc.common.protocal.XrpcProtocol;
import org.xprc.common.rpc.RegisterMeta;
import org.xprc.common.transport.body.AckCustomBody;
import org.xprc.common.transport.body.SubcribeResultCustomBody;
import org.xprc.remoting.model.RemotingTransporter;

import io.netty.channel.Channel;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.GlobalEventExecutor;
import io.netty.util.internal.ConcurrentSet;

public class RegistryConsumerManager {

	private static final Logger logger = LoggerFactory.getLogger(RegistryConsumerManager.class);

	private static final AttributeKey<ConcurrentSet<String>> S_SUBSCRIBE_KEY = AttributeKey
			.valueOf("server.subscribed");

	private volatile ChannelGroup subscriberChannels = new DefaultChannelGroup("subscribers",
			GlobalEventExecutor.INSTANCE);

	private final ConcurrentSet<MessageNonAck> messagesNonAcks = new ConcurrentSet<MessageNonAck>();

	private DefaultRegistryServer defaultRegistryServer;

	public RegistryConsumerManager(DefaultRegistryServer defaultRegistryServer) {
		this.defaultRegistryServer = defaultRegistryServer;
	}

	public ChannelGroup getSubscriberChannels() {
		return subscriberChannels;
	}

	public void checkSendFailedMessage() {
		ConcurrentSet<MessageNonAck> nonAcks = messagesNonAcks;
		messagesNonAcks.clear();
		if (nonAcks != null) {
			for (MessageNonAck messageNonAck : nonAcks) {
				try {
					pushMessageToConsumer(messageNonAck.getMsg(), messageNonAck.getServiceName());
				} catch (Exception e) {
					logger.error("send message failed");
				}
			}
		}
		nonAcks = null; // help GC
	}

	public void notifyMacthedSubscriber(RegisterMeta meta, LoadBalanceStrategy loadBalanceStrategy) {
		// 构建订阅通知的主体传输对象
		SubcribeResultCustomBody subcribeResultCustomBody = new SubcribeResultCustomBody();
		buildSubcribeResultCustomBody(meta, subcribeResultCustomBody, loadBalanceStrategy);

		// 传送给consumer对象的RemotingTransporter
		RemotingTransporter sendConsumerRemotingTrasnporter = RemotingTransporter
				.createRequestTransporter(XrpcProtocol.SUBCRIBE_RESULT, subcribeResultCustomBody);

		pushMessageToConsumer(sendConsumerRemotingTrasnporter, meta.getServiceName());

	}

	private void buildSubcribeResultCustomBody(RegisterMeta meta, SubcribeResultCustomBody subcribeResultCustomBody,
			LoadBalanceStrategy loadBalanceStrategy) {
		LoadBalanceStrategy defaultBalanceStrategy = defaultRegistryServer.getRegistryServerConfig()
				.getDefaultLoadBalanceStrategy();
		List<RegisterMeta> registerMetas = new ArrayList<RegisterMeta>();

		registerMetas.add(meta);
		subcribeResultCustomBody
				.setLoadBalanceStrategy(null == loadBalanceStrategy ? defaultBalanceStrategy : loadBalanceStrategy);
		subcribeResultCustomBody.setRegisterMeta(registerMetas);
	}

	private void pushMessageToConsumer(RemotingTransporter sendConsumerRemotingTrasnporter, String serviceName) {
		// 所有的订阅者的channel集合
		if (!subscriberChannels.isEmpty()) {
			for (Channel channel : subscriberChannels) {
				if (isChannelSubscribeOnServiceMeta(serviceName, channel)) {
					RemotingTransporter remotingTransporter = null;
					try {
						remotingTransporter = this.defaultRegistryServer.getRemotingServer().invokeSync(channel,
								sendConsumerRemotingTrasnporter, 3000l);
					} catch (RemotingSendRequestException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (RemotingTimeoutException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					// 如果是ack返回是null说明是超时了，需要重新发送
					if (remotingTransporter == null) {
						logger.warn("push consumer message time out,need send again");
						MessageNonAck msgNonAck = new MessageNonAck(remotingTransporter, channel, serviceName);
						messagesNonAcks.add(msgNonAck);
					}
					// 如果消费者端消费者消费失败
					AckCustomBody ackCustomBody = (AckCustomBody) remotingTransporter.getCustomHeader();
					if (!ackCustomBody.isSuccess()) {
						logger.warn("consumer fail handler this message");
						MessageNonAck msgNonAck = new MessageNonAck(remotingTransporter, channel, serviceName);
						messagesNonAcks.add(msgNonAck);
					}
				}
			}

		}
	}

	/**
	 * 因为在consumer订阅服务的时候，就会在其channel上绑定其订阅的信息
	 * 
	 * @param serviceMeta
	 * @param channel
	 * @return
	 */
	private boolean isChannelSubscribeOnServiceMeta(String serviceName, Channel channel) {
		ConcurrentSet<String> serviceMetaSet = channel.attr(S_SUBSCRIBE_KEY).get();

		return serviceMetaSet != null && serviceMetaSet.contains(serviceName);
	}

	public void notifyMacthedSubscriberCancel(RegisterMeta meta) {
		// 构建订阅通知的主体传输对象
		SubcribeResultCustomBody subcribeResultCustomBody = new SubcribeResultCustomBody();
		buildSubcribeResultCustomBody(meta, subcribeResultCustomBody, null);

		RemotingTransporter sendConsumerRemotingTrasnporter = RemotingTransporter
				.createRequestTransporter(XrpcProtocol.SUBCRIBE_SERVICE_CANCEL, subcribeResultCustomBody);

		pushMessageToConsumer(sendConsumerRemotingTrasnporter, meta.getServiceName());

	}

	static class MessageNonAck {

		private final long id;

		private final String serviceName;
		private final RemotingTransporter msg;
		private final Channel channel;

		public MessageNonAck(RemotingTransporter msg, Channel channel, String serviceName) {
			this.msg = msg;
			this.channel = channel;
			this.serviceName = serviceName;

			id = msg.getOpaque();
		}

		public long getId() {
			return id;
		}

		public RemotingTransporter getMsg() {
			return msg;
		}

		public Channel getChannel() {
			return channel;
		}

		public String getServiceName() {
			return serviceName;
		}

	}

}
