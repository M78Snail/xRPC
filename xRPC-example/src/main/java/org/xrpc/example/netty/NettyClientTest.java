package org.xrpc.example.netty;

import org.xprc.common.exception.remoting.RemotingException;
import org.xprc.common.exception.remoting.RemotingSendRequestException;
import org.xprc.common.exception.remoting.RemotingTimeoutException;
import org.xprc.remoting.model.RemotingTransporter;
import org.xprc.remoting.netty.NettyClientConfig;
import org.xprc.remoting.netty.NettyRemotingClient;
import org.xrpc.example.netty.TestCommonCustomBody.ComplexTestObj;

public class NettyClientTest {
	public static final byte TEST = -1;

	public static void main(String[] args)
			throws RemotingTimeoutException, RemotingSendRequestException, InterruptedException, RemotingException {
		NettyClientConfig nettyClientConfig = new NettyClientConfig();

		NettyRemotingClient client = new NettyRemotingClient(nettyClientConfig);
		client.start();

		ComplexTestObj complexTestObj = new ComplexTestObj("attr1", 2);
		TestCommonCustomBody commonCustomHeader = new TestCommonCustomBody(1, "test", complexTestObj);

		RemotingTransporter remotingTransporter = RemotingTransporter.createRequestTransporter(TEST,
				commonCustomHeader);

		RemotingTransporter request = client.invokeSync("127.0.0.1:18001", remotingTransporter, 3000);

		System.out.println("=============>>>>>>>>>>"+request);

	}
}
