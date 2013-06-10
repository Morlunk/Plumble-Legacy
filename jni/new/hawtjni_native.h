#ifndef NATIVE_H_
#define NATIVE_H_

#include "celt.h"
#include <stdint.h>
#include <stdlib.h>
#include <jni.h>

typedef struct SpeexEchoState SpeexEchoState;
typedef struct SpeexResamplerState SpeexResamplerState;
extern SpeexResamplerState *speex_resampler_init(unsigned int nb_channels, unsigned int in_rate, unsigned int out_rate, int quality, int *err);


/** Generic adaptive jitter buffer state */
struct JitterBuffer_;

/** Generic adaptive jitter buffer state */
typedef struct JitterBuffer_ JitterBuffer;

/** Definition of an incoming packet */
typedef struct _JitterBufferPacket JitterBufferPacket;

/** Definition of an incoming packet */
struct _JitterBufferPacket {
   char        *data;       /**< Data bytes contained in the packet */
   unsigned int len;        /**< Length of the packet in bytes */
   unsigned int timestamp;  /**< Timestamp for the packet */
   unsigned int span;       /**< Time covered by the packet (same units as timestamp) */
   unsigned short sequence;   /**< RTP Sequence number if available (0 otherwise) */
   unsigned int user_data;  /**< Put whatever data you like here (it's ignored by the jitter buffer) */
};

extern JitterBuffer *jitter_buffer_init(int step_size);
extern void jitter_buffer_reset(JitterBuffer *jitter);
extern void jitter_buffer_destroy(JitterBuffer *jitter);
extern void jitter_buffer_put(JitterBuffer *jitter, const JitterBufferPacket *packet);
extern int jitter_buffer_get(JitterBuffer *jitter, JitterBufferPacket *packet, int desired_span, int *start_offset);
extern int jitter_buffer_get_another(JitterBuffer *jitter, JitterBufferPacket *packet);
extern int jitter_buffer_get_pointer_timestamp(JitterBuffer *jitter);
extern void jitter_buffer_tick(JitterBuffer *jitter);
extern void jitter_buffer_remaining_span(JitterBuffer *jitter, unsigned int rem);
extern int jitter_buffer_ctl(JitterBuffer *jitter, int request, void *ptr);
extern int jitter_buffer_update_delay(JitterBuffer *jitter, JitterBufferPacket *packet, int *start_offset);

extern SpeexEchoState *speex_echo_state_init (int frame_size, int filter_length);
extern void speex_echo_state_destroy (SpeexEchoState *st);
extern void speex_echo_cancellation (SpeexEchoState *st, const unsigned int *rec, const unsigned int *play, unsigned int *out);
extern void speex_echo_cancel (SpeexEchoState *st, const unsigned int *rec, const unsigned int *play, unsigned int *out, unsigned int *Yout);
extern void speex_echo_capture (SpeexEchoState *st, const unsigned int *rec, unsigned int *out);
extern void speex_echo_playback (SpeexEchoState *st, const unsigned int *play);
extern void speex_echo_state_reset (SpeexEchoState *st);
extern int speex_echo_ctl (SpeexEchoState *st, int request, void *ptr);

static CELTMode *wrap_celt_mode_create(int Fs, int frame_size) {
	return celt_mode_create(Fs, frame_size, NULL);
}

static CELTEncoder *wrap_celt_encoder_create(const CELTMode *mode, int channels) {
	return celt_encoder_create(mode, channels, NULL);
}

static int wrap_celt_encode(CELTEncoder *st, jshort *pcm, unsigned char *compressed, int nbCompressedBytes) {
	return celt_encode(st, pcm, NULL, compressed, nbCompressedBytes);
}

static CELTDecoder *wrap_celt_decoder_create(const CELTMode *mode, int channels) {
	return celt_decoder_create(mode, channels, NULL);
}

static int wrap_celt_decode(CELTDecoder *st, unsigned char *data, int len, short *pcm) {
	int i, res;
//	unsigned char *tmp_data = (unsigned char *)calloc(len, sizeof(unsigned char));

//	for (i = 0; i < len; ++i) {
//		tmp_data[i] = (unsigned char)data[i];
//	}

	res = celt_decode(st, data, len, pcm);
//	free(tmp_data);
	return res;
}

static int wrap_celt_decode_float(CELTDecoder *st, unsigned char *data, int len, float *pcm) {
	return celt_decode_float(st, data, len, pcm);
}

static SpeexResamplerState *wrap_speex_resampler_init(unsigned int nb_channels, unsigned int in_rate, unsigned int out_rate, int quality) {
	return speex_resampler_init(nb_channels, in_rate, out_rate, quality, NULL);
}

#endif  // NATIVE_H_
