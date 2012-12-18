#include <jni.h>
#include <opus/include/opus.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

jlong Java_com_morlunk_mumbleclient_jni_NativeAudio_opusDecoderCreate(JNIEnv *env, jobject object, jint sampleRate, jint channels) {
	int error;
	OpusDecoder *decoder = opus_decoder_create((opus_int32)sampleRate, (int)channels, &error);
	return (jlong)(intptr_t) decoder;
}

void Java_com_morlunk_mumbleclient_jni_NativeAudio_opusDecoderDestroy(JNIEnv *env, jobject object, jlong decoder) {
	opus_decoder_destroy((OpusDecoder *)(intptr_t)decoder);
}

#ifdef __cplusplus
}
#endif
