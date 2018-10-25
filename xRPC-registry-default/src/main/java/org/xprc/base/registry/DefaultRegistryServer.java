package org.xprc.base.registry;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xprc.base.registry.model.RegistryPersistRecord;
import org.xprc.common.utils.NamedThreadFactory;
import org.xprc.common.utils.PersistUtils;
import org.xprc.registry.RegistryServer;
import org.xprc.remoting.netty.NettyRemotingServer;
import org.xprc.remoting.netty.NettyServerConfig;

import com.alibaba.fastjson.JSON;

public class DefaultRegistryServer implements RegistryServer {
	private static final Logger logger = LoggerFactory.getLogger(DefaultRegistryServer.class);

	// netty Server的一些配置文件
	private NettyServerConfig nettyServerConfig;

	// 注册中心的netty server端
	private NettyRemotingServer remotingServer;

	// 注册中心的配置文件
	private RegistryServerConfig registryServerConfig;

	// 注册中心消费侧的管理逻辑控制类
	private RegistryConsumerManager consumerManager;

	// 注册中心服务提供者的管理逻辑控制类
	private RegistryProviderManager providerManager;

	// 执行器
	private ExecutorService remotingExecutor;

	// channel inactive的线程执行器
	private ExecutorService remotingChannelInactiveExecutor;

	// 定时任务
	private final ScheduledExecutorService scheduledExecutorService = Executors
			.newSingleThreadScheduledExecutor(new NamedThreadFactory("registry-timer"));

	/**
	 * 
	 * @param nettyServerConfig
	 *            注册中心的netty的配置文件 至少需要配置listenPort
	 * @param nettyClientConfig
	 *            注册中心连接Monitor端的netty配置文件，至少需要配置defaultAddress值
	 *            这边monitor是单实例，所以address一个就好
	 */
	public DefaultRegistryServer(NettyServerConfig nettyServerConfig, RegistryServerConfig registryServerConfig) {
		this.nettyServerConfig = nettyServerConfig;
		this.registryServerConfig = registryServerConfig;
		consumerManager = new RegistryConsumerManager(this);
		providerManager = new RegistryProviderManager(this);
		initialize();

	}

	private void initialize() {
		this.remotingServer = new NettyRemotingServer(this.nettyServerConfig);
		this.remotingExecutor = Executors.newFixedThreadPool(nettyServerConfig.getServerWorkerThreads(),
				new NamedThreadFactory("RegistryCenterExecutorThread_"));

		this.remotingChannelInactiveExecutor = Executors.newFixedThreadPool(
				nettyServerConfig.getChannelInactiveHandlerThreads(),
				new NamedThreadFactory("RegistryCenterChannelInActiveExecutorThread_"));
		// 注册处理器
		this.registerProcessor();

		// 从硬盘上恢复一些服务的信息
		this.recoverServiceInfoFromDisk();

		this.scheduledExecutorService.scheduleAtFixedRate(new Runnable() {

			@Override
			public void run() {
				// 延迟60秒，每隔60秒开始 定时向consumer发送消费者消费失败的信息
				
				try {
					DefaultRegistryServer.this.getConsumerManager().checkSendFailedMessage();
				} catch (Exception e) {
					logger.warn("schedule publish failed [{}]", e.getMessage());
				}

			}
		}, 60, 60, TimeUnit.SECONDS);

		this.scheduledExecutorService.scheduleAtFixedRate(new Runnable() {

			@Override
			public void run() {
				// 延迟60秒，每隔一段时间将一些服务信息持久化到硬盘上
				try {
					DefaultRegistryServer.this.getProviderManager().persistServiceInfo();
				} catch (Exception e) {
					logger.warn("schedule persist failed [{}]", e.getMessage());
				}

			}
		}, 60, this.registryServerConfig.getPersistTime(), TimeUnit.SECONDS);

	}

	private void registerProcessor() {
		this.remotingServer.registerDefaultProcessor(new DefaultRegistryProcessor(this), this.remotingExecutor);
		this.remotingServer.registerChannelInactiveProcessor(new DefaultRegistryChannelInactiveProcessor(this),
				remotingChannelInactiveExecutor);
	}

	/**
	 * 从硬盘上恢复一些服务的审核负载算法的信息
	 */
	private void recoverServiceInfoFromDisk() {

		String persistString = PersistUtils.file2String(this.registryServerConfig.getStorePathRootDir());

		if (null != persistString) {
			List<RegistryPersistRecord> registryPersistRecords = JSON.parseArray(persistString.trim(),
					RegistryPersistRecord.class);

			if (null != registryPersistRecords) {
				for (RegistryPersistRecord metricsReporter : registryPersistRecords) {

					String serviceName = metricsReporter.getServiceName();
					this.getProviderManager().getHistoryRecords().put(serviceName, metricsReporter);

				}
			}
		}

	}

	@Override
	public void start() {
		this.remotingServer.start();
	}

	public RegistryConsumerManager getConsumerManager() {
		return consumerManager;
	}

	public RegistryProviderManager getProviderManager() {
		return providerManager;
	}

	public NettyRemotingServer getRemotingServer() {
		return remotingServer;
	}

	public void setRemotingServer(NettyRemotingServer remotingServer) {
		this.remotingServer = remotingServer;
	}

	public RegistryServerConfig getRegistryServerConfig() {
		return registryServerConfig;
	}

	public void setRegistryServerConfig(RegistryServerConfig registryServerConfig) {
		this.registryServerConfig = registryServerConfig;
	}

}
