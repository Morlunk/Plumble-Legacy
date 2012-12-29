package com.morlunk.mumbleclient.jni;

public class NativeAudio {
	static {
		System.loadLibrary("Plumble");
	}
	
	public static native long opusDecoderCreate(int sampleRate, int channels);
	public static native int opusDecodeFloat(long decoder, byte[] data, int length, float[] pcm, int frameSize, int decodeFec);
	public static native void opusDecoderDestroy(long decoder);
}
