package neoe.dl;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

import neoe.util.Log;
import neoe.util.TempFile123;

public class RealPartSave {
	public boolean allFinished;
	long blocks;
	// DL2 dl2;
	long filesize;
	String fn;
	String fnps;
	long lastDone;
	List<RealPart> parts;
	long remain;
	long st0;
	long st1;
	public long sum;
	long sum0, sum1;
	TempFile123 tempfile;

	public RealPartSave() {
		this.st0 = this.st1 = System.currentTimeMillis();
	}

	public void deleteFile() {
		new File(fnps).delete();
	}

	long getDone() {
		long sum = 0;
		for (RealPart p : parts) {
			sum += p.doneLen;
		}
		if (sum >= blocks) {
			allFinished = true;
		}
		return sum;
	}

	void save(DL2 dl2) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		DataOutputStream out = new DataOutputStream(baos);
		out.writeInt(DL2.ps_version);
		out.writeInt(DL2.blockSize);
		out.writeLong(filesize);
		out.writeLong(parts.size());
		long sum = 0;
		for (RealPart p : parts) {
			out.writeLong(p.start);
			out.writeLong(p.totalLen);
			out.writeLong(p.doneLen);
			sum += p.doneLen;
		}
		out.close();
		if (tempfile == null) {
			tempfile = new TempFile123(fnps);
		}
		tempfile.save(baos.toByteArray());
		{
			long now = System.currentTimeMillis();
			long speed = 0, speed2 = 0;
			long t1 = now - st0;
			if (t1 != 0)
				speed = (sum - sum0) * DL2.blockSize / t1;
			long t2 = now - st1;
			if (t2 != 0)
				speed2 = (sum - sum1) * DL2.blockSize / t2;
			sum1 = sum;
			st1 = now;
			System.out.print(String.format("[%d]%d/%d %s %dKB/s %dKB/s    \r", dl2.agentCnt - dl2.agentDown, sum,
					blocks, dl2.est.getInfo(sum, blocks), speed, speed2));

		}
	}

	public void add(long pi) {
		int partIndex = getPartIndex(pi, 0, parts.size() - 1);
		if (partIndex < 0) {
			U.bug();
		}
		RealPart pt = parts.get(partIndex);
		if (pt.start + pt.doneLen == pi) {
			pt.doneLen++;

		} else {
			RealPart np = new RealPart();
			np.start = pi;
			np.totalLen = pt.totalLen - (pi - pt.start);
			if (np.totalLen <= 0) {
				U.bug();
			}

			np.doneLen = 1;
			pt.totalLen = pi - pt.start;
			if (pt.totalLen <= 0) {
				U.bug();
			}
			parts.add(partIndex + 1, np);
		}
		inc();
	}

	private void inc() {
		sum++;
		if (sum >= blocks) {
			long sum2 = getDone();
			if (sum2 != sum) {
				Log.log(String.format("bug:sum from %d set to %s", sum, sum2));
				sum = sum2;
			}
			if (sum >= blocks) {
				Log.log("write finished");
				allFinished = true;
			}
		}
	}

	/** 二分法查找 */
	private int getPartIndex(long pi, int i, int j) {
		if (i > j)
			return -1;
		if (i == j) {
			return parts.get(i).isIn(pi) ? i : -1;
		}
		if (isIn(i, pi))
			return i;
		if (isIn(j, pi))
			return j;

		if (j - i == 1) {
			return -1;
		}
		int k = (i + j) / 2;
		if (k == i)
			k++;
		if (isIn(k, pi))
			return k;
		{
			RealPart a = parts.get(k);
			if (pi < a.start) {
				return getPartIndex(pi, i + 1, k - 1);
			}
			if (pi >= a.start + a.doneLen) {
				return getPartIndex(pi, k + 1, j - 1);
			}
		}
		return -1;
	}

	private boolean isIn(int i, long pi) {
		return parts.get(i).isIn(pi);
	}

}
