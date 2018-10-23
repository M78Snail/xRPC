package org.xprc.client.provider;

import java.util.List;

import org.xprc.client.provider.interceptor.ProviderProxyHandler;
import org.xprc.client.provider.model.ServiceWrapper;

public interface ServiceWrapperWorker {
	
	ServiceWrapperWorker provider(Object serviceProvider);
	
	ServiceWrapperWorker provider(ProviderProxyHandler proxyHandler,Object serviceProvider);

	List<ServiceWrapper> create();
}
