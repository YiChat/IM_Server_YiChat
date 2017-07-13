package tigase.util;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ThreadPoolUtils {

	private static final int DEFAULT_THREAD_MIN_SIZE = 10;

	private static final int DEFAULT_THREAD_MAX_SIZE = 30;

	private static final int DEFAULT_QUEUE_CAPACITY = 5000;

	public static ThreadPoolExecutor newCachePool(String threadName,
			String minSize, String maxSize, String queueSize) {
		int core = DEFAULT_THREAD_MIN_SIZE;
//		int core = JiveGlobals.getIntProperty(minSize, DEFAULT_THREAD_MIN_SIZE);
		int max = DEFAULT_THREAD_MAX_SIZE;
//		int max = JiveGlobals.getIntProperty(maxSize, DEFAULT_THREAD_MAX_SIZE);
		int capacity = DEFAULT_QUEUE_CAPACITY;
//		int capacity = JiveGlobals.getIntProperty(queueSize, DEFAULT_QUEUE_CAPACITY);
		return new ThreadPoolExecutor(core, max, 60L, TimeUnit.SECONDS,
				new LinkedBlockingQueue<Runnable>(capacity),
				new NamedThreadFactory(threadName),
				new ThreadPoolExecutor.CallerRunsPolicy());
	}

	public static ThreadPoolExecutor newFixedPool(String threadName, String size) {
		int core = DEFAULT_THREAD_MAX_SIZE;
//		int core = JiveGlobals.getIntProperty(size, DEFAULT_THREAD_MAX_SIZE);
		return new ThreadPoolExecutor(core, core, 60L, TimeUnit.SECONDS,
				new LinkedBlockingQueue<Runnable>(), new NamedThreadFactory(
						threadName), new ThreadPoolExecutor.CallerRunsPolicy());
	}

	public static ScheduledThreadPoolExecutor newScheduledPool(
			String threadName, String size) {
		int core = DEFAULT_THREAD_MAX_SIZE;
//		int core = JiveGlobals.getIntProperty(size, DEFAULT_THREAD_MAX_SIZE);
		return new ScheduledThreadPoolExecutor(core, new NamedThreadFactory(
				threadName), new ThreadPoolExecutor.CallerRunsPolicy());
	}

	public static class NamedThreadFactory implements ThreadFactory {
		private static final AtomicInteger threadPoolNumber = new AtomicInteger(
				1);
		private final ThreadGroup group;
		private final AtomicInteger threadNumber = new AtomicInteger(1);
		private static final String NAME_PATTERN = "%s-%d-thread";
		private final String threadNamePrefix;

		/**
		 * Creates a new {@link NamedThreadFactory} instance
		 * 
		 * @param threadNamePrefix
		 *            the name prefix assigned to each thread created.
		 */
		public NamedThreadFactory(String threadNamePrefix) {
			final SecurityManager s = System.getSecurityManager();
			group = (s != null) ? s.getThreadGroup() : Thread.currentThread()
					.getThreadGroup();
			this.threadNamePrefix = String.format(NAME_PATTERN,
					checkPrefix(threadNamePrefix),
					threadPoolNumber.getAndIncrement());
		}

		private static String checkPrefix(String prefix) {
			return prefix == null || prefix.length() == 0 ? "pool" : prefix;
		}

		/**
		 * Creates a new {@link Thread}
		 * 
		 * @see java.util.concurrent.ThreadFactory#newThread(java.lang.Runnable)
		 */
		public Thread newThread(Runnable r) {
			final Thread t = new Thread(group, r, String.format("%s-%d",
					this.threadNamePrefix, threadNumber.getAndIncrement()), 0);
			t.setDaemon(false);
			t.setPriority(Thread.NORM_PRIORITY);
			return t;
		}
	}
}
