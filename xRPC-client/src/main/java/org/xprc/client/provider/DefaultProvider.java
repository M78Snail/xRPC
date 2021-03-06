package org.xprc.client.provider;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xprc.client.provider.model.DefaultProviderInactiveProcessor;
import org.xprc.common.exception.remoting.RemotingException;
import org.xprc.common.protocal.XrpcProtocol;
import org.xprc.common.transport.body.PublishServiceCustomBody;
import org.xprc.common.utils.NamedThreadFactory;
import org.xprc.remoting.model.RemotingTransporter;
import org.xprc.remoting.netty.NettyClientConfig;
import org.xprc.remoting.netty.NettyRemotingClient;
import org.xprc.remoting.netty.NettyRemotingServer;
import org.xprc.remoting.netty.NettyServerConfig;

import io.netty.channel.Channel;

public class DefaultProvider implements Provider {

	private static final Logger logger = LoggerFactory.getLogger(DefaultProvider.class);

	// 向注册中心连接的netty client配置
	private NettyClientConfig clientConfig;

	// 等待服务提供者连接的netty server的配置
	private NettyServerConfig serverConfig;

	// 连接monitor和注册中心
	private NettyRemotingClient nettyRemotingClient;

	// 等待被Consumer连接
	private NettyRemotingServer nettyRemotingServer;

	// 等待被Consumer VIP连接
	private NettyRemotingServer nettyRemotingVipServer;

	// provider端向注册中心连接的业务逻辑的控制器
	private ProviderRegistryController providerController;

	private ProviderRPCController providerRPCController;

	// RPC调用的核心线程执行器
	private ExecutorService remotingExecutor;

	// RPC调用VIP的核心线程执行器
	private ExecutorService remotingVipExecutor;

	// 连接monitor端的channel
	private Channel monitorChannel;

	/********* 要发布的服务的信息 ***********/
	private List<RemotingTransporter> publishRemotingTransporters;

	/************ 全局发布的信息 ************/
	private ConcurrentMap<String, PublishServiceCustomBody> globalPublishService = new ConcurrentHashMap<String, PublishServiceCustomBody>();
	/***** 注册中心的地址 ******/
	private String registryAddress;
	/******* 服务暴露给consumer的地址 ********/
	private int exposePort;
	/************* 监控中心的monitor的地址 *****************/
	private String monitorAddress;
	/*********** 要提供的服务 ***************/
	private Object[] obj;

	// 当前provider端状态是否健康，也就是说如果注册宕机后，该provider端的实例信息是失效，这是需要重新发送注册信息,因为默认状态下start就是发送，只有channel
	// inactive的时候说明短线了，需要重新发布信息
	private boolean ProviderStateIsHealthy = true;

	// 定时任务执行器
	private final ScheduledExecutorService scheduledExecutorService = Executors
			.newSingleThreadScheduledExecutor(new NamedThreadFactory("provider-timer"));

	public DefaultProvider() {
		this.clientConfig = new NettyClientConfig();
		this.serverConfig = new NettyServerConfig();
		providerController = new ProviderRegistryController(this);
		providerRPCController = new ProviderRPCController(this);
		initialize();
	}

	public DefaultProvider(NettyClientConfig clientConfig, NettyServerConfig serverConfig) {
		this.clientConfig = clientConfig;
		this.serverConfig = serverConfig;
		providerController = new ProviderRegistryController(this);
		providerRPCController = new ProviderRPCController(this);
		initialize();
	}

	private void initialize() {
		this.nettyRemotingServer = new NettyRemotingServer(this.serverConfig);
		this.nettyRemotingClient = new NettyRemotingClient(this.clientConfig);
		this.nettyRemotingVipServer = new NettyRemotingServer(this.serverConfig);
		this.remotingExecutor = Executors.newFixedThreadPool(serverConfig.getServerWorkerThreads(),
				new NamedThreadFactory("providerExecutorThread_"));
		this.remotingVipExecutor = Executors.newFixedThreadPool(serverConfig.getServerWorkerThreads() / 2,
				new NamedThreadFactory("providerExecutorThread_"));

		// 注册处理器
		this.registerProcessor();

		this.scheduledExecutorService.scheduleAtFixedRate(new Runnable() {
			@Override
			public void run() {
				// 延迟5秒，每隔60秒开始 像其发送注册服务信息
				try {
					logger.info("schedule check publish service");
					if (!ProviderStateIsHealthy) {
						logger.info("channel which connected to registry,has been inactived,need to republish service");
						DefaultProvider.this.publishedAndStartProvider();
					}
				} catch (Exception e) {
					logger.warn("schedule publish failed [{}]", e.getMessage());
				}
			}
		}, 60, 60, TimeUnit.SECONDS);

		this.scheduledExecutorService.scheduleAtFixedRate(new Runnable() {

			@Override
			public void run() {
				try {
					logger.info("ready send message");
					DefaultProvider.this.providerController.getRegistryController().checkPublishFailMessage();
				} catch (InterruptedException | RemotingException e) {
					logger.warn("schedule republish failed [{}]", e.getMessage());
				}
			}
		}, 1, 1, TimeUnit.MINUTES);

		// TODO 清理所有的服务的单位时间的失效过期的统计信息
		this.scheduledExecutorService.scheduleAtFixedRate(new Runnable() {

			@Override
			public void run() {
				logger.info("ready prepare send Report");
				DefaultProvider.this.providerController.getServiceFlowControllerManager()
						.clearAllServiceNextMinuteCallCount();
			}
		}, 5, 45, TimeUnit.SECONDS);

		// TODO 如果监控中心的地址不是null，则需要定时发送统计信息
		this.scheduledExecutorService.scheduleAtFixedRate(new Runnable() {

			@Override
			public void run() {
				// DefaultProvider.this.providerController.getProviderMonitorController().sendMetricsInfo();
			}
		}, 5, 60, TimeUnit.SECONDS);

		// TODO 每隔60s去校验与monitor端的channel是否健康，如果不健康，或者inactive的时候，则重新去链接
		this.scheduledExecutorService.scheduleAtFixedRate(new Runnable() {

			@Override
			public void run() {
				// try {
				// DefaultProvider.this.providerController.getProviderMonitorController().checkMonitorChannel();
				// } catch (InterruptedException e) {
				// logger.warn("schedule check monitor channel failed [{}]",
				// e.getMessage());
				// }
			}
		}, 30, 60, TimeUnit.SECONDS);

		// 检查是否有服务需要自动降级
		this.scheduledExecutorService.scheduleAtFixedRate(new Runnable() {

			@Override
			public void run() {
				DefaultProvider.this.providerController.checkAutoDegrade();
			}
		}, 30, 60, TimeUnit.SECONDS);
	}

	@Override
	public void start() throws InterruptedException, RemotingException {
		logger.info("######### provider starting..... ########");
		// 编织服务
		this.publishRemotingTransporters = providerController.getLocalServerWrapperManager()
				.wrapperRegisterInfo(this.getExposePort(), this.obj);

		logger.info("registry center address [{}] servicePort [{}] service [{}]", this.registryAddress, this.exposePort,
				this.publishRemotingTransporters);
		// 记录发布的信息的记录，方便其他地方做使用
		initGlobalService();

		nettyRemotingClient.start();

		try {
			// 发布任务
			this.publishedAndStartProvider();
			logger.info("######### provider start successfully..... ########");
		} catch (Exception e) {
			logger.error("publish service to registry failed [{}]", e.getMessage());
		}

		int _port = this.exposePort;

		if (_port != 0) {
			this.serverConfig.setListenPort(exposePort);
			this.nettyRemotingServer.start();

			int vipPort = _port - 2;
			this.serverConfig.setListenPort(vipPort);
			this.nettyRemotingVipServer.start();
		}

		// if (monitorAddress != null) {
		// initMonitorChannel();
		// }

	}

	private void initGlobalService() {
		List<RemotingTransporter> list = this.publishRemotingTransporters; // Stack
																			// copy

		if (null != list && !list.isEmpty()) {
			for (RemotingTransporter remotingTransporter : list) {
				PublishServiceCustomBody customBody = (PublishServiceCustomBody) remotingTransporter.getCustomHeader();
				String serviceName = customBody.getServiceProviderName();
				this.globalPublishService.put(serviceName, customBody);
			}
		}
	}

	@Override
	public void publishedAndStartProvider() throws InterruptedException, RemotingException {
		logger.info("publish service....");
		providerController.getRegistryController().publishedAndStartProvider();
		// 发布之后再次将服务状态改成true
		ProviderStateIsHealthy = true;
	}

	@Override
	public Provider serviceListenPort(int exposePort) {
		this.exposePort = exposePort;
		return this;
	}

	@Override
	public Provider registryAddress(String registryAddress) {
		this.registryAddress = registryAddress;
		return this;
	}

	@Override
	public Provider monitorAddress(String monitorAddress) {
		this.monitorAddress = monitorAddress;
		return this;
	}

	@Override
	public Provider publishService(Object... obj) {
		this.obj = obj;
		return this;
	}

	@Override
	public void handlerRPCRequest(RemotingTransporter request, Channel channel) {
		providerRPCController.handlerRPCRequest(request, channel);
	}

	public List<RemotingTransporter> getPublishRemotingTransporters() {
		return publishRemotingTransporters;
	}

	public String getRegistryAddress() {
		return registryAddress;
	}

	public NettyRemotingClient getNettyRemotingClient() {
		return nettyRemotingClient;
	}

	// 注册处理器
	private void registerProcessor() {
		DefaultProviderRegistryProcessor defaultProviderRegistryProcessor = new DefaultProviderRegistryProcessor(this);
		// provider端作为client端去连接registry注册中心的处理器
		this.nettyRemotingClient.registerProcessor(XrpcProtocol.DEGRADE_SERVICE, defaultProviderRegistryProcessor,
				null);
		this.nettyRemotingClient.registerProcessor(XrpcProtocol.AUTO_DEGRADE_SERVICE, defaultProviderRegistryProcessor,
				null);
		// provider端连接registry链接inactive的时候要进行的操作(设置registry的状态为不健康，告之registry重新发送服务注册信息)
		this.nettyRemotingClient.registerChannelInactiveProcessor(new DefaultProviderInactiveProcessor(this), null);

		// provider端作为netty的server端去等待调用者连接的处理器，此处理器只处理RPC请求
		this.nettyRemotingServer.registerDefaultProcessor(new DefaultProviderRPCProcessor(this), this.remotingExecutor);
		this.nettyRemotingVipServer.registerDefaultProcessor(new DefaultProviderRPCProcessor(this),
				this.remotingVipExecutor);

	}

	public int getExposePort() {
		return exposePort;
	}

	public ProviderRegistryController getProviderController() {
		return providerController;
	}

	public void setProviderStateIsHealthy(boolean providerStateIsHealthy) {
		ProviderStateIsHealthy = providerStateIsHealthy;
	}

}
