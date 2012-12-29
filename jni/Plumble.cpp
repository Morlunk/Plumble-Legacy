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

jint Java_com_morlunk_mumbleclient_jni_NativeAudio_opusDecodeFloat(JNIEnv *env, jobject object, jlong decoderRef, jbyteArray data, jint len, jfloatArray pcm, jint frame_size, jint decode_fec) {
	OpusDecoder *decoder = (OpusDecoder *)(intptr_t)decoderRef;
	const unsigned char *dataBytes = (const unsigned char*)env->GetByteArrayElements(data, NULL);
	float *pcmFloat = (float*)env->GetFloatArrayElements(pcm, NULL);
	jint result = (jint)opus_decode_float(decoder, dataBytes, len, pcmFloat, frame_size, decode_fec);
	env->ReleaseByteArrayElements(data, (signed char*)dataBytes, NULL);
	env->ReleaseFloatArrayElements(pcm, pcmFloat, NULL);
	return result;
}

#ifdef __cplusplus
}
#endif
