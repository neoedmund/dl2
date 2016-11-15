package neoe.dl;

import java.io.IOException;

import neoe.util.Log;

public class DLAgent {
	private static final boolean DONNT_SEP_SAME_SOURCE = false;
	private static final int MAX_FAIL = 3;
	int failed;
	boolean live = true;
	private String name;
	private U.Part part;
	U.PartSave ps;

	Source1 src;

	public DLAgent(U.PartSave ps, Source1 src, U.Part p, String name) {
		this.ps = ps;
		this.src = src;
		this.part = p;
		this.name = name;

	}

	private void downloadPart(long pi) throws Exception {
		// say("[dl]download part:" + pi + "/" + ps.blocks);
		if (pi >= ps.blocks) {
			throw new RuntimeException("bug detected(1)");
		}

		long start = DL2.blockSize * pi;
		long len = DL2.blockSize;

		if (ps.dl2.remain != 0 && pi == ps.blocks - 1) {
			len = ps.dl2.remain;// ps.filesize % DL2.blockSize;
		}
		Downloader dl = new Downloader(name);
		dl.download(src, start, len, true);
		// write
		synchronized (ps) {
			long expect = part.start + part.doneLen;
			if (part.doneLen > part.totalLen) {
				say(String.format("[dl]part %s should be reassigned to others", pi));
				return;
			}
			if (expect != pi) {
				say(String.format("[dl]drop dl part %s expected %s", pi, expect));
				return;
			}
			// say("OK:" + pi + "/" + ps.blocks);
			U.writeToFile(ps.fn, start, len, dl.ba);
			part.incDoneLen(len);
			ps.save(name + " " + src.getSpeed(len));
		}

	}

	public void run() {
		say("start");
		try {
			while (true) { // on total
				while (true) { // on part
					if (part == null)
						break;
					long pi = part.getNext();
					if (pi < 0) {
						break;
					}
					try {
						downloadPart(pi);
					} catch (Exception ex) {
						// ex.printStackTrace();
						if (ex instanceof DL2Exception)
							Log.log(name + "[dl]download exception " + ex.getMessage());
						else
							Log.log(name + "[dl]download exception", ex);
						failed++;
						if (failed >= MAX_FAIL) {
							live = false;
							Log.log(String.format("agent %s down because failed too many times %d", name, failed));
							ps.dl2.incAgentDown(true);
							return;
						}
						U.sleep(3000);
					}
				}

				// done my work, find others to do
				if (ps.allFinished)
					break;
				part = seperateOthers();
				if (part == null) {
					U.sleep(1000);
				}
			}
		} catch (Throwable ex) {
			live = false;
			ex.printStackTrace();
			Log.log("[err]agent down:" + name, ex);
			ps.dl2.incAgentDown(true);
			return;

		}
		ps.dl2.incAgentDown(false);
		say("end");
	}

	private void say(String s) {
		Log.log(String.format("%s:%s", name, s));
	}

	private U.Part seperateOthers() throws IOException {
		synchronized (ps) {
			for (U.Part p : ps.parts) {
				if (p == part || p.isDone())
					continue;

				if (p.agent == null) {
					p.agent = this;
					say("[sep]take over " + p);
					return p;
				}
			}
			for (U.Part p : ps.parts) {
				if (p == part || p.isDone())
					continue;

				if (!p.agent.live) {
					if (p.doneLen < p.totalLen) {
						// reassign to me because origin agent is dead
						say("[sep]take over from dead agent " + p.agent.name + ":" + p);
						p.agent = this;
						return p;
					}
				} else {
					if (part != null && p.agent != null && p.agent.src == src && DONNT_SEP_SAME_SOURCE) {
						// dont sep same, for what reason?
						continue;
					} else {
						long remainLen = p.totalLen - p.doneLen;
						if (remainLen <= 1) {
							say("[sep]take over from slow agent " + p.agent.name + ":" + p);
							p.agent = this;
							return p;
						} else {
							// sep
							long left = remainLen / 2;
							long right = remainLen - left;
							long oldTotal = p.totalLen;
							p.totalLen = p.doneLen + left;
							U.Part np = new U.Part(ps);
							np.start = p.start + p.totalLen;
							np.totalLen = right;
							np.agent = this;
							ps.parts.add(np);
							say(String.format("[sep][start %s,len %s,done %s, mid %s]", p.start, oldTotal, p.doneLen,
									np.start));
							ps.save(name);
							return np;
						}
					}
				}
			}
			return null;
		}
	}

}
