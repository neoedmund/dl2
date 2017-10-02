package neoe.dl;

public class RealPart {
	long doneLen;
	long start;
	long totalLen;

	public boolean isIn(long pi) {
		return start <= pi && start + totalLen > pi;
	}

	// public boolean isDone() {
	// return (doneLen >= totalLen);
	// }
}
