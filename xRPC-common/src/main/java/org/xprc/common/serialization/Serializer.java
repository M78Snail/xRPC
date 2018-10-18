package org.xprc.common.serialization;

public interface Serializer {
	/**
	 * 将对象序列化成byte[]
	 * @param <T>
	 * 
	 * @param obj
	 * @return
	 */
	 <T> byte[] writeObject(T obj);

	/**
	 * 将byte数组反序列成对象
	 * 
	 * @param bytes
	 * @param clazz
	 * @return
	 */
	<T> T readObject(byte[] bytes, Class<T> clazz);
}
