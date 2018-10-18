package org.xprc.common.serialization;

import org.xprc.common.spi.BaseServiceLoader;

public final class SerializerHolder {

	// SPI
	private static final Serializer serializer = BaseServiceLoader.load(Serializer.class);

	public static Serializer serializerImpl() {
		return serializer;
	}
}
