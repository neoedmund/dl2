package neoe.util;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

import neoe.dl.U;

/**
 * - Temp file to save block data. 1) atomic, crash-safe 2) less file-system
 * overhead still testing
 */
public class TempFile123 {

	public static class Info {
		int loc, lenA, mid, lenB, total, lenC;

		public byte[] toBs() throws IOException {
			Info info = this;
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			DataOutputStream buf = new DataOutputStream(baos);
			buf.writeInt(info.loc);
			buf.writeInt(info.lenA);
			buf.writeInt(info.mid);
			buf.writeInt(info.lenB);
			buf.writeInt(info.total);
			buf.writeInt(info.lenC);
			buf.close();
			return baos.toByteArray();
		}
	}

	private String fn;

	public TempFile123(String fn) {
		this.fn = fn;
	}

	int cntSave;
	int cntExtend;

	/**
	 * 
	 * @param bs
	 * @return loc
	 * @throws IOException
	 */
	public int save(byte[] bs) throws IOException {
		File f = new File(fn);
		if (!f.exists()) {
			initFile(f, bs);
			return 1;
		} else {
			return doSave(f, bs);
		}
	}

	private int doSave(File f, byte[] bs) throws IOException {
		/*-
		 * [ loc(1/2/3) [ lenA mid lenB total lenC] [dataA] [dataB] [dataC(opt)]]
		 */
		RandomAccessFile ra = new RandomAccessFile(f, "rw");
		cntSave++;
		Info info = readInfo(ra);
		int loc = 0;
		int len = bs.length;
		if (info.loc == 1) {
			loc = 2;
			int max = info.total - info.mid;
			if (max >= len) {
				writeLenB(ra, len);
				writeData(ra, info.mid, bs);
				updateLoc(ra, loc);
				return loc;
			} else {
				writeToC(ra, bs, info);
				return 3;
			}
		} else if (info.loc == 2) {
			loc = 1;
			int max = info.mid;
			if (max >= len) {
				writeLenA(ra, len);
				writeData(ra, 0, bs);
				updateLoc(ra, loc);
				return loc;
			} else {
				writeToC(ra, bs, info);
				return 3;
			}
		} else if (info.loc == 3) { // recovery condition, rare
			System.out.println("[w]recovery from C to B");
			updateCToB(ra, info, info.lenC);
			return doSave(f, bs);
		} else {
			U.error("bad loc:" + info.loc);
			return 0;
		}
	}

	private void writeLenA(RandomAccessFile ra, int len) throws IOException {
		ra.seek(4);
		ra.writeInt(len);
	}

	private void writeLenB(RandomAccessFile ra, int len) throws IOException {
		ra.seek(4 * 3);
		ra.writeInt(len);
	}

	private void writeToC(RandomAccessFile ra, byte[] bs, Info info) throws IOException {
		int lenC = Math.max(info.total, (int) (bs.length * 1.2f));
		writeLenC(ra, lenC);
		ra.setLength(infolen + info.total + lenC);
		cntExtend++;
		writeData(ra, info.total, bs);
		ra.seek(0);
		ra.writeInt(3);
		updateCToB(ra, info, lenC);
		Log.log(String.format("[d]ext=%d(%.1f%%)\n", cntExtend, 100f * cntExtend / cntSave));
	}

	private void updateCToB(RandomAccessFile ra, Info info, int lenC) throws IOException {
		info.mid = info.total;
		info.total = info.total + lenC;
		info.lenB = lenC;
		info.loc = 2;
		updateInfo(ra, info);
	}

	private void updateInfo(RandomAccessFile ra, Info info) throws IOException {
		// should be atomic
		ra.seek(0);
		ra.write(info.toBs());
		ra.close(); // updateCToB
		/*- this should atomic
		 * mid <= total
		 * total <= total + lenC
		 * lenB <= lenC
		 */
	}

	private void writeLenC(RandomAccessFile ra, int len) throws IOException {
		ra.seek(4 * 5);
		ra.writeInt(len);

	}

	private void writeData(RandomAccessFile ra, int start, byte[] bs) throws IOException {
		ra.seek(infolen + start);
		ra.write(bs);
	}

	private Info readInfo(RandomAccessFile ra) throws IOException {
		Info i = new Info();
		i.loc = ra.readInt();
		i.lenA = ra.readInt();
		i.mid = ra.readInt();
		i.lenB = ra.readInt();
		i.total = ra.readInt();
		i.lenC = ra.readInt();
		return i;
	}

	private void updateLoc(RandomAccessFile ra, int loc) throws IOException {
		ra.seek(0);
		ra.writeInt(loc);
		ra.close();
	}

	static final int infolen = 4 * 6;

	private void initFile(File f, byte[] bs) throws IOException {
		RandomAccessFile ra = new RandomAccessFile(f, "rw");
		int deflen = Math.max(1000, (int) (bs.length * 1.5));
		int flen = infolen + deflen + deflen;
		ra.writeInt(0);
		ra.writeInt(bs.length);// lenA
		ra.writeInt(deflen);// mid
		ra.writeInt(0);// lenB
		ra.writeInt(deflen * 2);// total
		ra.writeInt(0);// lenC
		ra.write(bs);
		ra.setLength(flen);
		updateLoc(ra, 1);
	}

	public byte[] get() throws IOException {
		File f = new File(fn);
		if (!f.exists()) {
			return null;
		}
		RandomAccessFile ra = new RandomAccessFile(f, "r");
		Info info = readInfo(ra);
		if (info.loc == 1) {
			return getData(ra, infolen, info.lenA);
		} else if (info.loc == 2) {
			return getData(ra, infolen + info.mid, info.lenB);
		} else if (info.loc == 3) {
			return getData(ra, infolen + info.total, info.lenC);
		} else {
			System.out.println("[w]bad loc:" + info.loc);
			return null;
		}
	}

	private byte[] getData(RandomAccessFile ra, int pos, int len) throws IOException {
		ra.seek(pos);
		byte[] bs = new byte[len];
		ra.readFully(bs);
		return bs;
	}

	public static void main(String[] args) {
	}

}
