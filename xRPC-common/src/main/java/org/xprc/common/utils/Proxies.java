package org.xprc.common.utils;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy.Default;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.matcher.ElementMatchers;
import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.MethodInterceptor;

public enum Proxies {
	JDK_PROXY(new ProxyDelegate() {

		@Override
		public <T> T newProxy(Class<T> interfaceType, Object handler) {

			Object object = Proxy.newProxyInstance(interfaceType.getClassLoader(), new Class<?>[] { interfaceType },
					(InvocationHandler) handler);

			return interfaceType.cast(object);
		}
	}), CG_LIB(new ProxyDelegate() {

		@Override
		public <T> T newProxy(Class<T> interfaceType, Object handler) {

			Enhancer enhancer = new Enhancer();
			enhancer.setSuperclass(interfaceType);
			enhancer.setCallback((MethodInterceptor) handler);
			enhancer.setClassLoader(interfaceType.getClassLoader());

			return interfaceType.cast(enhancer.create());
		}
	}), BYTE_BUDDY(new ProxyDelegate() {

		@Override
		public <T> T newProxy(Class<T> interfaceType, Object handler) {
			Class<? extends T> cls = new ByteBuddy().subclass(interfaceType)
					.method(ElementMatchers.isDeclaredBy(interfaceType))
					.intercept(MethodDelegation.to(handler, "handler")
							.filter(ElementMatchers.not(ElementMatchers.isDeclaredBy(Object.class))))
					.make().load(interfaceType.getClassLoader(), Default.INJECTION).getLoaded();

			return Reflects.newInstance(cls);
		}
	});

	private final ProxyDelegate delegate;

	Proxies(ProxyDelegate delegate) {
		this.delegate = delegate;
	}

	public static Proxies getDefault() {
		return BYTE_BUDDY;
	}

	public <T> T newProxy(Class<T> interfaceType, Object handler) {
		return delegate.newProxy(interfaceType, handler);
	}

	interface ProxyDelegate {

		/**
		 * Returns a proxy instance that implements {@code interfaceType} by
		 * dispatching method invocations to {@code handler}. The class loader
		 * of {@code interfaceType} will be used to define the proxy class.
		 */
		<T> T newProxy(Class<T> interfaceType, Object handler);
	}
}
