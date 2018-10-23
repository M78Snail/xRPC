package org.xrpc.example.netty;

import java.util.concurrent.Executors;

import org.xprc.common.protocal.XrpcProtocol;
import org.xprc.common.serialization.SerializerHolder;
import org.xprc.remoting.model.NettyRequestProcessor;
import org.xprc.remoting.model.RemotingTransporter;
import org.xprc.remoting.netty.NettyRemotingServer;
import org.xprc.remoting.netty.NettyServerConfig;

import io.netty.channel.ChannelHandlerContext;

public class NettyServerTest {

	public static final byte TEST = -1;

	public static void main(String[] args) {
		NettyServerConfig config = new NettyServerConfig();
		config.setListenPort(18001);
		NettyRemotingServer server = new NettyRemotingServer(config);
		server.registerProecessor(TEST, new NettyRequestProcessor() {

			@Override
			public RemotingTransporter processRequest(ChannelHandlerContext ctx, RemotingTransporter transporter) {
				transporter.setCustomHeader(
						SerializerHolder.serializerImpl().readObject(transporter.bytes(), TestCommonCustomBody.class));
				System.out.println(transporter);
				transporter.setTransporterType(XrpcProtocol.RESPONSE_REMOTING);
				return transporter;
			}
		}, Executors.newCachedThreadPool());
		server.start();
	}

}
