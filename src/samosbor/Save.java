package samosbor;

import javax.microedition.rms.RecordStore;

/**
 * АВТОСОХРАНЕНИЕ (вместо паролей из оригинала).
 * Два RMS-хранилища:
 *   gh_auto  — текущее состояние (пишется каждые 12 с, на подборах, на паузе);
 *   gh_floor — снимок начала этажа. После смерти gh_floor копируется в gh_auto,
 *              чтобы «Продолжить» не возвращало в уже проигранную ситуацию.
 */
public final class Save {

    private static final String AUTO = "gh_auto";
    private static final String FLOOR = "gh_floor";

    private Save() {}

    public static void writeAuto(byte[] data) { write(AUTO, data); }
    public static byte[] readAuto() { return read(AUTO); }
    public static void writeFloor(byte[] data) { write(FLOOR, data); }
    public static byte[] readFloor() { return read(FLOOR); }

    public static boolean hasAuto() { return read(AUTO) != null; }

    public static void copyFloorToAuto() {
        byte[] b = read(FLOOR);
        if (b != null) write(AUTO, b);
        else clear();
    }

    public static void clear() {
        drop(AUTO);
        drop(FLOOR);
    }

    private static void write(String name, byte[] data) {
        if (data == null) return;
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore(name, true);
            if (rs.getNumRecords() == 0) rs.addRecord(data, 0, data.length);
            else rs.setRecord(1, data, 0, data.length);
        } catch (Throwable t) {
        } finally {
            if (rs != null) try { rs.closeRecordStore(); } catch (Throwable t) {}
        }
    }

    private static byte[] read(String name) {
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore(name, false);
            if (rs.getNumRecords() == 0) return null;
            return rs.getRecord(1);
        } catch (Throwable t) {
            return null;
        } finally {
            if (rs != null) try { rs.closeRecordStore(); } catch (Throwable t) {}
        }
    }

    private static void drop(String name) {
        try {
            RecordStore.deleteRecordStore(name);
        } catch (Throwable t) {
        }
    }
}
