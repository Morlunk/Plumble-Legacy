package com.morlunk.mumbleclient.jni;

import static org.fusesource.hawtjni.runtime.ArgFlag.*;
import static org.fusesource.hawtjni.runtime.ClassFlag.STRUCT;
import static org.fusesource.hawtjni.runtime.ClassFlag.TYPEDEF;

import org.fusesource.hawtjni.runtime.JniArg;
import org.fusesource.hawtjni.runtime.JniClass;
import org.fusesource.hawtjni.runtime.JniField;
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

    public static final int JITTER_BUFFER_BAD_ARGUMENT = -2;
    public static final int JITTER_BUFFER_GET_AVAILABLE_COUNT = 3;
    public static final int JITTER_BUFFER_GET_MARGIN = 1;
    public static final int JITTER_BUFFER_INCOMPLETE = 2;
    public static final int JITTER_BUFFER_INTERNAL_ERROR = -1;
    public static final int JITTER_BUFFER_MISSING = 1;
    public static final int JITTER_BUFFER_OK = 0;
    public static final int JITTER_BUFFER_SET_MARGIN = 0;

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

    /*
     * Echo cancellation
     */

    @JniMethod(cast = "SpeexEchoState *")
    public final static native long speex_echo_state_init(int frame_size, int filter_length);
    public final static native void speex_echo_state_destroy(@JniArg(cast = "SpeexEchoState *")long st);
    public final static native void speex_echo_cancellation(@JniArg(cast = "SpeexEchoState *")long st, @JniArg(flags = {NO_OUT}) int[] rec, @JniArg(flags = {NO_OUT}) int[] play, @JniArg(flags = {NO_IN}) int[] out);
    public final static native void speex_echo_capture(@JniArg(cast = "SpeexEchoState *")long st, @JniArg(flags = {NO_OUT}) int[] rec, @JniArg(flags = {NO_IN}) int[] out);
    public final static native void speex_echo_ctl(@JniArg(cast = "SpeexEchoState *")long st, int request, @JniArg(cast = "void *") int value);
    public final static native void speex_echo_playback(@JniArg(cast = "SpeexEchoState *")long st, @JniArg(flags = {NO_OUT}) int[] play);

    /*
     * Jitter buffer
     */
    @JniMethod(cast = "JitterBuffer *")
    public final static native long jitter_buffer_init(int tick);
    public final static native void jitter_buffer_reset(@JniArg(cast = "JitterBuffer *")long st);
    public final static native void jitter_buffer_destroy(@JniArg(cast = "JitterBuffer *")long st);
    public final static native void jitter_buffer_put(@JniArg(cast = "JitterBuffer *")long st, @JniArg(flags = {NO_OUT}) JitterBufferPacket packet);
    public final static native int jitter_buffer_get(@JniArg(cast = "JitterBuffer *")long st, JitterBufferPacket packet, int desiredSpan, int[] startOffset);
    public final static native int jitter_buffer_get_pointer_timestamp(@JniArg(cast = "JitterBuffer *")long st);
    public final static native void jitter_buffer_tick(@JniArg(cast = "JitterBuffer *")long st);
    public final static native void jitter_buffer_ctl(@JniArg(cast = "JitterBuffer *")long st, int request, int[] ptr);
    public final static native int jitter_buffer_update_delay(@JniArg(cast = "JitterBuffer *")long st, @JniArg(flags = {NO_OUT}) JitterBufferPacket packet, int[] offset);
}
