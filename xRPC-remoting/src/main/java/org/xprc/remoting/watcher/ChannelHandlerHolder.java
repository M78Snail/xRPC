package org.xprc.remoting.watcher;

import io.netty.channel.ChannelHandler;

public interface ChannelHandlerHolder {
	ChannelHandler[] handlers();
}
