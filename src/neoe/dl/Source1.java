package neoe.dl;

import java.util.Map;

public class Source1 implements Cloneable {

	public int concurrent;
	public Map header;
	public String name;
	public Proxy1 proxy;
	private long sum;
	private long t0, t1;
	public String url;

	public Source1() {
		t0 = t1 = System.currentTimeMillis();
	}

	public String getSpeed(long size, boolean dump) {
		synchronized (this) {
			sum += size;
		}
		if (!dump)
			return null;
		long t2 = System.currentTimeMillis();
		long t = t2 - t1;
		t1 = t2;
		if (t == 0)
			return "MAX";
		return String.format("avg %,d KB/s cur %,d KB/s", sum / (t2 - t0), size / t);
	}

	public String getSpeed() {
		long t2 = System.currentTimeMillis();
		long t = t2 - t0;
		if (t == 0)
			return "MAX";
		return String.format("%s %,dKB/s, %,d bytes", url, sum / t, sum);

	}

	public Source1 clone() {
		try {
			return (Source1) super.clone();
		} catch (CloneNotSupportedException e) {
			e.printStackTrace();
			return null;
		}
	}
}
