package neoe.dl;

import java.io.RandomAccessFile;
import java.util.concurrent.ConcurrentLinkedQueue;

import neoe.util.Log;

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
						int size = queue.size();
						if (size > 0) {
							writes(size);
						} else {
							if (ps != null && ps.allFinished) {
								break;
							}
							// Log.log("FileWriter no data");
							Thread.sleep(1000);
						}
						Thread.sleep(100);
					} catch (Throwable e) {
						e.printStackTrace();
						U.sleep(1000);
					}
				}
				Log.log("FileWriter finished.");
			}
		}.start();
	}

	protected void writes(int size) throws Exception {
		// Log.log("FileWriter writing "+size);
		RandomAccessFile f = new RandomAccessFile(dl2.fn, "rw");
		for (int i = 0; i < size; i++) {
			Object[] r = (Object[]) queue.poll();
			if (r == null) {
				U.bug();
				return;
			}
			long pi = (long) r[0];
			byte[] ba = (byte[]) r[1];
			long start = DL2.blockSize * pi;
			long len = DL2.blockSize;
			writeToFile(f, start, len, ba);
			ps.add(pi);
		}
		f.close();
		ps.save("FileWriter " + size);
	}

	private static void writeToFile(RandomAccessFile f, long start, long len, byte[] ba) throws Exception {
		f.seek(start);
		f.write(ba, 0, (int) len);
	}

	public void flush() throws Exception {
		while (queue.size() > 0) {
			U.sleep(300);
			Log.log("wait FileWriter to finish:" + queue.size());
		}
	}

	public void add(long pi, byte[] ba) {
		queue.add(new Object[] { pi, ba });
	}

	private ConcurrentLinkedQueue<Object[]> queue = new ConcurrentLinkedQueue<Object[]>();

}
