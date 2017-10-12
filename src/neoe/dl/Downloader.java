package neoe.dl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import neoe.util.FileUtil;
import neoe.util.Log;

/** */
public class Downloader {

	public static final byte[] emptyBA = new byte[0];

	private static final int MAX_RETRY = 5;

	public byte[] ba;

	String enc = "UTF8";

	private long len;

	private String name;

	Proxy proxy;
	public boolean readContent = true;
	Map reqHeader;
	public Map respHeader;
	int retry;
	private long start;
	String url;
	private boolean usePart;
	boolean useProxy;

	public Downloader(String name) {
		this.name = name;
	}

	public void download(Source1 src, long start, long len, boolean readContent) throws Exception {

		setConfig(src);
		setPart(start, len);
		this.readContent = readContent;
		run();
	}

	public Object getFileLength() {
		Log.log(String.format("[DD %s]respHeader=%s", reqHeader.get("Range"), respHeader));
		List s = (List) respHeader.get("Content-Range");
		if (s != null) {
			String s1 = s.get(0).toString();
			int p1 = s1.indexOf("/");
			if (p1 >= 0)
				return Long.parseLong(s1.substring(p1 + 1));
		}
		return null;
	}

	public String getPage() throws Exception {
		if (ba == null)
			return "NA";
		return new String(ba, enc);
	}

	public void run() throws Exception {
		ba = emptyBA;
		retry = 0;
		while (true) {
			retry += 1;
			// safeguard
			if (retry > MAX_RETRY)
				return;

			URL u = new URL(url);
			URLConnection conn;
			if (useProxy) {
				// Log.log(String.format("[D]connect via proxy", url, proxy));
				conn = u.openConnection(proxy);
			} else {
				// Log.log(String.format("[D]connect"));
				conn = u.openConnection();
			}
			conn.setConnectTimeout(3000);
			// set headers
			for (Object o : reqHeader.keySet()) {
				conn.setRequestProperty((String) o, reqHeader.get(o).toString());
			}
			boolean error = false;
			Exception ex1 = null;
			try {
				respHeader = conn.getHeaderFields();
				// Log.log(String.format("[DD %s|%s]respHeader=%s", name,
				// reqHeader.get("Range"), respHeader));
				if (readContent) {
					ByteArrayOutputStream baos = new ByteArrayOutputStream();
					FileUtil.copy(conn.getInputStream(), baos);
					ba = baos.toByteArray();
					int len1 = ba.length;
					{// gzip enc
						Object encoding = respHeader.get("Content-Encoding"); // java.util.Collections$UnmodifiableRandomAccessList
						Object te = respHeader.get("Transfer-Encoding");
						// Log.log("encoding="+encoding);
						if (encoding != null && encoding.toString().toLowerCase().indexOf("gzip") >= 0) {
							// gzipped
							GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(ba));
							ByteArrayOutputStream baos2 = new ByteArrayOutputStream();
							FileUtil.copy(gzip, baos2);
							ba = baos2.toByteArray();
							int len2 = ba.length;
							Log.log(String.format("[D]extract gzip %d bytes -> %d bytes", len1, len2));
						}

					}

					// Log.log(String.format("[D]downloaded %s(%d bytes)", url,
					// ba.length));
				}
			} catch (Exception ex) {
				error = true;
				ex1 = ex;
			}
			if (error) {
				Log.log("net warn:" + ex1);
				String errorString = "" + ex1;
				if (errorString.indexOf("java.io.FileNotFoundException") >= 0) {
					throw new DL2Exception("should be 404, skip");
				}
				if (errorString.indexOf("connect timed out") >= 0) {
					throw new DL2Exception("connect timed out");
				}
				throw new RuntimeException("download fail via proxy:" + proxy, ex1);
			} else {
				return;
			}
		}
	}

	// private void say(String s) {
	// Log.log(name + ":" + s);
	// }

	public void savePage(String path) throws Exception {
		if (ba == null) {
			Log.log("[W]no content for url[" + url + "] to " + path);
			return;
		}
		FileUtil.save(ba, path);
		Log.log(String.format("[D]save %d bytes for url[%s] to %s", ba.length, url, path));
	}

	public void setConfig(Source1 src) {

		reqHeader = new HashMap();

		// header
		if (src.header != null) {
			reqHeader.putAll(src.header);
		}

		// proxy
		if (src.proxy != null) {
			useProxy = true;
			String[] ss = src.proxy.url.split(":");
			proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(ss[0], Integer.parseInt(ss[1])));
			// if (src.proxy.user != null) {
			// reqHeader.put("Proxy-Authorization", "Basic " +
			// Base64.encodeBytes(src.proxy.user.getBytes()));
			// }
		}

		if (enc == null)
			enc = "UTF8";

		url = src.url;

		// if (useProxy) {
		// Log.log(String.format("[D]setup %s via %s", url, proxy));
		// } else {
		// Log.log(String.format("[D]setup %s", url));
		// }
	}

	private void setPart(long start, long len) {
		this.start = start;
		this.len = len;
		this.usePart = true;
		reqHeader.put("Range", String.format("bytes=%d-%d", start, start + len - 1));
	}
}
