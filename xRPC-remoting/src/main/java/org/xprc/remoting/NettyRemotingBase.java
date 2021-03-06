package org.xprc.remoting;

import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xprc.common.exception.remoting.RemotingSendRequestException;
import org.xprc.common.exception.remoting.RemotingTimeoutException;
import org.xprc.common.protocal.XrpcProtocol;
import org.xprc.common.utils.Pair;
import org.xprc.remoting.model.NettyChannelInactiveProcessor;
import org.xprc.remoting.model.NettyRequestProcessor;
import org.xprc.remoting.model.RemotingResponse;
import org.xprc.remoting.model.RemotingTransporter;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;

/**
 * netty C/S 端的客户端提取，子类完成创建netty的事情，该抽象类则完成使用子类创建好的channel去与远程端交互
 * 
 * @author duxiaoming
 *
 */
public abstract class NettyRemotingBase {
	private static final Logger logger = LoggerFactory.getLogger(NettyRemotingBase.class);

	/****** key为请求的opaque value是远程返回的结果封装类 ******/
	protected final ConcurrentHashMap<Long, RemotingResponse> responseTable = new ConcurrentHashMap<Long, RemotingResponse>(
			256);

	// 如果使用者没有对创建的Netty网络段注入某个特定请求的处理器的时候，默认使用该默认的处理器
	protected Pair<NettyRequestProcessor, ExecutorService> defaultRequestProcessor;

	// netty网络对channelInactive事件发生的处理器
	protected Pair<NettyChannelInactiveProcessor, ExecutorService> defaultChannelInactiveProcessor;

	protected final ExecutorService publicExecutor = Executors.newFixedThreadPool(4, new ThreadFactory() {
		private AtomicInteger threadIndex = new AtomicInteger(0);

		@Override
		public Thread newThread(Runnable r) {
			return new Thread(r, "NettyClientPublicExecutor_" + this.threadIndex.incrementAndGet());
		}
	});

	// 注入的某个requestCode对应的处理器放入到HashMap中，键值对一一匹配
	protected final HashMap<Byte/* request code */, Pair<NettyRequestProcessor, ExecutorService>> processorTable = new HashMap<Byte, Pair<NettyRequestProcessor, ExecutorService>>(
			64);

	@SuppressWarnings("unused")
	public RemotingTransporter invokeSyncImpl(final Channel channel, final RemotingTransporter request,
			final long timeoutMillis)
			throws RemotingTimeoutException, RemotingSendRequestException, InterruptedException {

		try {
			// 构造一个请求的封装体，请求Id和请求结果一一对应
			final RemotingResponse remotingResponse = new RemotingResponse(request.getOpaque(), timeoutMillis, null);
			// 将请求放入一个"篮子"中，等远程端填充该篮子中嗷嗷待哺的每一个结果集
			this.responseTable.put(request.getOpaque(), remotingResponse);
			// 发送请求
			channel.writeAndFlush(request).addListener(new ChannelFutureListener() {

				@Override
				public void operationComplete(ChannelFuture future) throws Exception {
					if (future.isSuccess()) {
						remotingResponse.setSendRequestOK(true);
						return;
					} else {
						remotingResponse.setSendRequestOK(false);
					}

					responseTable.remove(request).getOpaque();
					remotingResponse.setCause(future.cause());
					remotingResponse.putResponse(null);

					logger.warn("use channel [{}] send msg [{}] failed and failed reason is [{}]", channel, request,
							future.cause().getMessage());

				}
			});

			RemotingTransporter remotingTransporter = remotingResponse.waitResponse();

			if (remotingResponse == null) {
				// 如果发送是成功的，则说明远程端，处理超时了
				if (remotingResponse.isSendRequestOK()) {
					throw new RemotingTimeoutException(ConnectionUtils.parseChannelRemoteAddr(channel), timeoutMillis,
							remotingResponse.getCause());
				} else {
					throw new RemotingSendRequestException(ConnectionUtils.parseChannelRemoteAddr(channel),
							remotingResponse.getCause());
				}
			}
			return remotingTransporter;

		} finally {
			// 最后不管怎么样，都需要将其从篮子中移除出来，否则篮子会撑爆的
			this.responseTable.remove(request.getOpaque());
		}

	}

	// ChannelRead0方法对应的具体实现
	public void processMessageReceived(ChannelHandlerContext ctx, RemotingTransporter msg) {
		if (logger.isDebugEnabled()) {
			logger.debug("channel [] received RemotingTransporter is [{}]", ctx.channel(), msg);
		}

		final RemotingTransporter remotingTransporter = msg;

		if (remotingTransporter != null) {
			switch (remotingTransporter.getTransporterType()) {

			// 作为server端 client端的请求的对应的处理
			case XrpcProtocol.REQUEST_REMOTING:
				processRemotingRequest(ctx, remotingTransporter);
				break;

			// 作为客户端，来自server端的响应的处理
			case XrpcProtocol.RESPONSE_REMOTING:
				processRemotingResponse(ctx, remotingTransporter);

				break;

			default:
				break;
			}
		}
	}

	protected void processChannelInactive(final ChannelHandlerContext ctx) {
		final Pair<NettyChannelInactiveProcessor, ExecutorService> pair = this.defaultChannelInactiveProcessor;
		if (pair != null) {

			Runnable run = new Runnable() {

				@Override
				public void run() {
					try {
						pair.getKey().processChannelInactive(ctx);
					} catch (RemotingSendRequestException | RemotingTimeoutException | InterruptedException e) {
						logger.error("server occor exception [{}]", e.getMessage());
					}
				}
			};
			try {
				pair.getValue().submit(run);
			} catch (Exception e) {
				logger.error("server is busy,[{}]", e.getMessage());
			}

		}
	}

	/**
	 * 通过注册的process解决问题
	 * 
	 * @param ctx
	 * @param remotingTransporter
	 */
	protected void processRemotingRequest(final ChannelHandlerContext ctx,
			final RemotingTransporter remotingTransporter) {
		final Pair<NettyRequestProcessor, ExecutorService> matchedPair = this.processorTable
				.get(remotingTransporter.getCode());
		final Pair<NettyRequestProcessor, ExecutorService> pair = null == matchedPair ? this.defaultRequestProcessor
				: matchedPair;
		if (pair != null) {
			Runnable runnable = new Runnable() {

				@Override
				public void run() {
					try {
						// TODO: RPC hook

						final RemotingTransporter response = pair.getKey().processRequest(ctx, remotingTransporter);

						if (response != null) {
							ctx.writeAndFlush(response).addListener(new ChannelFutureListener() {

								@Override
								public void operationComplete(ChannelFuture future) throws Exception {
									if (!future.isSuccess()) {
										logger.error("fail send response ,exception is [{}]",
												future.cause().getMessage());
									}
								}
							});
						}
					} catch (Exception e) {
						logger.error("processor occur exception [{}]", e.getMessage());
						final RemotingTransporter response = RemotingTransporter.newInstance(
								remotingTransporter.getOpaque(), XrpcProtocol.RESPONSE_REMOTING,
								XrpcProtocol.HANDLER_ERROR, null);
						ctx.writeAndFlush(response);
					}
				}
			};

			try {
				pair.getValue().submit(runnable);
			} catch (Exception e) {
				logger.error("server is busy,[{}]", e.getMessage());
				final RemotingTransporter response = RemotingTransporter.newInstance(remotingTransporter.getOpaque(),
						XrpcProtocol.RESPONSE_REMOTING, XrpcProtocol.HANDLER_BUSY, null);
				ctx.writeAndFlush(response);
			}
		}
	}

	/**
	 * client处理server端返回的消息的处理
	 * 
	 * @param ctx
	 * @param remotingTransporter
	 */
	protected void processRemotingResponse(final ChannelHandlerContext ctx,
			final RemotingTransporter remotingTransporter) {
		// 从缓存篮子里拿出对应请求的对应响应的载体RemotingResponse
		final RemotingResponse remotingResponse = responseTable.get(remotingTransporter.getOpaque());

		// 不超时的情况下
		if (null != remotingResponse) {
			// 首先先设值，这样会在countdownlatch wait之前把值赋上
			remotingResponse.setRemotingTransporter(remotingTransporter);
			// 可以直接countdown
			remotingResponse.putResponse(remotingTransporter);
			// 从篮子中移除
			responseTable.remove(remotingTransporter.getOpaque());

		} else {
			logger.warn("received response but matched Id is removed from responseTable maybe timeout");
			logger.warn(remotingTransporter.toString());
		}
	}

}
