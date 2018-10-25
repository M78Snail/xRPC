package org.xprc.base.registry;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xprc.base.registry.model.RegistryPersistRecord;
import org.xprc.base.registry.model.RegistryPersistRecord.PersistProviderInfo;
import org.xprc.common.loadbalance.LoadBalanceStrategy;
import org.xprc.common.protocal.XrpcProtocol;
import org.xprc.common.rpc.RegisterMeta;
import org.xprc.common.rpc.RegisterMeta.Address;
import org.xprc.common.rpc.ServiceReviewState;
import org.xprc.common.serialization.SerializerHolder;
import org.xprc.common.transport.body.AckCustomBody;
import org.xprc.common.transport.body.PublishServiceCustomBody;
import org.xprc.common.transport.body.SubcribeResultCustomBody;
import org.xprc.common.transport.body.SubscribeRequestCustomBody;
import org.xprc.common.utils.PersistUtils;
import org.xprc.remoting.model.RemotingTransporter;

import com.alibaba.fastjson.JSON;

import io.netty.channel.Channel;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.netty.util.internal.ConcurrentSet;

public class RegistryProviderManager {

	private static final Logger logger = LoggerFactory.getLogger(RegistryProviderManager.class);

	private static final AttributeKey<ConcurrentSet<String>> S_SUBSCRIBE_KEY = AttributeKey
			.valueOf("server.subscribed");
	private static final AttributeKey<ConcurrentSet<RegisterMeta>> S_PUBLISH_KEY = AttributeKey
			.valueOf("server.published");

	// 某个服务 在哪个地址里
	private final ConcurrentMap<String, ConcurrentMap<Address, RegisterMeta>> globalRegisterInfoMap = new ConcurrentHashMap<String, ConcurrentMap<Address, RegisterMeta>>();
	// 指定节点都注册了哪些服务
	private final ConcurrentMap<Address, ConcurrentSet<String>> globalServiceMetaMap = new ConcurrentHashMap<RegisterMeta.Address, ConcurrentSet<String>>();
	// 某个服务 订阅它的消费者的channel集合
	private final ConcurrentMap<String, ConcurrentSet<Channel>> globalConsumerMetaMap = new ConcurrentHashMap<String, ConcurrentSet<Channel>>();
	// 提供者某个地址对应的channel
	private final ConcurrentMap<Address, Channel> globalProviderChannelMetaMap = new ConcurrentHashMap<RegisterMeta.Address, Channel>();
	// 每个服务的历史记录
	private final ConcurrentMap<String, RegistryPersistRecord> historyRecords = new ConcurrentHashMap<String, RegistryPersistRecord>();
	// 每个服务对应的负载策略
	private final ConcurrentMap<String, LoadBalanceStrategy> globalServiceLoadBalance = new ConcurrentHashMap<String, LoadBalanceStrategy>();

	private DefaultRegistryServer defaultRegistryServer;

	public RegistryProviderManager(DefaultRegistryServer defaultRegistryServer) {
		this.defaultRegistryServer = defaultRegistryServer;
	}

	/**
	 * 持久化操作 原则：1)首先优先从globalRegisterInfoMap中持久化到库中
	 * 2)如果globalRegisterInfoMap中没有信息，则从老版本中的historyRecords中的信息重新保存到硬盘中去,这样做的好处就是不需要多维护一个historyRecords这个全局变量的信息有效性
	 * 
	 * 这样做的原因是因为，只要有服务注册到注册中心，在注册的处理的时候，已经从历史中获取到以前审核和负载的情况，所以globalRegisterInfoMap中的信息是最新的
	 * 如果有些服务以前注册过，但这次重启之后没有注册，所以就需要重新将其更新一下合并记录
	 * 
	 * @throws IOException
	 */
	public void persistServiceInfo() {
		Map<String, RegistryPersistRecord> persistMap = new HashMap<String, RegistryPersistRecord>();
		ConcurrentMap<String, ConcurrentMap<Address, RegisterMeta>> _globalRegisterInfoMap = this.globalRegisterInfoMap; // _stack
																															// copy
		ConcurrentMap<String, LoadBalanceStrategy> _globalServiceLoadBalance = this.globalServiceLoadBalance; // _stack
																												// copy
		ConcurrentMap<String, RegistryPersistRecord> _historyRecords = this.historyRecords;

		// globalRegisterInfoMap 中保存
		if (_globalRegisterInfoMap.keySet() != null) {
			for (String serviceName : _globalRegisterInfoMap.keySet()) {
				RegistryPersistRecord persistRecord = new RegistryPersistRecord();
				persistRecord.setServiceName(serviceName);
				persistRecord.setBalanceStrategy(_globalServiceLoadBalance.get(serviceName));

				List<PersistProviderInfo> providerInfos = new ArrayList<PersistProviderInfo>();
				ConcurrentMap<Address, RegisterMeta> serviceMap = _globalRegisterInfoMap.get(serviceName);
				for (Address address : serviceMap.keySet()) {
					PersistProviderInfo info = new PersistProviderInfo();
					info.setAddress(address);
					info.setIsReviewed(serviceMap.get(address).getIsReviewed());
					providerInfos.add(info);
				}
				persistRecord.setProviderInfos(providerInfos);
				persistMap.put(serviceName, persistRecord);

			}
		}

		if (null != _historyRecords.keySet()) {
			for (String serviceName : _historyRecords.keySet()) {
				// 不包含的时候
				if (!persistMap.keySet().contains(serviceName)) {
					persistMap.put(serviceName, _historyRecords.get(serviceName));
				} else {
					// 负载策略不需要合并更新，需要更新的是existRecord中没有的provider的信息
					List<PersistProviderInfo> providerInfos = new ArrayList<PersistProviderInfo>();
					RegistryPersistRecord existRecord = persistMap.get(serviceName);
					providerInfos.addAll(existRecord.getProviderInfos());
					// 可能需要合并的信息，合并原则，如果同地址的审核策略以globalRegisterInfoMap为准，如果不同地址，则合并信息
					RegistryPersistRecord possibleMergeRecord = _historyRecords.get(serviceName);
					List<PersistProviderInfo> possibleProviderInfos = possibleMergeRecord.getProviderInfos();
					for (PersistProviderInfo eachPossibleInfo : possibleProviderInfos) {
						Address address = eachPossibleInfo.getAddress();

						boolean exist = false;
						for (PersistProviderInfo existProviderInfo : providerInfos) {
							if (existProviderInfo.getAddress().equals(address)) {
								exist = true;
								break;
							}
						}
						if (!exist) {
							providerInfos.add(eachPossibleInfo);
						}
					}

					existRecord.setProviderInfos(providerInfos);
					persistMap.put(serviceName, existRecord);
				}

			}
			if (null != persistMap.values() && !persistMap.values().isEmpty()) {

				String jsonString = JSON.toJSONString(persistMap.values());

				if (jsonString != null) {
					try {
						PersistUtils.string2File(jsonString,
								this.defaultRegistryServer.getRegistryServerConfig().getStorePathRootDir());
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}

		}

	}

	public RemotingTransporter handlerRegister(RemotingTransporter remotingTransporter, Channel channel) {
		// 准备好ack信息返回个provider，悲观主义，默认返回失败ack，要求provider重新发送请求
		AckCustomBody ackCustomBody = new AckCustomBody(remotingTransporter.getOpaque(), false);
		RemotingTransporter responseTransporter = RemotingTransporter.createResponseTransporter(XrpcProtocol.ACK,
				ackCustomBody, remotingTransporter.getOpaque());

		// 接收到主体信息
		PublishServiceCustomBody publishServiceCustomBody = SerializerHolder.serializerImpl()
				.readObject(remotingTransporter.bytes(), PublishServiceCustomBody.class);

		RegisterMeta meta = RegisterMeta.createRegiserMeta(publishServiceCustomBody, channel);

		if (logger.isDebugEnabled()) {
			logger.info("Publish [{}] on channel[{}].", meta, channel);
		}

		// channel上打上该服务的标记 方便当channel inactive的时候，直接从channel上拿到标记的属性，通知
		attachPublishEventOnChannel(meta, channel);

		// 一个服务的最小单元，也是确定一个服务的最小单位
		final String serviceName = meta.getServiceName();

		// 找出提供此服务的全部地址和该服务在该地址下的审核情况
		ConcurrentMap<Address, RegisterMeta> maps = this.getRegisterMeta(serviceName);

		synchronized (globalRegisterInfoMap) {
			// 历史记录中的所有服务的持久化的信息记录
			ConcurrentMap<String, RegistryPersistRecord> concurrentMap = historyRecords;
			// 获取到这个地址可能以前注册过的注册信息
			RegisterMeta existRegiserMeta = maps.get(meta.getAddress());
			// 如果等于空，则说明以前没有注册过 这就需要从历史记录中将某些服务以前注册审核的信息恢复一下记录
			if (null == existRegiserMeta) {
				RegistryPersistRecord persistRecord = concurrentMap.get(serviceName);
				// 如果历史记录中没有记录该信息，也就说持久化中没有记录到该信息的时候，就需要构造默认的持久化信息
				if (null == persistRecord) {
					persistRecord = new RegistryPersistRecord();
					// 持久化的服务名
					persistRecord.setServiceName(serviceName);
					// 默认的负载均衡的策略
					persistRecord.setBalanceStrategy(LoadBalanceStrategy.WEIGHTINGRANDOM);

					PersistProviderInfo providerInfo = new PersistProviderInfo();
					// 服务提供者的地址
					providerInfo.setAddress(meta.getAddress());
					// 服务默认是未审核
					providerInfo.setIsReviewed(ServiceReviewState.HAS_NOT_REVIEWED);
					persistRecord.getProviderInfos().add(providerInfo);

					concurrentMap.put(serviceName, persistRecord);

				}
				// 循环该服务的所有服务提供者实例的信息，获取到当前实例的审核状态，设置好meta的审核信息
				for (PersistProviderInfo providerInfo : persistRecord.getProviderInfos()) {
					if (providerInfo.getAddress().equals(meta.getAddress())) {
						meta.setIsReviewed(providerInfo.getIsReviewed());
					}
				}
				// 向globalRegisterInfoMap 添加该服务名称 对应的
				existRegiserMeta = meta;
				maps.put(meta.getAddress(), existRegiserMeta);

			}

			this.getServiceMeta(meta.getAddress()).add(serviceName);
			// 默认的负载均衡的策略
			LoadBalanceStrategy defaultBalanceStrategy = defaultRegistryServer.getRegistryServerConfig()
					.getDefaultLoadBalanceStrategy();
			if (null != concurrentMap.get(serviceName)) {
				RegistryPersistRecord persistRecord = concurrentMap.get(serviceName);
				if (null != persistRecord.getBalanceStrategy()) {
					defaultBalanceStrategy = persistRecord.getBalanceStrategy();
				}
			}

			// 设置该服务默认的负载均衡的策略
			globalServiceLoadBalance.put(serviceName, defaultBalanceStrategy);
			// 判断provider发送的信息已经被成功的存储的情况下，则告之服务注册成功
			ackCustomBody.setSuccess(true);

			// 如果审核通过，则通知相关服务的订阅者
			if (meta.getIsReviewed() == ServiceReviewState.PASS_REVIEW) {
				this.defaultRegistryServer.getConsumerManager().notifyMacthedSubscriber(meta,
						globalServiceLoadBalance.get(serviceName));
			}
		}

		// 将地址与该channel绑定好，方便其他地方使用
		globalProviderChannelMetaMap.put(meta.getAddress(), channel);

		return responseTransporter;
	}

	public RemotingTransporter handlerRegisterCancel(RemotingTransporter request, Channel channel) {
		// 准备好ack信息返回个provider，悲观主义，默认返回失败ack，要求provider重新发送请求
		AckCustomBody ackCustomBody = new AckCustomBody(request.getOpaque(), false);

		RemotingTransporter responseTransporter = RemotingTransporter.createResponseTransporter(XrpcProtocol.ACK,
				ackCustomBody, request.getOpaque());

		// 接收到主体信息
		PublishServiceCustomBody publishServiceCustomBody = SerializerHolder.serializerImpl()
				.readObject(request.bytes(), PublishServiceCustomBody.class);

		RegisterMeta meta = RegisterMeta.createRegiserMeta(publishServiceCustomBody, channel);

		handlePublishCancel(meta, channel);

		ackCustomBody.setSuccess(true);

		globalProviderChannelMetaMap.remove(meta.getAddress());

		return responseTransporter;
	}

	/**
	 * 处理服务消费者consumer订阅服务的请求
	 * 
	 * @param request
	 * @param channel
	 * @return
	 */
	public RemotingTransporter handleSubscribe(RemotingTransporter request, Channel channel) {
		SubcribeResultCustomBody subcribeResultCustomBody = new SubcribeResultCustomBody();

		RemotingTransporter responseTransporter = RemotingTransporter
				.createResponseTransporter(XrpcProtocol.SUBCRIBE_RESULT, subcribeResultCustomBody, request.getOpaque());

		// 接收到主体信息
		SubscribeRequestCustomBody requestCustomBody = SerializerHolder.serializerImpl().readObject(request.bytes(),
				SubscribeRequestCustomBody.class);
		String serviceName = requestCustomBody.getServiceName();
		// 将其降入到channel的group中去
		this.defaultRegistryServer.getConsumerManager().getSubscriberChannels().add(channel);

		// 存储消费者信息
		ConcurrentSet<Channel> channels = globalConsumerMetaMap.get(serviceName);
		if (null == channels) {
			channels = new ConcurrentSet<Channel>();
		}
		channels.add(channel);
		globalConsumerMetaMap.put(serviceName, channels);

		// 将订阅的channel上打上tag标记，表示该channel订阅的服务
		attachSubscribeEventOnChannel(serviceName, channel);

		ConcurrentMap<Address, RegisterMeta> maps = this.getRegisterMeta(serviceName);
		// 如果订阅的暂时还没有服务提供者，则返回空列表给订阅者
		if (maps.isEmpty()) {
			return responseTransporter;
		}
		// 构建返回的订阅信息的对象
		buildSubcribeResultCustomBody(maps, subcribeResultCustomBody);
		return responseTransporter;
	}

	public RemotingTransporter handleManager(RemotingTransporter request, Channel channel) {
		// TODO Auto-generated method stub
		return null;
	}

	public void handlePublishCancel(RegisterMeta meta, Channel channel) {
		if (logger.isDebugEnabled()) {
			logger.info("Cancel publish {} on channel{}.", meta, channel);
		}
		// 将其channel上打上的标记移除掉
		attachPublishCancelEventOnChannel(meta, channel);

		final String serviceMeta = meta.getServiceName();
		ConcurrentMap<Address, RegisterMeta> maps = this.getRegisterMeta(serviceMeta);
		if (maps.isEmpty()) {
			return;
		}
		synchronized (globalRegisterInfoMap) {
			Address address = meta.getAddress();
			// 从 globalRegisterInfoMap 中删除，该服务在该地址没有
			RegisterMeta data = maps.remove(address);
			if (data != null) {
				// 从globalServiceMetaMap 中删除，该地址下不再包含该服务
				this.getServiceMeta(address).remove(serviceMeta);

				if (data.getIsReviewed() == ServiceReviewState.PASS_REVIEW) {
					this.defaultRegistryServer.getConsumerManager().notifyMacthedSubscriberCancel(meta);
				}

			}
		}

	}

	private void attachPublishCancelEventOnChannel(RegisterMeta meta, Channel channel) {
		Attribute<ConcurrentSet<RegisterMeta>> attr = channel.attr(S_PUBLISH_KEY);
		ConcurrentSet<RegisterMeta> registerMetaSet = attr.get();
		if (registerMetaSet == null) {
			ConcurrentSet<RegisterMeta> newRegisterMetaSet = new ConcurrentSet<>();
			registerMetaSet = attr.setIfAbsent(newRegisterMetaSet);
			if (registerMetaSet == null) {
				registerMetaSet = newRegisterMetaSet;
			}
		}

		registerMetaSet.remove(meta);

	}

	private void attachPublishEventOnChannel(RegisterMeta meta, Channel channel) {
		Attribute<ConcurrentSet<RegisterMeta>> attr = channel.attr(S_PUBLISH_KEY);
		ConcurrentSet<RegisterMeta> registerMetaSet = attr.get();
		if (registerMetaSet == null) {
			ConcurrentSet<RegisterMeta> newRegisterMetaSet = new ConcurrentSet<>();
			registerMetaSet = attr.setIfAbsent(newRegisterMetaSet);
			if (registerMetaSet == null) {
				registerMetaSet = newRegisterMetaSet;
			}
		}
		registerMetaSet.add(meta);

	}

	private ConcurrentMap<Address, RegisterMeta> getRegisterMeta(String serviceMeta) {
		ConcurrentMap<Address, RegisterMeta> maps = globalRegisterInfoMap.get(serviceMeta);
		if (maps == null) {
			ConcurrentMap<Address, RegisterMeta> newMaps = new ConcurrentHashMap<RegisterMeta.Address, RegisterMeta>();
			maps = globalRegisterInfoMap.putIfAbsent(serviceMeta, newMaps);
			if (maps == null) {
				maps = newMaps;
			}
		}

		return maps;
	}

	private ConcurrentSet<String> getServiceMeta(Address address) {
		ConcurrentSet<String> serviceMetaSet = globalServiceMetaMap.get(address);
		if (serviceMetaSet == null) {
			ConcurrentSet<String> newServiceMetaSet = new ConcurrentSet<>();

			serviceMetaSet = globalServiceMetaMap.putIfAbsent(address, newServiceMetaSet);
			if (serviceMetaSet == null) {
				serviceMetaSet = newServiceMetaSet;
			}
		}
		return serviceMetaSet;

	}

	private void attachSubscribeEventOnChannel(String serviceMeta, Channel channel) {
		Attribute<ConcurrentSet<String>> attr = channel.attr(S_SUBSCRIBE_KEY);
		ConcurrentSet<String> serviceMetaSet = attr.get();
		if (serviceMetaSet == null) {
			ConcurrentSet<String> newServiceMetaSet = new ConcurrentSet<>();
			serviceMetaSet = attr.setIfAbsent(newServiceMetaSet);
			if (serviceMetaSet == null) {
				serviceMetaSet = newServiceMetaSet;
			}
		}
		serviceMetaSet.add(serviceMeta);
	}

	private void buildSubcribeResultCustomBody(ConcurrentMap<Address, RegisterMeta> maps,
			SubcribeResultCustomBody subcribeResultCustomBody) {
		Collection<RegisterMeta> values = maps.values();
		if (values != null && values.size() > 0) {
			List<RegisterMeta> registerMetas = new ArrayList<RegisterMeta>();
			for (RegisterMeta meta : values) {
				// 判断是否人工审核过，审核过的情况下，组装给consumer的响应主体，返回个consumer
				if (meta.getIsReviewed() == ServiceReviewState.PASS_REVIEW) {
					registerMetas.add(meta);
				}
			}
			subcribeResultCustomBody.setRegisterMeta(registerMetas);
		}
	}

	public ConcurrentMap<String, RegistryPersistRecord> getHistoryRecords() {
		return historyRecords;
	}

}
