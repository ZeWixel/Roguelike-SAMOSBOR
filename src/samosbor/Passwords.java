package samosbor;

/**
 * Пароль из четырёх символов — как в Insmouse no Yakata (там тоже короткие
 * слова вместо сохранений). Кодирует seed пробега, номер этапа и этаж.
 */
public final class Passwords {

    public static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int BASE = 36;
    private static final int MAX = BASE * BASE * BASE * BASE; // 1679616

    private Passwords() {}

    public static String encode(int runSeed, int stage, int floorNumber) {
        int seed10 = runSeed & 1023;
        int st = stage & 15;
        int fd = (floorNumber - 540) & 31;
        int code = (seed10 << 9) | (st << 5) | fd;
        int checksum = code % 3;
        int value = code * 3 + checksum;
        if (value >= MAX) value %= MAX;
        char[] out = new char[4];
        for (int i = 3; i >= 0; i--) {
            out[i] = ALPHABET.charAt(value % BASE);
            value /= BASE;
        }
        return new String(out);
    }

    /** out[0]=runSeed, out[1]=stage, out[2]=floorNumber; false если пароль битый. */
    public static boolean decode(String s, int[] out) {
        if (s == null || s.length() != 4) return false;
        int value = 0;
        for (int i = 0; i < 4; i++) {
            int d = ALPHABET.indexOf(s.charAt(i));
            if (d < 0) d = ALPHABET.indexOf(Character.toUpperCase(s.charAt(i)));
            if (d < 0) return false;
            value = value * BASE + d;
        }
        int checksum = value % 3;
        int code = value / 3;
        if (code % 3 != checksum) return false;
        int seed10 = (code >> 9) & 1023;
        int st = (code >> 5) & 15;
        int fd = code & 31;
        if (st > 12 || fd > 31) return false;
        out[0] = 0x1a2b000 | seed10;
        out[1] = st;
        out[2] = 540 + fd;
        return true;
    }
}
