package neoe.dl;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import neoe.util.Log;

public class U {

	static class Conf {

		Map<String, Map> headers = new HashMap();

		Map<String, Proxy1> proxy = new HashMap();

		List<Source1> source = new ArrayList();

		Map<String, String> urls = new HashMap();

		int failCnt;

		public void init(Map m) {
			Log.log("load config:" + m);
			loadProxy((List) m.get("proxy"));
			loadHeader((List) m.get("httpHeader"));
			loadUrl((List) m.get("url"));
			loadSource((List) m.get("source"));
			failCnt = getInt(m.get("failcnt"), 3);
		}

		private int getInt(Object o, int def) {
			if (o == null)
				return def;
			try {
				return Integer.parseInt(o.toString());
			} catch (Exception e) {
				return def;
			}
		}

		private void loadHeader(List list) {
			for (Object o : list) {
				List l = (List) o;
				headers.put(l.get(0).toString(), (Map) l.get(1));
			}
		}

		Map<String, String> authm = new HashMap();

		private void loadProxy(List list) {
			for (Object o : list) {
				Map m = (Map) o;
				Proxy1 p = new Proxy1();
				proxy.put(m.get("name").toString(), p);
				p.url = m.get("url").toString();
				p.user = (String) m.get("user");// can be null
				authm.put(p.url, p.user);
			}
			Authenticator.setDefault(new Authenticator() {
				@Override
				protected PasswordAuthentication getPasswordAuthentication() {
					if (getRequestorType() == RequestorType.PROXY) {
						String host = getRequestingHost() + ":" + getRequestingPort();

						String user = authm.get(host);
						if (user != null) {
							Log.log("[auth]found, host=" + host);

							String[] ss = user.split(":");
							return new PasswordAuthentication(ss[0], ss[1].toCharArray());
						} else {
							Log.log("[auth]not found, host=" + host);
						}
					}
					return null;
				}
			});
		}

		private void loadSource(List list) {
			for (Object o : list) {
				List line = (List) o;
				Source1 src = new Source1();
				String p = line.get(0).toString();

				if ("DIRECT".equals(p)) {

				} else {
					src.proxy = proxy.get(p);
					if (src.proxy == null) {
						throw new RuntimeException("conf:proxy name not found:" + p);
					}
				}
				{
					String u = line.get(1).toString();
					src.url = urls.get(u);

					if (src.url == null) {
						throw new RuntimeException("conf:url name not found:" + u);
					}
					src.name = p + "@" + u;
				}

				{
					String s = line.get(2).toString();
					src.header = headers.get(s);
					if (src.header == null) {
						throw new RuntimeException("conf:header name not found:" + s);
					}
				}

				src.concurrent = Integer.parseInt(line.get(3).toString());
				source.add(src);
			}
		}

		private void loadUrl(List list) {
			for (Object o : list) {
				List line = (List) o;
				urls.put(line.get(0).toString(), line.get(1).toString());
			}

		}
	}

	interface Func {
		Object func(Object o) throws Exception;
	}

	static class Part implements Comparable<Part> {
		DLAgent agent;
		long doneLen;
		private U.PartSave ps;
		long start;
		long totalLen;

		Part(U.PartSave ps) {
			this.ps = ps;
		}

		@Override
		public int compareTo(Part p2) {
			if (start > p2.start)
				return 1;
			if (start == p2.start)
				return 0;
			return -1;
		}

		public long getNext() {
			synchronized (ps) {
				if (doneLen < totalLen)
					return start + doneLen;
				return -1;
			}
		}

		private String getSpeed(long done) {
			long inc = done - ps.lastDone;
			long t1 = System.currentTimeMillis();
			long t = t1 - ps.st1;
			ps.st1 = t1;
			ps.lastDone = done;
			if (t == 0)
				return "MAX";
			return String.format("%,d", inc * DL2.blockSize / t);
		}

		public synchronized void incDoneLen(long len) {
			synchronized (ps) {
				doneLen++;
				ps.sum += len;
				if (doneLen >= totalLen) {
					long done = ps.getDone();
					Log.log(String.format("progress %d/%d(%.1f%%) %sKB/s", done, ps.blocks, 100.0f * done / ps.blocks,
							getSpeed(done)));
					if (done == ps.blocks) {
						Log.log("download finished:" + ps.fn);
						ps.allFinished = true;
						synchronized (ps.dl2) {
							ps.dl2.notifyAll();
						}

					}
				}
			}
		}

		public boolean isDone() {
			synchronized (ps) {
				return (doneLen >= totalLen);
			}
		}

		public String toString() {
			return String.format("[%s+%s/%s]", start, doneLen, totalLen);
		}
	}

	static class PartSave {

		public boolean allFinished;
		long blocks;
		public DL2 dl2;
		long filesize;
		String fn;
		String fnps;
		long lastDone;
		List<Part> parts;
		long remain;
		long st0;
		long st1;
		public long sum;

		PartSave(DL2 dl2) {
			this.dl2 = dl2;
			this.st0 = this.st1 = System.currentTimeMillis();
		}

		public void deleteFile() {
			new File(fnps).delete();
		}

		long getDone() {
			synchronized (this) {
				long sum = 0;
				for (Part p : parts) {
					sum += p.doneLen;
				}
				return sum;
			}
		}

		public void init(String fn, int concurrent, long blocks, long filesize) throws IOException {
			Log.log("init parts " + concurrent);

			long unitsize = blocks / concurrent;
			long remain = blocks % concurrent;

			parts = new ArrayList<Part>();
			long s1 = 0;
			for (int i = 0; i < concurrent; i++) {
				Part p = new Part(this);
				parts.add(p);
				p.start = s1;
				if (i == concurrent - 1) {
					p.totalLen = unitsize + remain;
				} else {
					p.totalLen = unitsize;
				}
				Log.log(String.format("source part %d/%d [%d-%d/%d(%d)]", i, concurrent, p.start, p.start + p.totalLen,
						blocks, p.totalLen));
				s1 += unitsize;
			}
			save("init");
			Log.log(String.format("alloc %s size=%,d", fn, filesize));
			RandomAccessFile f = new RandomAccessFile(fn, "rw");
			f.setLength(filesize);
			f.close();
			Log.log("alloc OK");
		}

		public boolean load(String psFile, long filesize) throws IOException {
			synchronized (this) {
				this.filesize = filesize;
				DataInputStream in = new DataInputStream(new FileInputStream(psFile));
				{
					int i = in.readInt();
					if (i != DL2.ps_version) {

						Log.log(String.format("cannot resume, ps_versoin not same(%s expect %s)", i, DL2.ps_version));
						return false;
					}
					i = in.readInt();
					if (i != DL2.blockSize) {
						Log.log(String.format("cannot resume, blocksize not same(%s expect %s)", i, DL2.blockSize));
						return false;
					}
				}
				long fs = in.readLong();
				if (fs != filesize) {
					Log.log(String.format("cannot resume, filesize not same(%s expect %s)", fs, filesize));
					return false;
				}
				long cnt = in.readLong();
				parts = new ArrayList();
				for (long i = 0; i < cnt; i++) {
					Part p = new Part(this);
					p.start = in.readLong();
					p.totalLen = in.readLong();
					p.doneLen = in.readLong();
					parts.add(p);
				}
				in.close();

				return true;
			}

		}

		void save(String callerName) throws IOException {
			synchronized (this) {
				String tmpf = fnps + "." + ts36();
				DataOutputStream out = new DataOutputStream(new FileOutputStream(tmpf));
				out.writeInt(DL2.ps_version);
				out.writeInt(DL2.blockSize);
				out.writeLong(filesize);
				Collections.sort(parts);
				out.writeLong(parts.size());
				long sum = 0;
				for (Part p : parts) {
					out.writeLong(p.start);
					out.writeLong(p.totalLen);
					out.writeLong(p.doneLen);
					sum += p.doneLen;
				}
				out.close();
				File f1 = new File(fnps);
				File f2 = new File(tmpf);
				f1.delete();
				f2.renameTo(f1);
				{
					long speed = 0;
					long t1 = System.currentTimeMillis() - st0;
					if (t1 != 0)
						speed = this.sum / t1;
					Log.log(String.format("parts %d %d/%d (%.1f%%) %,d KB/s by %s", parts.size(), sum, blocks,
							100.0f * sum / blocks, speed, callerName));
				}
			}
		}

	}

	static long checkFileSize(List<Source1> source) throws Exception {
		Object o = threadAny(source, new Func() {

			@Override
			public Object func(Object o) throws Exception {
				Source1 src = (Source1) o;
				Downloader dl = new Downloader(src.name);
				dl.download(src, 0, 1, false);// get 1 bytes
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
					} catch (Exception e) {
						e.printStackTrace();
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

	public synchronized static void writeToFile(String fn, long start, long len, byte[] ba) throws Exception {
		// Log.log(String.format("[d]write %s,%s,%s", start, len, ba.length));
		RandomAccessFile f = new RandomAccessFile(fn, "rw");
		f.seek(start);
		f.write(ba, 0, (int) len);
		f.close();
	}

}
