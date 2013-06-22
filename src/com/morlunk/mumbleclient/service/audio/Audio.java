package com.morlunk.mumbleclient.service.audio;

import com.morlunk.mumbleclient.service.MumbleService;

/**
 * Created by andrew on 10/06/13.
 */
public class Audio {

    private MumbleService mService;

    private AudioInput mInput;
    private AudioOutput mOutput;

    public Audio(MumbleService service) {
        mService = service;
    }

    public void start() {
        mInput.startRecording();
    }

    public void stop() {

    }

}
