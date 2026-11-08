package javax.microedition.media.control;

import javax.microedition.media.Control;

public interface MIDIControl extends Control {
    int shortMidiEvent(int type, int data1, int data2);
    int longMidiEvent(byte[] data, int offset, int length);
}
