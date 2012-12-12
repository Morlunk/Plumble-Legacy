package com.morlunk.mumbleclient.jni;

public class NativeAudio {
	
	static {
		System.loadLibrary("plumble_native");
	}
	
	public static native long opusDecoderCreate(int sampleRate, int channels);
}
