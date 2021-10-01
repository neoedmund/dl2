//neoe(c)
package neoe.dl.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author neoe
 */
public class Log {

	static public String DEFAULT = "dl2";

	final static Map<String, Log> cache = new HashMap<String, Log>();

	public static boolean stdout = false;

	public static boolean debug = true;

	public synchronized static Log getLog(String name) {
		Log log = cache.get(name);
		if (log == null) {
			log = new Log(name, "log-" + name + ".log");
			cache.put(name, log);
		}
		return log;
	}

	private PrintWriter out;
	private SimpleDateFormat time;
	private Date now = new Date();

	private Log(String name, String fn) {
		try {
			File f = new File(fn);
			System.out.println("Log " + name + ":" + f.getAbsolutePath());
			out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(f, true), "utf8"), true);
			time = new SimpleDateFormat("yyMMdd H:m:s");
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	public static void logTo(String name, Object msg) {
		Log.getLog(name).log0(msg);
	}

	public static void logTo(String name, Object msg, Throwable t) {
		Log.getLog(name).log0(msg, t);
	}

	public static void log(Object msg) {
		Log.getLog(DEFAULT).log0(msg);
	}

	public static void log(Object msg, Throwable t) {
		Log.getLog(DEFAULT).log0(msg, t);
	}

	public synchronized void log0(Object o, Throwable t) {
		if (out == null || o == null) {
			return;
		}
		String s0 = o.toString();
		try {
			now.setTime(System.currentTimeMillis());
			StringBuilder sb = new StringBuilder();
			sb.append(time.format(now)).append(" ").append(s0);
			if (t == null)
				sb.append("\r\n");
			else
				sb.append(", Error:\r\n");
			out.write(sb.toString());
			if (t != null) {
				t.printStackTrace(out);
			}
			out.flush();
			int p1 = s0.indexOf('[');
			if (stdout && p1 != 0)
				System.out.print(sb.toString());
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	public synchronized void log0(Object o) {
		log0(o, null);
	}
}
