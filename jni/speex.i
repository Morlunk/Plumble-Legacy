/* File : speex.i */

%module Speex

%include audio_types.i

/* Use this to resolve speex types to primitives */
#define __EMX__ 1

%{
#include <speex/speex_types.h>
#include <speex/speex_jitter.h>
#include <speex/speex_echo.h>
#include <speex/speex_resampler.h>
%}

/* Remove underscore */
%rename(JitterBufferPacket) _JitterBufferPacket;

%include "speex/include/speex/speex_types.h"
%include "speex/include/speex/speex_jitter.h"
%include "speex/include/speex/speex_echo.h"
%include "speex/include/speex/speex_resampler.h"