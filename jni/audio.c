#include <jni.h>
#include <opus/include/opus.h>
#include <stdint.h>

jlong Java_com_morlunk_mumbleclient_jni_NativeAudio_opusDecoderCreate(JNIEnv *env, jobject this, jint sampleRate, jint channels) {
	int error;
	OpusDecoder *decoder = opus_decoder_create((opus_int32)sampleRate, (int)channels, &error);
	return (jlong)(intptr_t) decoder;
}

void Java_com_morlunk_mumbleclient_jni_NativeAudio_opusDecoderDestroy(JNIEnv *env, jobject this, jlong decoder) {
	opus_decoder_destroy((OpusDecoder *)(intptr_t)decoder);
}
