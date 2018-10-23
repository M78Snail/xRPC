package org.xprc.client.provider;

import org.xprc.common.exception.remoting.RemotingException;
import org.xprc.remoting.model.RemotingTransporter;

import io.netty.channel.Channel;

public interface Provider {

	void start() throws InterruptedException, RemotingException;

	void publishedAndStartProvider() throws InterruptedException, RemotingException;

	Provider serviceListenPort(int exposePort);

	Provider registryAddress(String registryAddress);

	Provider monitorAddress(String monitorAddress);

	Provider publishService(Object... obj);

	void handlerRPCRequest(RemotingTransporter request, Channel channel);

}
