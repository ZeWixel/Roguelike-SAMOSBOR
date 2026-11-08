package samosbor;

/**
 * Детерминированный LCG. Один и тот же seed => один и тот же этаж,
 * набор монстров и предметов (это нужно для паролей и для рогалика).
 * java.util.Random в CLDC-1.0 не гарантирован, поэтому свой.
 */
public final class Rng {

    private long state;

    public Rng(long seed) {
        state = seed & 0xffffffffL;
        if (state == 0) state = 0x9e3779b9L;
        next();
    }

    public void setSeed(long seed) {
        state = seed & 0xffffffffL;
        if (state == 0) state = 0x9e3779b9L;
        next();
    }

    public int next() {
        state = (state * 1103515245L + 12345L) & 0x7fffffffL;
        return (int) state;
    }

    /** 0 .. bound-1 */
    public int nextInt(int bound) {
        if (bound <= 1) return 0;
        return (next() >>> 4) % bound;
    }

    public int range(int lo, int hi) {
        return lo + nextInt(hi - lo + 1);
    }

    public boolean chance(int percent) {
        return nextInt(100) < percent;
    }
}
