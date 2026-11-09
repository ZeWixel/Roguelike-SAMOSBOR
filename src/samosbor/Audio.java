package samosbor;

import java.io.InputStream;
import javax.microedition.media.Manager;
import javax.microedition.media.MediaException;
import javax.microedition.media.Player;
import javax.microedition.media.control.VolumeControl;

/**
 * Звук «Гигахрущёвки» через JSR-135 MMAPI.
 * Фоновая музыка — зацикленные MIDI theme.mid / danger.mid (сирена),
 * 11 коротких MIDI-эффектов. Если MMAPI на телефоне нет, Game ловит
 * NoClassDefFoundError и игра продолжается без звука.
 *
 * lastEvent/lastEventAt нужны браузерному превью: оно дублирует звуки WebAudio.
 */
public final class Audio {

    public static final int SHOT = 0, HIT = 1, KILL = 2, PICKUP = 3,
            DOOR = 4, HURT = 5, SIREN = 6, MENU = 7, STEP = 8,
            DRAW = 9, RELOAD = 10, BREACH = 11;

    private static final String[] FILES = {
        "shot.mid", "hit.mid", "kill.mid", "pickup.mid", "door.mid",
        "hurt.mid", "siren.mid", "menu.mid", "step.mid", "draw.mid",
        "reload.mid", "breach.mid"
    };

    /** Для HTML-превью (озвучка через WebAudio). */
    public static volatile String lastEvent = "";
    public static volatile long lastEventAt = 0;
    public static volatile String lastMusic = "";

    private final Player[] sfx = new Player[FILES.length];
    private Player music;
    private int currentTrack = -1;
    private boolean enabled = true;
    private boolean musicEnabled = true;

    public Audio() {
        for (int i = 0; i < FILES.length; i++) {
            sfx[i] = load("/audio/" + FILES[i]);
        }
    }

    private Player load(String path) {
        InputStream in = null;
        try {
            in = getClass().getResourceAsStream(path);
            if (in == null) return null;
            Player p = Manager.createPlayer(in, "audio/midi");
            p.realize();
            p.prefetch();
            setVolume(p, 88);
            return p;
        } catch (Throwable t) {
            return null;
        } finally {
            if (in != null) {
                try { in.close(); } catch (Exception e) {}
            }
        }
    }

    private static void setVolume(Player p, int level) {
        try {
            VolumeControl v = (VolumeControl) p.getControl("VolumeControl");
            if (v != null) v.setLevel(level);
        } catch (Throwable t) {
        }
    }

    /** Проиграть эффект. */
    public void play(int id) {
        if (!enabled || id < 0 || id >= sfx.length) return;
        lastEvent = FILES[id];
        lastEventAt = System.currentTimeMillis();
        Player p = sfx[id];
        if (p == null) return;
        try {
            if (p.getState() == Player.STARTED) p.stop();
            p.setMediaTime(0);
            p.start();
        } catch (MediaException e) {
        } catch (Throwable t) {
        }
    }

    /** 0 = тема, 1 = сирена (последние 30 сек), -1 = выключить. */
    public void music(int track) {
        if (track == currentTrack) return;
        currentTrack = track;
        stopMusic();
        if (track < 0 || !enabled || !musicEnabled) return;
        String name = track == 1 ? "/audio/danger.mid" : "/audio/theme.mid";
        lastMusic = track == 1 ? "danger" : "theme";
        music = load(name);
        if (music == null) return;
        setVolume(music, 62);
        try {
            music.setLoopCount(-1);
            music.start();
        } catch (MediaException e) {
        } catch (Throwable t) {
        }
    }

    private void stopMusic() {
        if (music == null) return;
        try { music.stop(); } catch (Throwable t) {}
        try { music.close(); } catch (Throwable t) {}
        music = null;
        lastMusic = "";
    }

    public void setEnabled(boolean on) {
        enabled = on;
        musicEnabled = on;
        if (!on) {
            stopMusic();
            currentTrack = -1;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void close() {
        stopMusic();
        for (int i = 0; i < sfx.length; i++) {
            if (sfx[i] != null) {
                try { sfx[i].close(); } catch (Throwable t) {}
                sfx[i] = null;
            }
        }
    }
}
