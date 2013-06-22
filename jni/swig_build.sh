#!/bin/sh

pushd ${0%/*}

# Speex
rm -r ../src/com/morlunk/mumbleclient/swig/speex/*
swig -java -package com.morlunk.mumbleclient.swig.speex -outdir ../src/com/morlunk/mumbleclient/swig/speex speex.i

# CELT
rm -r ../src/com/morlunk/mumbleclient/swig/celt/*
swig -java -package com.morlunk.mumbleclient.swig.celt -outdir ../src/com/morlunk/mumbleclient/swig/celt celt.i

# Opus
rm -r ../src/com/morlunk/mumbleclient/swig/opus/*
swig -java -package com.morlunk.mumbleclient.swig.opus -outdir ../src/com/morlunk/mumbleclient/swig/opus opus.i

popd
