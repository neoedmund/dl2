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

	public String enc = "UTF8";

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

	private Source1 src;

	public Downloader(String name) {
		this.name = name;
	}

	public void download(Source1 src, long start, long len, boolean readContent) throws Exception {
		this.src = src;
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
		Log.log(String.format("Maybe no support for ranged download, %s, respHeader=%s", reqHeader.get("Range"),
				respHeader));
		return null;
	}

	public String getPage() throws Exception {
		if (ba == null)
			return "NA";
		return new String(ba, enc);
	}

	int redirect = 0;

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
				 Log.log(String.format("[D]connect via proxy %s %s", url, proxy));
				conn = u.openConnection(proxy);
			} else {
//				 Log.log(String.format("[D]connect"));
				conn = u.openConnection();
			}
			conn.setConnectTimeout(9000);
			// set headers
			for (Object o : reqHeader.keySet()) {
				conn.setRequestProperty((String) o, reqHeader.get(o).toString());
			}
			boolean error = false;
			Exception ex1 = null;
			try {
				respHeader = conn.getHeaderFields();
				{
					String loc = getStr((List) respHeader.get("Location"));
					if (loc != null && !loc.isEmpty()) {
						redirect++;
						if (redirect > 10) {
							throw new RuntimeException("too many redirect");
						}
						Log.log(String.format("source %s redirect(%s) to %s", src.name, redirect, loc));
						src.url = loc;
						url = loc;
						run();
						return;
					}
				}
				 Log.log(String.format("[DD %s|%s]respHeader=%s", name,
				 reqHeader.get("Range"), respHeader));
				if (readContent) {
					ByteArrayOutputStream baos = new ByteArrayOutputStream();
					FileUtil.copy(conn.getInputStream(), baos);
					ba = baos.toByteArray();
					int len1 = ba.length;
					{// gzip enc
						Object encoding = respHeader.get("Content-Encoding"); // java.util.Collections$UnmodifiableRandomAccessList
						Object te = respHeader.get("Transfer-Encoding");
//						 Log.log("encoding="+encoding);
						if (encoding != null && encoding.toString().toLowerCase().indexOf("gzip") >= 0) {
							// gzipped
							GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(ba));
							ByteArrayOutputStream baos2 = new ByteArrayOutputStream();
							FileUtil.copy(gzip, baos2);
							ba = baos2.toByteArray();
							int len2 = ba.length;
//							Log.log(String.format("[D]extract gzip %d bytes -> %d bytes", len1, len2));
						}

					}

//					 Log.log(String.format("[D]downloaded %s(%d bytes)", url,
//					 ba.length));
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
//				ex1.printStackTrace();
				throw new DL2Exception("download fail via proxy:" + proxy + " by:" + ex1);
			} else {
				return;
			}
		}
	}

	// private void say(String s) {
	// Log.log(name + ":" + s);
	// }

	private static String getStr(List list) {
		if (list == null || list.size() <= 0)
			return null;
		return (String) list.get(0);
	}

	public void savePage(String path) throws Exception {
		if (ba == null) {
			Log.log("[W]no content for url[" + url + "] to " + path);
			return;
		}
		FileUtil.save(ba, path);
		Log.log(String.format("[D]save %d bytes for url[%s] to %s", ba.length, url, path));
	}

	public void setConfig(Source1 src) {
		this.src = src;
		reqHeader = new HashMap();

		// header
		if (src.header != null) {
			reqHeader.putAll(src.header);
		}

		// proxy
		if (src.proxy != null) {
			useProxy = true;
			String[] ss = src.proxy.url.split(":");
			if (ss[0].equals("socks")) {
				proxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(ss[1], Integer.parseInt(ss[2])));
			} else {
				proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(ss[0], Integer.parseInt(ss[1])));
			}
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
