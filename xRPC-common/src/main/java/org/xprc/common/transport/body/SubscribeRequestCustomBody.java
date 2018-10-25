package org.xprc.common.transport.body;

import org.xprc.common.exception.remoting.RemotingCommmonCustomException;

public class SubscribeRequestCustomBody implements CommonCustomBody {

	private String serviceName;

	public String getServiceName() {
		return serviceName;
	}

	public void setServiceName(String serviceName) {
		this.serviceName = serviceName;
	}

	@Override
	public void checkFields() throws RemotingCommmonCustomException {
	}

}
