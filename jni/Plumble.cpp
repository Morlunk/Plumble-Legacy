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
	jbyte *dataBytes = env->GetByteArrayElements(data, NULL);
	jfloat *pcmFloat = env->GetFloatArrayElements(pcm, NULL);
	jint result = (jint)opus_decode_float(decoder, (unsigned char*)dataBytes, (opus_int32)len, pcmFloat, (int)frame_size, (int)decode_fec);
	env->ReleaseByteArrayElements(data, (signed char*)dataBytes, NULL);
	env->ReleaseFloatArrayElements(pcm, pcmFloat, NULL);
	return result;
}

jint Java_com_morlunk_mumbleclient_jni_NativeAudio_opusPacketGetFrames(JNIEnv *env, jobject object, jbyteArray data, jint length) {
	const unsigned char *dataBytes = (const unsigned char*)env->GetByteArrayElements(data, NULL);
	jint result = (jint)opus_packet_get_nb_frames(dataBytes, (opus_int32)length);
	env->ReleaseByteArrayElements(data, (signed char*)dataBytes, NULL);
	return result;
}

jint Java_com_morlunk_mumbleclient_jni_NativeAudio_opusPacketGetSamplesPerFrame(JNIEnv *env, jobject object, jbyteArray data, jint sampleRate) {
	const unsigned char *dataBytes = (const unsigned char*)env->GetByteArrayElements(data, NULL);
	jint result = (jint)opus_packet_get_samples_per_frame(dataBytes, (opus_int32)sampleRate);
	env->ReleaseByteArrayElements(data, (signed char*)dataBytes, NULL);
	return result;
}

jint Java_com_morlunk_mumbleclient_jni_NativeAudio_opusPacketGetChannels(JNIEnv *env, jobject object, jbyteArray data) {
	const unsigned char *dataBytes = (const unsigned char*)env->GetByteArrayElements(data, NULL);
	jint result = (jint)opus_packet_get_nb_channels(dataBytes);
	env->ReleaseByteArrayElements(data, (signed char*)dataBytes, NULL);
	return result;
}

jlong Java_com_morlunk_mumbleclient_jni_NativeAudio_opusEncoderCreate(JNIEnv *env, jobject object, jint sampleRate, jint channels, jint application) {
	int error;
	OpusEncoder *encoder = opus_encoder_create((opus_int32)sampleRate, (int)channels, (int)application, &error);
	return (jlong)(intptr_t)encoder;
}

void Java_com_morlunk_mumbleclient_jni_NativeAudio_opusEncoderDestroy(JNIEnv *env, jobject object, jlong encoder) {
	opus_encoder_destroy((OpusEncoder *)(intptr_t)encoder);
}

jint Java_com_morlunk_mumbleclient_jni_NativeAudio_opusEncode(JNIEnv *env, jobject object, jlong encoderRef, jshortArray pcm, jint frameSize, jbyteArray data, jint maxBytes) {
	OpusEncoder *encoder = (OpusEncoder *)(intptr_t)encoderRef;
	jshort *pcmShort = env->GetShortArrayElements(pcm, NULL);
	jbyte *dataBytes = env->GetByteArrayElements(data, NULL);
	jint result = (jint)opus_encode(encoder, (opus_int16*)pcmShort, (int)frameSize, (unsigned char*)dataBytes, (int)maxBytes);
	env->ReleaseByteArrayElements(data, (signed char*)dataBytes, NULL);
	env->ReleaseShortArrayElements(pcm, pcmShort, NULL);
	return result;
}

jint Java_com_morlunk_mumbleclient_jni_NativeAudio_opusEncoderCtl(JNIEnv *env, jobject object, jlong encoderRef, jint request, jint value) {
	OpusEncoder *encoder = (OpusEncoder *)(intptr_t)encoderRef;
	return (jint)opus_encoder_ctl(encoder, request, value);
}

#ifdef __cplusplus
}
#endif
