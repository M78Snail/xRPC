package org.xprc.remoting;

import org.xprc.remoting.model.RemotingResponse;

public interface InvokeCallback {
	void operationComplete(final RemotingResponse remotingResponse);
}
