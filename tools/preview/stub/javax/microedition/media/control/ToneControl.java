package javax.microedition.media.control;

import javax.microedition.media.Control;

public interface ToneControl extends Control {
    byte[] getSequence();
    void setSequence(byte[] sequence);
}
