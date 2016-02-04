package neoe.dl;

import java.util.Map;

public class Source1 {

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

	public String getSpeed(long size) {
		synchronized (this) {
			sum += size;
		}
		long t2 = System.currentTimeMillis();
		long t = t2 - t1;
		t1 = t2;
		if (t == 0)
			return "MAX";		
		return String.format("%,dKB/s avg. %,dKB/s, sum=%,dbytes", size / t, sum / (t2 - t0), sum);

	}

	public String getSpeed() {
		long t2 = System.currentTimeMillis();
		long t = t2 - t0;
		if (t == 0)
			return "MAX";
		return String.format("%s %,dKB/s, %,dbytes", name, sum / t, sum);

	}

}
