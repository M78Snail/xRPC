package org.xprc.common.utils;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class SystemClock {
	private static final SystemClock millisClock = new SystemClock(1);

	private final long precision;
	private final AtomicLong now;

	public static SystemClock millisClock() {
		return millisClock;
	}

	private SystemClock(long precision) {
		this.precision = precision;
		now = new AtomicLong(System.currentTimeMillis());
		scheduleClockUpdating();
	}

	private void scheduleClockUpdating() {
		ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {

			public Thread newThread(Runnable runnable) {
				Thread t = new Thread(runnable, "system.clock");
				t.setDaemon(true);
				return t;
			}
		});

		scheduler.scheduleAtFixedRate(new Runnable() {

			public void run() {
				now.set(System.currentTimeMillis());
			}
		}, precision, precision, TimeUnit.MILLISECONDS);
	}

	public long now() {
		return now.get();
	}

	public long precision() {
		return precision;
	}
}
