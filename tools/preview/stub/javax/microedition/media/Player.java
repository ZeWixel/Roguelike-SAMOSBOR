package javax.microedition.media;

import javax.microedition.media.control.VolumeControl;

/** Заглушка JSR-135: «проигрыватель» ничего не звучит, но честно живёт. */
public interface Player extends Controllable {
    int UNREALIZED = 100, REALIZED = 200, PREFETCHED = 300,
            STARTED = 400, CLOSED = 0;
    long TIME_UNKNOWN = -1L;

    void realize() throws MediaException;
    void prefetch() throws MediaException;
    void start() throws MediaException;
    void stop() throws MediaException;
    void deallocate();
    void close();
    void setMediaTime(long now) throws MediaException;
    long getMediaTime();
    int getState();
    long getDuration();
    String getContentType();
    void setLoopCount(int count);
    void addPlayerListener(PlayerListener listener);
    void removePlayerListener(PlayerListener listener);
}
