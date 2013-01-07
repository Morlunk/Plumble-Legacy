package com.morlunk.mumbleclient.jni;

/**
 * A way to interact with the Opus codec through JNI.
 * @see Plumble.cpp
 * @author morlunk
 */
public class NativeAudio {
	static {
		System.loadLibrary("Plumble");
	}
	
	// Constants
	public static final int OPUS_OK = 0;
	public static final int OPUS_APPLICATION_VOIP = 2048;
	public static final int OPUS_SET_BITRATE_REQUEST = 4002;
	public static final int OPUS_SET_VBR_REQUEST = 4006;
	
	// Decoder
	public static native long opusDecoderCreate(int sampleRate, int channels);
	public static native int opusDecodeFloat(long decoder, byte[] data, int length, float[] pcm, int frameSize, int decodeFec);
	public static native void opusDecoderDestroy(long decoder);
	// Encoder
	public static native long opusEncoderCreate(int sampleRate, int channels, int application);
	public static native int opusEncoderCtl(long encoder, int request, int value);
	public static native int opusEncode(long encoder, short[] pcm, int frameSize, byte[] data, int maxBytes);
	public static native void opusEncoderDestroy(long encoder);
	// General
	public static native int opusPacketGetFrames(byte[] data, int length);
	public static native int opusPacketGetSamplesPerFrame(byte[] data, int sampleRate);
	public static native int opusPacketGetChannels(byte[] data);
}
