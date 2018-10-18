package org.xprc.common.transport.body;

import org.xprc.common.exception.remoting.RemotingCommmonCustomException;

public interface CommonCustomBody {
	void checkFields() throws RemotingCommmonCustomException;
}
