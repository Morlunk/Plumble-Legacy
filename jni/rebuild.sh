#!/bin/sh

javac -cp ../libs/hawtjni-runtime-1.1-SNAPSHOT.jar ../src/com/morlunk/mumbleclient/jni/Native.java
java -cp hawtjni-generator-1.1-SNAPSHOT.jar org.fusesource.hawtjni.generator.HawtJNI -o new ../src
