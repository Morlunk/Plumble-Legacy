package com.morlunk.mumbleclient.jni;

import static org.fusesource.hawtjni.runtime.ArgFlag.NO_IN;
import static org.fusesource.hawtjni.runtime.ArgFlag.NO_OUT;
import static org.fusesource.hawtjni.runtime.ClassFlag.STRUCT;
import static org.fusesource.hawtjni.runtime.ClassFlag.TYPEDEF;

import org.fusesource.hawtjni.runtime.JniArg;
import org.fusesource.hawtjni.runtime.JniClass;
import org.fusesource.hawtjni.runtime.JniMethod;


@JniClass
public class Native {
	static {
		System.loadLibrary("NativeAudio");
	}

	@JniClass(flags = {STRUCT, TYPEDEF})
	public static class JitterBufferPacket {
		public byte[] data;
		public int len;
		public int timestamp;
		public int span;
		public short sequence;
		public int user_data;
	}

	@JniMethod(accessor = "wrap_celt_mode_create", cast = "CELTMode *")
	public final static native long celt_mode_create(int Fs, int frame_size);
	public final static native void celt_mode_destroy(@JniArg(cast = "CELTMode *") long mode);

	@JniMethod(accessor = "wrap_celt_encoder_create", cast = "CELTEncoder *")
	public final static native long celt_encoder_create(@JniArg(cast = "CELTMode *") long mode, int channels);
	public final static native void celt_encoder_destroy(@JniArg(cast = "CELTEncoder *") long st);
	public final static native void celt_encoder_ctl(@JniArg(cast = "CELTEncoder *") long st, int request, int value);
	@JniMethod(accessor = "wrap_celt_encode")
	public final static native int celt_encode(@JniArg(cast = "CELTEncoder *") long st, @JniArg(flags = {NO_OUT}) short[] pcm, @JniArg(cast = "unsigned char *", flags = {NO_IN}) byte[] compressed, int nbCompressedBytes);

	@JniMethod(accessor = "wrap_celt_decoder_create", cast = "CELTDecoder *")
	public final static native long celt_decoder_create(@JniArg(cast = "CELTMode *") long mode, int channels);
	public final static native void celt_decoder_destroy(@JniArg(cast = "CELTDecoder *") long st);
	@JniMethod(accessor = "wrap_celt_decode")
	public final static native int celt_decode(@JniArg(cast = "CELTDecoder *") long st, @JniArg(cast = "unsigned char *", flags = {NO_OUT}) byte[] data, int len, @JniArg(flags = {NO_IN}) short[] pcm);
	@JniMethod(accessor = "wrap_celt_decode_float")
	public final static native int celt_decode_float(@JniArg(cast = "CELTDecoder *") long st, @JniArg(cast = "unsigned char *", flags = {NO_OUT}) byte[] data, int len, @JniArg(flags = {NO_IN}) float[] pcm);

	@JniMethod(accessor = "wrap_speex_resampler_init", cast = "SpeexResamplerState *")
	public final static native long speex_resampler_init(long nb_channels, long in_rate, long out_rate, int quality);
	public final static native void speex_resampler_destroy(@JniArg(cast = "SpeexResamplerState *") long st);
	public final static native int speex_resampler_process_int(@JniArg(cast = "SpeexResamplerState *") long st, int channel_index, @JniArg(flags = {NO_OUT}) short[] in, int[] in_len, @JniArg(flags = {NO_IN}) short[] out, int[] out_len);
}
