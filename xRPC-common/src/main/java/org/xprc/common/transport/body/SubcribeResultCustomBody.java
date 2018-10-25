package org.xprc.common.transport.body;

import java.util.ArrayList;
import java.util.List;

import org.xprc.common.exception.remoting.RemotingCommmonCustomException;
import org.xprc.common.loadbalance.LoadBalanceStrategy;
import org.xprc.common.rpc.RegisterMeta;

public class SubcribeResultCustomBody implements CommonCustomBody {

	private String serviceName;

	private LoadBalanceStrategy loadBalanceStrategy;

	private List<RegisterMeta> registerMeta = new ArrayList<RegisterMeta>();

	@Override
	public void checkFields() throws RemotingCommmonCustomException {
		// TODO Auto-generated method stub

	}

	public List<RegisterMeta> getRegisterMeta() {
		return registerMeta;
	}

	public void setRegisterMeta(List<RegisterMeta> registerMeta) {
		this.registerMeta = registerMeta;
	}

	public LoadBalanceStrategy getLoadBalanceStrategy() {
		return loadBalanceStrategy;
	}

	public void setLoadBalanceStrategy(LoadBalanceStrategy loadBalanceStrategy) {
		this.loadBalanceStrategy = loadBalanceStrategy;
	}

	public String getServiceName() {
		return serviceName;
	}

	public void setServiceName(String serviceName) {
		this.serviceName = serviceName;
	}

}
