package org.xprc.common.utils;

import io.netty.channel.Channel;

public interface ChannelGroup {
	/**
	 * 轮询获取某个服务消费者和服务提供者之间的某个channel
	 * 
	 * @return
	 */
	Channel next();

	/**
	 * 向group组中增加一个active的channel
	 * 
	 * @param channel
	 * @return
	 */
	boolean add(Channel channel);

	/**
	 * 将group组中的某个channel移除掉
	 * 
	 * @param channel
	 * @return
	 */
	boolean remove(Channel channel);

	/**
	 * 设置整个group的权重
	 * 
	 * @param weight
	 */
	void setWeight(int weight);

	/**
	 * 获取到整个channelGroup的权重
	 * 
	 * @return
	 */
	int getWeight();

	/**
	 * 
	 * @return
	 */
	int size();

	/**
	 * 
	 * @return
	 */
	boolean isAvailable();

	/**
	 * 
	 * @return
	 */
	UnresolvedAddress getAddress();
}
