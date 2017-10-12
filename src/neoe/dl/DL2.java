package neoe.dl;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import neoe.util.FileUtil;
import neoe.util.Log;
import neoe.util.PyData;

public class DL2 {

	static final int blockSize = 128 * 1024;

	static final int ps_version = 2;

	static final String ver = "10h12b".toString();

	public static void main(String[] args) throws Exception {
		Log.log("DL2 " + ver);
		// http://docs.oracle.com/javase/6/docs/technotes/guides/net/http-keepalive.html
		System.setProperty("http.maxConnections", "999");
		Map m;
		if (args.length == 1) {
			String confn = args[0];
			m = (Map) PyData.parseAll(FileUtil.readString(new FileInputStream(confn), null), false);
		} else {
			if ("-u".equals(args[0])) {
				m = new HashMap();
				m.put("url", PyData.parseAll(String.format("[[url1 \"%s\" ]]", args[1])));
				m.put("proxy", Collections.EMPTY_LIST);
				m.put("source", PyData.parseAll("[[DIRECT url1 head1 4]]"));
				m.put("httpHeader", PyData.parseAll(String.format("[[ head1 {\"user-agent\": \"%s\"}]]", U.DEF_AGENT)));
			} else {
				usage();
				return;
			}
		}
		new DL2().run(m);
	}

	private static void usage() {
		System.out.println("Usage: dl2 <dl2conf> OR dl2 -u <url>");

	}

	private int agentCnt;
	int agentDown;
	private long blocks;
	Conf conf = new Conf();
	private long filesize;
	String fn;
	PartSave ps = new PartSave(this);
	FileWriter fw;

	long remain;

	public boolean resume;

	private int concurrent;

	private void doDownloadInit() throws IOException {
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
			Log.log("load fail");
			return false;
		}
		calcSize();
		Log.log(String.format("done %d/%d", ps.getDone(), ps.blocks));
		return true;
	}

	public synchronized void incAgentDown(boolean error) {
		agentDown++;
		if (error)
			Log.log("dead agent count:" + agentDown);
		if (agentDown >= agentCnt) {
			if (!ps.allFinished) {
				Log.log("all agents dead! download fail.");
				fw.outError = true;
			}
		}

	}

	public void run(Map m) throws Exception {
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
				doDownloadInit();
			} else {
				Log.log("resume");
			}
		} else {
			doDownloadInit();
		}
		fw = new FileWriter(this);
		fw.ps = ps.snapshot();
		{
			long done = ps.getDone();
			Log.log(String.format("Start %s parts, done: %.1f%%", ps.parts.size(), 100.0f * done / blocks));
		}

		List<Thread> agentThreads = startAgents();
		synchronized (this) {
			this.wait();
		}

		{
			RealPartSave ps = fw.ps;
			long done = ps.getDone();
			Log.log(String.format("Program end, %s parts, done: %d/%d(%.1f%%)", ps.parts.size(), done, blocks,
					100.0f * done / blocks));
			if (done == blocks) {
				ps.deleteFile();
				{
					if (fn.endsWith(U.DOWNLOADING)) {
						String fn2 = fn.substring(0, fn.length() - U.DOWNLOADING.length());
						new File(fn).renameTo(new File(fn2));
						Log.log("renamed to " + fn2);
					}
				}
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
		{// stop slow agents
			int cnt = 0;
			for (Thread at : agentThreads) {
				if (at.isAlive()) {
					at.interrupt();
					cnt++;
				}
			}
			if (cnt > 0) {
				Log.log(String.format("interrupt %s slow agents", cnt));
				U.sleep(1000);
			}
		}
	}

	private List<Thread> startAgents() {
		Log.log("start agents");
		int cnt = 0;
		List<Thread> agentThreads = new ArrayList<>();
		for (final Source1 src : conf.source) {
			for (int i = 0; i < src.concurrent; i++) {
				final DLAgent agent = new DLAgent(ps, src, null, src.name + ":" + i + "/" + src.concurrent);
				cnt++;
				Thread t = new Thread() {
					public void run() {
						agent.run();
					}
				};
				t.start();
				agentThreads.add(t);
			}
		}
		this.agentCnt = cnt;
		return agentThreads;
	}

}
