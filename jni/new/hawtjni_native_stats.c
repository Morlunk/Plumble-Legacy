
#include "hawtjni.h"
#include "hawtjni_native_stats.h"

#ifdef NATIVE_STATS

int Native_nativeFunctionCount = 19;
int Native_nativeFunctionCallCount[19];
char * Native_nativeFunctionNames[] = {
	"celt_1decode",
	"celt_1decode_1float",
	"celt_1decoder_1create",
	"celt_1decoder_1destroy",
	"celt_1encode",
	"celt_1encoder_1create",
	"celt_1encoder_1ctl",
	"celt_1encoder_1destroy",
	"celt_1mode_1create",
	"celt_1mode_1destroy",
	"speex_1echo_1cancellation",
	"speex_1echo_1capture",
	"speex_1echo_1ctl",
	"speex_1echo_1playback",
	"speex_1echo_1state_1destroy",
	"speex_1echo_1state_1init",
	"speex_1resampler_1destroy",
	"speex_1resampler_1init",
	"speex_1resampler_1process_1int",
};

#define STATS_NATIVE(func) Java_org_fusesource_hawtjni_runtime_NativeStats_##func

JNIEXPORT jint JNICALL STATS_NATIVE(Native_1GetFunctionCount)
	(JNIEnv *env, jclass that)
{
	return Native_nativeFunctionCount;
}

JNIEXPORT jstring JNICALL STATS_NATIVE(Native_1GetFunctionName)
	(JNIEnv *env, jclass that, jint index)
{
	return (*env)->NewStringUTF(env, Native_nativeFunctionNames[index]);
}

JNIEXPORT jint JNICALL STATS_NATIVE(Native_1GetFunctionCallCount)
	(JNIEnv *env, jclass that, jint index)
{
	return Native_nativeFunctionCallCount[index];
}

#endif
