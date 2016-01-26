package neoe.dl;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Map;

import neoe.util.FileUtil;
import neoe.util.Log;
import neoe.util.PyData;

public class DL2 {

	static final int blockSize = 128 * 1024;

	static final int ps_version = 1;

	static final String ver = "0.1";

	public static void main(String[] args) throws Exception {
		Log.log("DL2 ver " + ver);
		// http://docs.oracle.com/javase/6/docs/technotes/guides/net/http-keepalive.html
		System.setProperty("http.maxConnections", "999");
		new DL2().run(args[0]);
	}

	private int agentCnt;
	int agentDown;
	private long blocks;
	U.Conf conf = new U.Conf();
	private long filesize;
	private String fn;
	U.PartSave ps = new U.PartSave(this);

	long remain;

	public boolean resume;

	private int concurrent;

	private void doDownloadParts() throws IOException {
		calcSize();
		ps.init(fn, concurrent, blocks, filesize);
	}

	private void calcSize() {
		blocks = filesize / blockSize;
		remain = filesize % blockSize;
		if (remain > 0) {
			blocks += 1;
			Log.log(String.format("filesize %d=%d*%d+%d", filesize, blockSize, blocks - 1, remain));
		} else {
			Log.log(String.format("filesize %d=%d*%d", filesize, blockSize, blocks));
		}

		int ac = 0;
		for (Source1 src : conf.source) {
			ac += src.concurrent;
		}
		concurrent = ac;
		ps.blocks = blocks;
		ps.filesize = filesize;
		ps.fn = fn;
		ps.fnps = U.getPsFile(fn);

	}

	private boolean doResumeDownloadParts() throws IOException {
		long fl = new File(fn).length();
		if (filesize != fl) {
			Log.log(String.format("[w]cannot resume, target file size not same [%s expect %s]", fl, filesize));
			return false;
		}
		if (!ps.load(U.getPsFile(fn), filesize)) {
			// load fail
			return false;
		}

		calcSize();
		return true;
	}

	public synchronized void incAgentDown(boolean error) {
		agentDown++;
		if (error)
			Log.log("dead agent count:" + agentDown);
		if (agentDown >= agentCnt) {
			if (!ps.allFinished)
				Log.log("all agents dead! download fail.");
			synchronized (this) {
				this.notifyAll();
			}

		}

	}

	public void run(String confn) throws Exception {
		Map m = (Map) PyData.parseAll(FileUtil.readString(new FileInputStream(confn), null), false);
		conf.init(m);
		if (conf.source.isEmpty()) {
			System.err.println("nothing to download, exit");
			return;
		}
		filesize = U.checkFileSize(conf.source);
		if (filesize <= 0) {
			Log.log("exit because filesize=" + filesize);
			return;
		}
		fn = U.getFileName(conf.source.get(0).url, filesize, this);

		if (resume) {
			if (!doResumeDownloadParts()) {
				fn = fn + "." + U.ts36();
				doDownloadParts();
			} else {
				Log.log("resume");

			}
		} else {
			doDownloadParts();
		}
		synchronized (ps) {
			long done = ps.getDone();
			Log.log(String.format("[D]start %s parts, done: %.1f%%", ps.parts.size(), 100.0f * done / blocks));
		}
		startAgents();
		synchronized (this) {
			this.wait();
		}
		synchronized (ps) {
			long done = ps.getDone();
			Log.log(String.format("[D]program end, %s parts, done: %.1f%%", ps.parts.size(), 100.0f * done / blocks));
			if (done == blocks) {
				ps.deleteFile();
			}
		}
		long t1 = System.currentTimeMillis();
		long t = t1 - ps.st0;
		if (t == 0)
			Log.log(String.format("total speed:MAX, %,dbytes in %,d sec", ps.sum, t / 1000));
		else
			Log.log(String.format("total speed:%,dKB/s, %,dbytes in %,d sec", ps.sum / t, ps.sum, t / 1000));
		for (Source1 src : conf.source) {
			Log.log("|-" + src.getSpeed());
		}
	}

	private void startAgents() {
		Log.log("start agents");
		int cnt = 0;
		for (final Source1 src : conf.source) {
			for (int i = 0; i < src.concurrent; i++) {
				final DLAgent agent = new DLAgent(ps, src, null, src.name + ":" + i + "/" + src.concurrent);
				cnt++;
				new Thread() {
					public void run() {
						agent.run();

					}
				}.start();
			}
		}
		this.agentCnt = cnt;
	}

}
