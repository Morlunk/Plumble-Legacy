#!/bin/sh

pushd ${0%/*}

rm -r ../src/com/morlunk/mumbleclient/swig/speex/*
swig -v -java -package com.morlunk.mumbleclient.swig.speex -outdir ../src/com/morlunk/mumbleclient/swig/speex speex.i

popd
