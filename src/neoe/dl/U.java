package neoe.dl;

import java.io.File;
import java.util.List;

import neoe.util.Log;

public class U {

	public static final String DEF_AGENT = "Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:73.0) Gecko/20100101 Firefox/73.0"
			.toString();

	interface Func {
		Object func(Object o) throws Exception;
	}

	static long checkFileSize(List<Source1> source) throws Exception {
		Object o = threadAny(source, new Func() {

			@Override
			public Object func(Object o) throws Exception {
				Source1 src = ((Source1) o).clone();
				Downloader dl = new Downloader(src.name);
				dl.download(src, 0, 1, false);// get 1 bytes
				// System.out.println("content:"+new String(dl.ba));
				return dl.getFileLength();
			}
		});
		if (o == null) {
			throw new RuntimeException("cannot get file size from sources");
		}
		return (long) o;
	}

	public static final String DOWNLOADING = ".dling";

	static String getFileName(String s, long filesize, DL2 dl2) {
		String fn = null;
		int p = s.lastIndexOf("/");
		if (p >= 0) {
			s = s.substring(p + 1);
			int p2 = s.indexOf("?");
			if (p2 >= 0) {
				s = s.substring(0, p2);
			}
			if (!s.isEmpty()) {
				fn = s;
			}
		}
		if (fn == null) {
			fn = "dl_" + Long.toString(System.currentTimeMillis(), 36);
			Log.log("[W]cannot get filename from url use:" + fn);
		}
		if (new File(fn).exists()) {
			throw new RuntimeException("file already exists:" + fn);
		}
		{
			fn = fn + DOWNLOADING;
		}
		while (true) {
			if (new File(fn).exists()) {
				String fnps = U.getPsFile(fn);
				if (new File(fnps).exists()) {
					Log.log("find part file to resume:" + fn);
					dl2.resume = true;
					return fn;
				} else {
					// retry
					fn = fn + "_" + Long.toString(System.currentTimeMillis(), 36);
				}
			} else {
				Log.log("download to " + fn);
				return fn;
			}
		}
	}

	public static String getPsFile(String fn) {
		return fn + ".dl2";
	}

	public static void sleep(int ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
		}
	}

	public static Object threadAny(List data, final U.Func func) throws InterruptedException {
		final int cnt = data.size();
		Thread[] ts = new Thread[cnt];
		final Object[] ret = new Object[1];
		int i = 0;
		final int[] finished = new int[1];
		final Thread current = Thread.currentThread();
		Log.log("[threadAny]start cnt=" + cnt);
		for (final Object o : data) {
			final int i2 = i;
			ts[i] = new Thread() {
				public void run() {
					try {
						Object v = func.func(o);
						synchronized (ret) {
							if (v != null) {
								if (ret[0] == null) {
									ret[0] = v;
									Log.log("[threadAny]notify from " + i2 + "=" + v);
									synchronized (current) {
										current.notifyAll();
									}
								} else {
									Log.log("[threadAny]ignore ret from " + i2 + "=" + v);
								}
							}
						}
					} catch (Throwable e) {
						e.printStackTrace();
						Log.log("[e]" + e);
					} finally {
						synchronized (finished) {
							finished[0]++;
							if (finished[0] >= cnt) {
								synchronized (current) {
									current.notifyAll();
								}
							}
						}
					}
				}
			};
			ts[i].start();
			i++;
		}
		synchronized (current) {
			current.wait();
		}

		Log.log("[threadAny]ret=" + ret[0]);
		i = 0;
		for (Thread t : ts) {
			if (t.isAlive()) {
				t.interrupt();
				Log.log("[threadAny]interrupt " + i);
			}
			i++;
		}
		return ret[0];

	}

	public static String ts36() {
		return Long.toString(System.currentTimeMillis(), 36);
	}

	public static void error(String s) {
		throw new RuntimeException(s);
	}

	public static void bug() {
		new RuntimeException("bug?").printStackTrace();
		System.exit(1);
	}

}
