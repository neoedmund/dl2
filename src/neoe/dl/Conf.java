package neoe.dl;

import java.io.File;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import neoe.dl.util.Log;

class Conf {

	Map<String, Map> headers = new HashMap();

	Map<String, Proxy1> proxy = new HashMap();

	List<Source1> source = new ArrayList();

	Map<String, String> urls = new HashMap();

	int failCnt;
	String workingDir = ".";

	public void init(Map m) {
		Log.log("[d]load config:" + m);
		{
			String dir = (String) m.get("destDir");
			if (dir != null) {
				workingDir = dir;
				new File(dir).mkdirs();
				if (!new File(dir).isDirectory()) {
					U.error("fail to create dir:" + dir);
				}
			}
		}
		loadProxy((List) m.get("proxy"));
		loadHeader((List) m.get("httpHeader"));
		loadUrl((List) m.get("url"));
		loadSource((List) m.get("source"));
		failCnt = getInt(m.get("failcnt"), 3);
	}

	private int getInt(Object o, int def) {
		if (o == null)
			return def;
		try {
			return Integer.parseInt(o.toString());
		} catch (Exception e) {
			return def;
		}
	}

	private void loadHeader(List list) {
		for (Object o : list) {
			List l = (List) o;
			headers.put(l.get(0).toString(), (Map) l.get(1));
		}
	}

	Map<String, String> authm = new HashMap();

	private void loadProxy(List list) {
		for (Object o : list) {
			Map m = (Map) o;
			Proxy1 p = new Proxy1();
			proxy.put(m.get("name").toString(), p);
			p.url = m.get("url").toString();
			p.user = (String) m.get("user");// can be null
			authm.put(p.url, p.user);
		}
		Authenticator.setDefault(new Authenticator() {
			@Override
			protected PasswordAuthentication getPasswordAuthentication() {
				if (getRequestorType() == RequestorType.PROXY) {
					String host = getRequestingHost() + ":" + getRequestingPort();

					String user = authm.get(host);
					if (user != null) {
						Log.log("[auth]found, host=" + host);

						String[] ss = user.split(":");
						return new PasswordAuthentication(ss[0], ss[1].toCharArray());
					} else {
						Log.log("[auth]not found, host=" + host);
					}
				}
				return null;
			}
		});
	}

	private void loadSource(List list) {
		for (Object o : list) {
			List line = (List) o;
			Source1 src = new Source1();
			String p = line.get(0).toString();

			if ("DIRECT".equals(p)) {

			} else {
				src.proxy = proxy.get(p);
				if (src.proxy == null) {
					throw new RuntimeException("conf:proxy name not found:" + p);
				}
			}
			{
				String u = line.get(1).toString();
				src.url = urls.get(u);

				if (src.url == null) {
					throw new RuntimeException("conf:url name not found:" + u);
				}
				src.name = p + "@" + u;
			}

			{
				String s = line.get(2).toString();
				src.header = headers.get(s);
				if (src.header == null) {
					throw new RuntimeException("conf:header name not found:" + s);
				}
			}

			src.concurrent = Integer.parseInt(line.get(3).toString());
			source.add(src);
		}
	}

	private void loadUrl(List list) {
		for (Object o : list) {
			List line = (List) o;
			urls.put(line.get(0).toString(), line.get(1).toString());
		}

	}
}