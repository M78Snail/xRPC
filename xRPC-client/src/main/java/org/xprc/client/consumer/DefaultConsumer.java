package org.xprc.client.consumer;

import static org.xprc.client.consumer.AbstractDefaultConsumer.getChannelGroupByServiceName;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xprc.common.exception.remoting.RemotingSendRequestException;
import org.xprc.common.exception.remoting.RemotingTimeoutException;
import org.xprc.common.protocal.XrpcProtocol;
import org.xprc.common.rpc.RegisterMeta;
import org.xprc.common.utils.ChannelGroup;
import org.xprc.common.utils.JUnsafe;
import org.xprc.common.utils.NettyChannelGroup;
import org.xprc.common.utils.UnresolvedAddress;
import org.xprc.remoting.ConnectionUtils;
import org.xprc.remoting.model.RemotingTransporter;
import org.xprc.remoting.netty.NettyClientConfig;
import org.xprc.remoting.netty.NettyRemotingClient;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;

public abstract class DefaultConsumer extends AbstractDefaultConsumer {

	private static final Logger logger = LoggerFactory.getLogger(DefaultConsumer.class);

	private NettyClientConfig registryClientConfig;
	private NettyClientConfig providerClientConfig;
	protected NettyRemotingClient registryNettyRemotingClient;
	protected NettyRemotingClient providerNettyRemotingClient;
	private Channel registyChannel;

	private DefaultConsumerRegistry defaultConsumerRegistry;
	private ConsumerManager consumerManager;
	private ConsumerConfig consumerConfig;

	public DefaultConsumer(NettyClientConfig registryClientConfig, NettyClientConfig providerClientConfig,
			ConsumerConfig consumerConfig) {
		this.registryClientConfig = registryClientConfig;
		this.providerClientConfig = providerClientConfig;
		this.consumerConfig = consumerConfig;
		defaultConsumerRegistry = new DefaultConsumerRegistry(this);
		consumerManager = new ConsumerManager(this);
		initialize();
	}

	private void initialize() {

		// 因为服务消费端可以直连provider，所以当传递过来的与注册中心连接的配置文件为空的时候，可以不初始化registryNettyRemotingClient
		if (null != this.registryClientConfig) {
			this.registryNettyRemotingClient = new NettyRemotingClient(this.registryClientConfig);
			// 注册处理器
			this.registerProcessor();
		}

		this.providerNettyRemotingClient = new NettyRemotingClient(this.providerClientConfig);

	}

	private void registerProcessor() {
		this.registryNettyRemotingClient.registerProcessor(XrpcProtocol.SUBCRIBE_RESULT,
				new DefaultConsumerRegistryProcessor(this), null);
		this.registryNettyRemotingClient.registerProcessor(XrpcProtocol.SUBCRIBE_SERVICE_CANCEL,
				new DefaultConsumerRegistryProcessor(this), null);
		this.registryNettyRemotingClient.registerProcessor(XrpcProtocol.CHANGE_LOADBALANCE,
				new DefaultConsumerRegistryProcessor(this), null);
	}

	@Override
	public SubscribeManager subscribeService(String service) {
		SubscribeManager manager = new SubscribeManager() {
			private final ReentrantLock lock = new ReentrantLock();
			private final Condition notifyCondition = lock.newCondition();
			private final AtomicBoolean signalNeeded = new AtomicBoolean(false);

			@Override
			public void start() {
				subcribeService(service, new NotifyListener() {

					@Override
					public void notify(RegisterMeta registerMeta, NotifyEvent event) {
						// host
						String remoteHost = registerMeta.getAddress().getHost();
						// port vip服务 port端口号-2
						int remotePort = registerMeta.isVIPService() ? (registerMeta.getAddress().getPort() - 2)
								: registerMeta.getAddress().getPort();

						final ChannelGroup group = group(new UnresolvedAddress(remoteHost, remotePort));

						if (event == NotifyEvent.CHILD_ADDED) {
							// 链路复用，如果此host和port对应的链接的channelGroup是已经存在的，则无需建立新的链接，只需要将此group与service建立关系即可
							if (!group.isAvailable()) {
								int connCount = registerMeta.getConnCount() < 0 ? 1 : registerMeta.getConnCount();
								group.setWeight(registerMeta.getWeight());
								for (int i = 0; i < connCount; i++) {
									try {
										// 所有的consumer与provider之间的链接不进行短线重连操作
										DefaultConsumer.this.getProviderNettyRemotingClient().setreconnect(false);
										DefaultConsumer.this.getProviderNettyRemotingClient().getBootstrap()
												.connect(ConnectionUtils
														.string2SocketAddress(remoteHost + ":" + remotePort))
												.addListener(new ChannelFutureListener() {

													@Override
													public void operationComplete(ChannelFuture future)
															throws Exception {
														group.add(future.channel());
														onSucceed(signalNeeded.getAndSet(false));
													}
												});
									} catch (Exception e) {
										logger.error("connection provider host [{}] and port [{}] occor exception [{}]",
												remoteHost, remotePort, e.getMessage());
									}
								}
							} else {
								onSucceed(signalNeeded.getAndSet(false));
							}
							addChannelGroup(service, group);
						} else {
							onSucceed(signalNeeded.getAndSet(false));
						}
					}
				});

			}

			@Override
			public boolean waitForAvailable(long timeoutMillis) {
				if (isServiceAvailable(service)) {
					return true;
				}
				boolean available = false;
				long start = System.nanoTime();
				final ReentrantLock _look = lock;
				_look.lock();
				try {
					while (!isServiceAvailable(service)) {
						signalNeeded.set(true);
						notifyCondition.await(timeoutMillis, TimeUnit.MILLISECONDS);

						available = isServiceAvailable(service);
						if (available || (System.nanoTime() - start) > TimeUnit.MILLISECONDS.toNanos(timeoutMillis)) {
							break;
						}
					}
				} catch (InterruptedException e) {
					JUnsafe.throwException(e);
				} finally {
					_look.unlock();
				}
				return available;
			}

			private void onSucceed(boolean doSignal) {
				if (doSignal) {
					final ReentrantLock _look = lock;
					_look.lock();
					try {
						notifyCondition.signalAll();
					} finally {
						_look.unlock();
					}
				}

			}

			private boolean isServiceAvailable(String service) {
				CopyOnWriteArrayList<ChannelGroup> list = DefaultConsumer.super.getChannelGroupByServiceName(service);
				if (list == null) {
					return false;
				} else {
					for (ChannelGroup channelGroup : list) {
						if (channelGroup.isAvailable()) {
							return true;
						}
					}
				}
				return false;
			}

		};

		manager.start();

		return manager;
	}

	protected NettyRemotingClient getProviderNettyRemotingClient() {
		return providerNettyRemotingClient;
	}

	@Override
	public void getOrUpdateHealthyChannel() {
		// 获取到注册中心的地址
		String addresses = this.registryClientConfig.getDefaultAddress();

		if (registyChannel != null && registyChannel.isActive() && registyChannel.isWritable())
			return;

		if (addresses == null || "".equals(addresses)) {
			logger.error("registry address is empty");
			return;
		}
		// 与注册中心连接的时候重试次数
		int retryConnectionTimes = this.consumerConfig.getRetryConnectionRegistryTimes();
		// 连接给每次注册中心的时候最大的超时时间
		long maxTimeout = this.consumerConfig.getMaxRetryConnectionRegsitryTime();

		String[] adds = addresses.split(",");
		for (int i = 0; i < adds.length; i++) {

			if (registyChannel != null && registyChannel.isActive() && registyChannel.isWritable())
				return;

			String currentAddress = adds[i];
			// 开始计时
			final long beginTimestamp = System.currentTimeMillis();
			long endTimestamp = beginTimestamp;

			int times = 0;

			// 当重试次数小于最大次数且每个实例重试的时间小于最大的时间的时候，不断重试
			for (; times < retryConnectionTimes && (endTimestamp - beginTimestamp) < maxTimeout; times++) {
				try {
					Channel channel = registryNettyRemotingClient.createChannel(currentAddress);
					if (channel != null && channel.isActive() && channel.isWritable()) {
						this.registyChannel = channel;
						break;
					} else {
						continue;
					}
				} catch (InterruptedException e) {
					logger.warn("connection registry center [{}] fail", currentAddress);
					endTimestamp = System.currentTimeMillis();
					continue;
				}
			}
		}
	}

	@Override
	public void subcribeService(String subcribeServices, NotifyListener listener) {
		if (subcribeServices != null) {
			// 连接注册中心
			this.defaultConsumerRegistry.subcribeService(subcribeServices, listener);
		}
	}

	@Override
	public ChannelGroup group(UnresolvedAddress address) {
		ChannelGroup group = addressGroups.get(address);
		if (group == null) {
			ChannelGroup newGroup = newChannelGroup(address);
			group = addressGroups.putIfAbsent(address, newGroup);
			if (group == null) {
				group = newGroup;
			}
		}
		return group;
	}

	private ChannelGroup newChannelGroup(UnresolvedAddress address) {
		return new NettyChannelGroup(address);
	}



	@Override
	public Channel directGetProviderByChannel(UnresolvedAddress address) throws InterruptedException {
		return this.providerNettyRemotingClient.getAndCreateChannel(address.getHost() + ":" + address.getPort());
	}

	@Override
	public void start() {
		logger.info("######### consumer start.... #########");
		// 如果连接注册中心的client初始化成功的情况下，且连接注册中心的地址不为空的时候去尝试连接注册中心
		if (null != this.registryClientConfig && null != this.registryNettyRemotingClient) {
			this.registryNettyRemotingClient.start();
			// 获取到与注册中心集群的一个健康的的Netty 长连接的channel
			getOrUpdateHealthyChannel();
		}

		this.providerNettyRemotingClient.setreconnect(false);
		this.providerNettyRemotingClient.start();

	}

	

	@Override
	public boolean addChannelGroup(String serviceName, ChannelGroup group) {
		return DefaultConsumer.super.addIfAbsent(serviceName, group);
	}

	@Override
	public boolean removeChannelGroup(String serviceName, ChannelGroup group) {
		return DefaultConsumer.super.removedIfAbsent(serviceName, group);
	}

	public ConsumerConfig getConsumerConfig() {
		return consumerConfig;
	}

	public NettyRemotingClient getRegistryNettyRemotingClient() {
		return registryNettyRemotingClient;
	}

	public Channel getRegistyChannel() {
		return registyChannel;
	}

	public ConsumerManager getConsumerManager() {
		return consumerManager;
	}

	public DefaultConsumerRegistry getDefaultConsumerRegistry() {
		return defaultConsumerRegistry;
	}

}
