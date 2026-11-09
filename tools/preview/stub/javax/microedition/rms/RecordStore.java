package javax.microedition.rms;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Hashtable;

/**
 * Заглушка RMS для JDK-превью: те же вызовы, что и на телефоне, но записи
 * лежат в файлах во временном каталоге (между запусками превью сохраняются).
 */
public final class RecordStore {

    private static final Hashtable OPEN = new Hashtable();

    private final String name;
    private final Hashtable records = new Hashtable();
    private int nextId = 1;
    private boolean open = true;

    private RecordStore(String name) { this.name = name; }

    private static File file(String name) {
        String safe = name.replaceAll("[^A-Za-z0-9_-]", "_");
        return new File(System.getProperty("java.io.tmpdir"), "gh_rms_" + safe + ".bin");
    }

    public static RecordStore openRecordStore(String name, boolean createIfNecessary)
            throws RecordStoreException {
        RecordStore rs = (RecordStore) OPEN.get(name);
        if (rs != null && rs.open) return rs;
        File f = file(name);
        if (!f.exists() && !createIfNecessary) {
            throw new RecordStoreNotFoundException(name);
        }
        rs = new RecordStore(name);
        if (f.exists()) rs.load(f);
        OPEN.put(name, rs);
        return rs;
    }

    public static void deleteRecordStore(String name) throws RecordStoreException {
        RecordStore rs = (RecordStore) OPEN.get(name);
        if (rs != null) { rs.open = false; OPEN.remove(name); }
        File f = file(name);
        if (f.exists() && !f.delete()) {
            throw new RecordStoreException("cannot delete " + name);
        }
    }

    public static String[] listRecordStores() {
        File dir = new File(System.getProperty("java.io.tmpdir"));
        String[] all = dir.list();
        if (all == null) return new String[0];
        int n = 0;
        for (int i = 0; i < all.length; i++) {
            if (all[i].startsWith("gh_rms_") && all[i].endsWith(".bin")) n++;
        }
        String[] out = new String[n];
        int j = 0;
        for (int i = 0; i < all.length; i++) {
            if (all[i].startsWith("gh_rms_") && all[i].endsWith(".bin")) {
                out[j++] = all[i].substring(7, all[i].length() - 4);
            }
        }
        return out;
    }

    private void load(File f) throws RecordStoreException {
        DataInputStream in = null;
        try {
            in = new DataInputStream(new FileInputStream(f));
            int count = in.readInt();
            for (int i = 0; i < count; i++) {
                int id = in.readInt();
                int len = in.readInt();
                byte[] b = new byte[len];
                in.readFully(b);
                records.put(new Integer(id), b);
                if (id >= nextId) nextId = id + 1;
            }
        } catch (Exception e) {
            throw new RecordStoreException(e.toString());
        } finally {
            if (in != null) try { in.close(); } catch (Exception e) {}
        }
    }

    private void save() throws RecordStoreException {
        DataOutputStream out = null;
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            out = new DataOutputStream(bos);
            out.writeInt(records.size());
            for (int id = 1; id < nextId; id++) {
                byte[] b = (byte[]) records.get(new Integer(id));
                if (b == null) continue;
                out.writeInt(id);
                out.writeInt(b.length);
                out.write(b, 0, b.length);
            }
            out.flush();
            FileOutputStream fos = new FileOutputStream(file(name));
            fos.write(bos.toByteArray());
            fos.close();
        } catch (Exception e) {
            throw new RecordStoreException(e.toString());
        } finally {
            if (out != null) try { out.close(); } catch (Exception e) {}
        }
    }

    private void check() throws RecordStoreNotOpenException {
        if (!open) throw new RecordStoreNotOpenException(name);
    }

    public int getNumRecords() throws RecordStoreNotOpenException {
        check();
        return records.size();
    }

    public int getNextRecordID() throws RecordStoreNotOpenException {
        check();
        return nextId;
    }

    public int addRecord(byte[] data, int offset, int numBytes)
            throws RecordStoreException {
        check();
        if (data == null) data = new byte[0];
        int id = nextId++;
        records.put(new Integer(id), copy(data, offset, numBytes));
        save();
        return id;
    }

    public void setRecord(int recordId, byte[] data, int offset, int numBytes)
            throws RecordStoreException {
        check();
        if (!records.containsKey(new Integer(recordId))) {
            throw new InvalidRecordIDException("id " + recordId);
        }
        records.put(new Integer(recordId), copy(data, offset, numBytes));
        save();
    }

    public void deleteRecord(int recordId) throws RecordStoreException {
        check();
        if (records.remove(new Integer(recordId)) == null) {
            throw new InvalidRecordIDException("id " + recordId);
        }
        save();
    }

    public byte[] getRecord(int recordId) throws RecordStoreException {
        check();
        byte[] b = (byte[]) records.get(new Integer(recordId));
        if (b == null) throw new InvalidRecordIDException("id " + recordId);
        return (byte[]) b.clone();
    }

    public int getRecordSize(int recordId) throws RecordStoreException {
        return getRecord(recordId).length;
    }

    public void closeRecordStore() throws RecordStoreNotOpenException {
        check();
        open = false;
        OPEN.remove(name);
    }

    private static byte[] copy(byte[] src, int off, int len) {
        byte[] b = new byte[len];
        for (int i = 0; i < len; i++) b[i] = src[off + i];
        return b;
    }
}
