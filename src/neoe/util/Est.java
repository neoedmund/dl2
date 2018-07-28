package neoe.util;

public class Est {
	private long t1;
	private long start;

	public Est(long start) {
		t1 = System.currentTimeMillis();
		this.start = start;
	}

	public String getInfo(long i, long cnt) {
		long t2 = System.currentTimeMillis();
		long t = (t2 - t1) / 1000;
		float h = ((float) t) / 3600;
		long p = i - start;
		if (p == 0)
			p = 1;
		float h3 = h * (cnt - start) / p;
		float h2 = h * (cnt - i) / p;
		String unit = "H";
		if (h < 1) {
			h *= 60;
			h2 *= 60;
			h3 *= 60;
			unit = "M";
		}
		String s = String.format("=%.1f%s+%.1f%s|%.1f%s %.1f%%", h, unit, h2, unit, h3, unit,
				((float) i / cnt * 100));
		return s;
	}

}
