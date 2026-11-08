package javax.microedition.media;

import java.io.InputStream;
import javax.microedition.media.control.VolumeControl;

/** Заглушка JSR-135: читает MIDI-ресурс, чтобы превью проверяло его наличие. */
public final class Manager {

    private Manager() {}

    public static Player createPlayer(InputStream stream, String type) throws MediaException {
        if (stream == null) throw new MediaException("null stream");
        try {
            byte[] buf = new byte[4096];
            while (stream.read(buf) > 0) { /* проглатываем */ }
        } catch (Exception e) {
            throw new MediaException(e.toString());
        }
        return new StubPlayer();
    }

    public static Player createPlayer(String locator) throws MediaException {
        return new StubPlayer();
    }

    public static String[] getSupportedContentTypes(String protocol) {
        return new String[] {"audio/midi"};
    }

    public static String[] getSupportedProtocols(String contentType) {
        return new String[] {"http"};
    }

    public static void playTone(int note, int duration, int volume) throws MediaException {}

    private static final class StubPlayer implements Player {
        private int state = UNREALIZED;
        private final VolumeControl volume = new StubVolume();
        private long mediaTime;
        private int loops = 1;

        public void realize() { state = REALIZED; }
        public void prefetch() { state = PREFETCHED; }
        public void start() { state = STARTED; }
        public void stop() { if (state != CLOSED) state = PREFETCHED; }
        public void deallocate() { if (state != CLOSED) state = REALIZED; }
        public void close() { state = CLOSED; }
        public void setMediaTime(long now) { mediaTime = now; }
        public long getMediaTime() { return mediaTime; }
        public int getState() { return state; }
        public long getDuration() { return TIME_UNKNOWN; }
        public String getContentType() { return "audio/midi"; }
        public void setLoopCount(int count) { loops = count; }
        public void addPlayerListener(PlayerListener l) {}
        public void removePlayerListener(PlayerListener l) {}
        public Control[] getControls() { return new Control[] {volume}; }
        public Control getControl(String t) {
            return "VolumeControl".equals(t) ? volume : null;
        }
    }

    private static final class StubVolume implements VolumeControl {
        private int level = 100;
        private boolean muted;
        public int getLevel() { return level; }
        public int setLevel(int l) { level = l < 0 ? 0 : (l > 100 ? 100 : l); return level; }
        public boolean isMuted() { return muted; }
        public void setMute(boolean m) { muted = m; }
    }
}
