package com.morlunk.mumbleclient.service.audio;

/**
 * Created by andrew on 24/05/13.
 */

import com.morlunk.mumbleclient.jni.Native;
import com.morlunk.mumbleclient.service.MumbleProtocol;

/**
 * Cancels echo using the speex library.
 */
public class EchoCanceller {

    public static final int FILTER_LENGTH = 500; // 500ms, see http://www.speex.org/docs/manual/speex-manual/node7.html#SECTION00740000000000000000

    long mSpeexEchoState = 0;

    public static EchoCanceller initialize() {
        long state = Native.speex_echo_state_init(MumbleProtocol.FRAME_SIZE, FILTER_LENGTH);
        long[] sampleRate = new long[1];
        sampleRate[0] = MumbleProtocol.SAMPLE_RATE;
        Native.speex_echo_ctl(state, Native.SPEEX_ECHO_SET_SAMPLING_RATE, sampleRate);
        return new EchoCanceller(state);
    }

    private EchoCanceller(long state) {
        mSpeexEchoState = state;
    }

    /**
     * Adds a frame of audio from the speakers to speex's echo buffer.
     * Should be called for every frame of audio played.
     */
    public void addPlaybackFrame(short[] play) {
        Native.speex_echo_playback(mSpeexEchoState, play);
    }

    /**
     * (Hopefully) cancels the echo in the passed frame of audio.
     * @param in An audio frame straight from the recording device.
     * @return An echo-less frame.
     */
    public short[] cancelEcho(short[] in) {
        short[] out = new short[MumbleProtocol.FRAME_SIZE];
        Native.speex_echo_capture(mSpeexEchoState, in, out);
        return out;
    }


    public void destroy() {
        if(mSpeexEchoState != 0) {
            Native.speex_echo_state_destroy(mSpeexEchoState);
            mSpeexEchoState = 0;
        }
    }
}
