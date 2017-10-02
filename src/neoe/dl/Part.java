package neoe.dl;

import neoe.util.Log;

class Part /*implements Comparable<Part>*/ {
	DLAgent agent;
	long doneLen;
	private PartSave ps;
	long start;
	long totalLen;

	Part(PartSave ps) {
		this.ps = ps;
	}

//	@Override
//	public int compareTo(Part p2) {
//		if (start > p2.start)
//			return 1;
//		if (start == p2.start)
//			return 0;
//		return -1;
//	}

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
				}
			}
		}
	}


	public String toString() {
		return String.format("[%s+%s/%s]", start, doneLen, totalLen);
	}


	public boolean isDone() {
		synchronized (ps) {
			return (doneLen >= totalLen);
		}
	}

	public RealPart dup() {
		RealPart p = new RealPart();
		p.start=this.start;
		p.totalLen=this.totalLen;
		p.doneLen=this.doneLen;
		return p;
	}
}