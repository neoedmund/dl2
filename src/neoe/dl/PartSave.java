package neoe.dl;

import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

import neoe.util.Log;

class PartSave {

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

	long getDone() {
		synchronized (this) {
			long sum = 0;
			for (Part p : parts) {
				sum += p.doneLen;
			}
			if (sum >= blocks) {
				allFinished = true;
			} else {
				allFinished = false;
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
		Log.log(String.format("alloc %s size=%,d", fn, filesize));
		{
			RandomAccessFile f = new RandomAccessFile(fn, "rw");
			f.setLength(filesize);
			f.close();
		}
		Log.log("alloc OK");
	}

	public boolean load(String psFile, long filesize) throws IOException {

		this.filesize = filesize;
		FileInputStream fi;
		DataInputStream in = new DataInputStream(fi = new FileInputStream(psFile));
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
		fi.close();
		lastDone = getDone();
		return true;

	}

	public RealPartSave snapshot() {
		RealPartSave n = new RealPartSave();
		synchronized (this) {
			n.blocks = this.blocks;
			n.fn = this.fn;
			n.fnps = this.fnps;
			n.filesize = this.filesize;
			n.parts = new ArrayList<>();
			long sum = 0;
			for (Part p : this.parts) {
				n.parts.add(p.dup());
				sum += p.doneLen;
			}
			n.sum1 = sum;
			n.sum0 = sum;
			n.sum = sum;
		}
		return n;
	}

}