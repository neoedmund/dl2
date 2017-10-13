package neoe.dl;

import java.io.IOException;

import neoe.util.Log;

public class DLAgent {
	private static final boolean DONNT_SEP_SAME_SOURCE = false;
	int failed;
	boolean live = true;
	private String name;
	private Part part;
	PartSave ps;

	Source1 src;

	public DLAgent(PartSave ps, Source1 src, Part p, String name) {
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
			if (part.doneLen > part.totalLen) {
				say(String.format("[dl]drop, part %s has been reassigned to others", pi));
				return;
			}
			long expect = part.start + part.doneLen;
			if (expect != pi) {
				say(String.format("[dl]drop, part %s expected %s", pi, expect));
				return;
			}

			// say("OK:" + pi + "/" + ps.blocks);
			if (dl.ba.length != len) {
				say(String.format("[dl]drop, down len not right, got %s expected %s", dl.ba.length, len));
				return;
			}
			ps.dl2.fw.add(pi, dl.ba, len);
			part.incDoneLen(len);
			String sp = src.getSpeed(len);
			if (ps.dl2.conf.source.size() > 1) {
				// to see speed from different source
				say(sp);
			}
			// ps.save(name + " " + );
		}

	}

	public void run() {
		say("start " + part);
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
						if (failed >= ps.dl2.conf.failCnt) {
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

	private Part seperateOthers() throws IOException {
		// Log.log("seperateOthers:"+ps.parts);
		synchronized (ps) {
			for (Part p : ps.parts) {
				if (p == part || p.isDone())
					continue;

				if (p.agent == null) {
					p.agent = this;
					say("[sep]take over " + p);
					return p;
				} else {
					// say("[d]part agent=" + p.agent);
				}
			}
			for (Part p : ps.parts) {
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
							Part np = new Part(ps);
							np.start = p.start + p.totalLen;
							np.totalLen = right;
							np.agent = this;
							ps.parts.add(np);
							say(String.format("[sep][start %s,len %s,done %s, mid %s]", p.start, oldTotal, p.doneLen,
									np.start));
							// ps.save(name);
							return np;
						}
					}
				}
			}
			return null;
		}
	}

}
