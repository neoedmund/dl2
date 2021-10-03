package neoe.dl;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import neoe.dl.util.Est;
import neoe.dl.util.FileUtil;
import neoe.dl.util.Log;
import neoe.dl.util.PyData;

public class DL2 {

	static final int blockSize = 128 * 1024;

	static final int ps_version = 2;

	static final String ver = "9A".toString();

	public boolean console = true;

	Est est;

	public static void main(String[] args) throws Exception {
		Log.log("DL2 " + ver);
		if (args.length == 0) {
			usage();
			return;
		}
		// http://docs.oracle.com/javase/6/docs/technotes/guides/net/http-keepalive.html
		System.setProperty("http.maxConnections", "999");

		Map m;
		if (args.length == 1) {
			String confn = args[0];
			m = (Map) PyData.parseAll(FileUtil.readString(new FileInputStream(confn), null), false);
		} else {
			m = new HashMap();
			List urls = new ArrayList();
			List<String> proxys = new ArrayList();
			int cc = 4;
			for (int i = 0; i < args.length; i++) {
				String s = args[i];

				if ("-u".equals(s)) {
					i++;
					urls.add(args[i]);
				} else if ("-c".equals(s)) {
					i++;
					cc = Integer.parseInt(args[i]);
				} else if ("-p".equals(s)) {
					i++;
					proxys.add(args[i]);
				} else if ("-p1".equals(s)) {
					proxys.add("socks:127.0.0.1:1080");
				} else if ("-f".equals(s)) {
					i++;
					m.put("failcnt", args[i]);
				}
			}
			List urlv = new ArrayList();
			{
				int i = 0;
				for (Object url : urls) {
					List row = new ArrayList();
					row.add("url" + (i++));
					row.add(url);
					urlv.add(row);
				}
			}
			proxys.add("DIRECT");
			List proxyValue = new ArrayList();

			List sourcev = new ArrayList();
			for (String proxy : proxys) {
				{
					Map pm = new HashMap();
					pm.put("name", proxy);
					pm.put("url", proxy);
					proxyValue.add(pm);
				}
				int i = 0;
				for (Object url : urls) {
					List row = new ArrayList();
					row.add(proxy);
					row.add("url" + (i++));
					row.add("head1");
					row.add(cc);
					sourcev.add(row);
				}
			}

			m.put("url", urlv);
			m.put("proxy", proxyValue);
			m.put("source", sourcev);
			m.put("httpHeader", PyData.parseAll(String.format("[[ head1 {\"User-Agent\": \"%s\"}]]", U.DEF_AGENT)));

		}
		new DL2().run(m);
	}

	private static void usage() {
		System.out
				.println("Usage: dl2 <dl2conf> OR dl2 -u <url> -u <url2..> -c <concurrent number>  -p proxy-host:port");
	}

	int agentCnt;
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

	public int run(Map m) throws Exception {
		conf.init(m);
		if (conf.source.isEmpty()) {
			System.err.println("nothing to download, exit");
			return -1;
		}

		filesize = U.checkFileSize(conf.source, this);

		if (filesize <= 0) {
			Log.log("exit because filesize=" + filesize);
			return -2;
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
			est = new Est(done);
		}

		List<Thread> agentThreads = startAgents();
		while (!done) {
			fw.ps.getDone();
			U.sleep(10);
		}

		{ // renanme file
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
		{ // display sum
			long t1 = System.currentTimeMillis();
			long t = t1 - ps.st0;
			if (t == 0)
				Log.log(String.format("total speed:MAX, %,d bytes in %,d sec", ps.sum, t / 1000));
			else
				Log.log(String.format("total speed:%,d KB/s, %,d bytes in %,d sec", ps.sum / t, ps.sum, t / 1000));
			for (DLAgent a : agents) {
				Log.log("|-" + a.src.getSpeed());
			}
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
				if (console) {
					System.exit(0);
				}
			}
		}
		return 0;
	}

	List<DLAgent> agents = new ArrayList<>();

	public boolean cancel = false;

	protected boolean done;

	private List<Thread> startAgents() {
		Log.log("start agents");
		int cnt = 0;
		List<Thread> agentThreads = new ArrayList<>();
		for (final Source1 src : conf.source) {
			for (int i = 0; i < src.concurrent; i++) {
				final DLAgent agent = new DLAgent(ps, src, null, src.name + ":" + i + "/" + src.concurrent);
				agents.add(agent);
				cnt++;
				Thread t = new Thread() {
					public void run() {
						agent.run();
					}
				};
				t.start();
				agentThreads.add(t);
				U.sleep(500);// no sudden access
			}
		}
		this.agentCnt = cnt;
		return agentThreads;
	}

	public void checkCancel() {
		if (cancel) {
			throw new RuntimeException("DL2 cancelled");
		}
	}

	public static Map getSimpleConf(String url0, String destDir) throws Exception {
		Map m = new HashMap();
		List urls = new ArrayList();
		urls.add(url0);
		List<String> proxys = new ArrayList();
		int cc = 4;
		List urlv = new ArrayList();
		{
			int i = 0;
			for (Object url : urls) {
				List row = new ArrayList();
				row.add("url" + (i++));
				row.add(url);
				urlv.add(row);
			}
		}
		proxys.add("DIRECT");
		List proxyValue = new ArrayList();
		List sourcev = new ArrayList();
		for (String proxy : proxys) {
			{
				Map pm = new HashMap();
				pm.put("name", proxy);
				pm.put("url", proxy);
				proxyValue.add(pm);
			}
			int i = 0;
			for (Object url : urls) {
				List row = new ArrayList();
				row.add(proxy);
				row.add("url" + (i++));
				row.add("head1");
				row.add(cc);
				sourcev.add(row);
			}
		}

		m.put("url", urlv);
		m.put("proxy", proxyValue);
		m.put("source", sourcev);
		m.put("httpHeader", PyData.parseAll(String.format("[[ head1 {\"User-Agent\": \"%s\"}]]", U.DEF_AGENT)));
		m.put("destDir", destDir);
		return m;
	}
	public static Map getSimpleConf2(String url0, String destFile) throws Exception {
		Map m = new HashMap();
		List urls = new ArrayList();
		urls.add(url0);
		List<String> proxys = new ArrayList();
		int cc = 4;
		List urlv = new ArrayList();
		{
			int i = 0;
			for (Object url : urls) {
				List row = new ArrayList();
				row.add("url" + (i++));
				row.add(url);
				urlv.add(row);
			}
		}
		proxys.add("DIRECT");
		List proxyValue = new ArrayList();
		List sourcev = new ArrayList();
		for (String proxy : proxys) {
			{
				Map pm = new HashMap();
				pm.put("name", proxy);
				pm.put("url", proxy);
				proxyValue.add(pm);
			}
			int i = 0;
			for (Object url : urls) {
				List row = new ArrayList();
				row.add(proxy);
				row.add("url" + (i++));
				row.add("head1");
				row.add(cc);
				sourcev.add(row);
			}
		}

		m.put("url", urlv);
		m.put("proxy", proxyValue);
		m.put("source", sourcev);
		m.put("httpHeader", PyData.parseAll(String.format("[[ head1 {\"User-Agent\": \"%s\"}]]", U.DEF_AGENT)));
		m.put("destFile", destFile);
		return m;
	}

}
