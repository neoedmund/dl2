package neoe.dl;

import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

import neoe.dl.util.Log;

public class FileWriter {
	private DL2 dl2;
	/** real ps(commited data), dl2.ps is a working ps */
	public RealPartSave ps;

	public FileWriter(DL2 dl2) {
		this.dl2 = dl2;
		new Thread() {
			public void run() {
				Log.log("FileWriter started.");

				while (true) {
					try {
						// Log.log("FileWriter check");
						if (!queue.isEmpty()) {
							// Log.log("[D]FileWriter has data");
							List<Object[]> buf = new ArrayList<>();
							queue.drainTo(buf);
							// Log.log("[D]FileWriter size:" + buf.size());
							writes(buf);
							// Log.log("[D]FileWriter write:" + buf.size());
						} else {
							if (ps != null && ps.allFinished) {
								break;
							}
							if (outError) {
								Log.log("FileWriter exit because outer error");
								break;
							}
							Thread.sleep(1000);
						}
						Thread.sleep(300);
					} catch (Throwable e) {
						e.printStackTrace();
						U.sleep(1000);
					}
				}
				Log.log("FileWriter finished.");
				dl2.done=true;
			}
		}.start();
	}

	protected void writes(List<Object[]> buf) throws Exception {
		// Log.log("FileWriter writing "+size);
		RandomAccessFile f = new RandomAccessFile(dl2.fn, "rw");
		for (Object[] r : buf) {
			if (r == null) {
				U.bug();
				return;
			}
			long pi = (long) r[0];
			byte[] ba = (byte[]) r[1];
			long start = DL2.blockSize * pi;
			// Log.log("writing "+start);
			long len = (long) r[2];
			writeToFile(f, start, len, ba);
			// Log.log("writing p0 ");
			ps.add(pi);
			// Log.log("write "+start);
		}
		f.close();
		ps.save(dl2);// + " t=" + Thread.currentThread().getId());
	}

	private static void writeToFile(RandomAccessFile f, long start, long len, byte[] ba) throws Exception {
		f.seek(start);
		f.write(ba, 0, (int) len);
	}

	// public void flush() throws Exception {
	// while (!ps.allFinished) {
	// Log.log(String.format("wait FileWriter to finish:%s (%s/%s)", queue.size(),
	// ps.getDone(), ps.blocks));
	// U.sleep(300);
	// }
	// }

	public void add(long pi, byte[] ba, long len) throws InterruptedException {
		if (len != ba.length) {
			U.bug();
		}
		queue.put(new Object[] { pi, ba, len });
	}

	private LinkedBlockingQueue<Object[]> queue = new LinkedBlockingQueue<Object[]>(5000);
	public boolean outError = false;

}
