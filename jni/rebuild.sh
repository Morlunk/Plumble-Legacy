#!/bin/sh

pushd ${0%/*}

javac -cp ../libs/hawtjni-runtime-1.8.jar ../src/com/morlunk/mumbleclient/jni/Native.java
java -cp hawtjni-generator-1.8.jar org.fusesource.hawtjni.generator.HawtJNI -o new ../src

popd

ndk-build NDK_DEBUG=1
