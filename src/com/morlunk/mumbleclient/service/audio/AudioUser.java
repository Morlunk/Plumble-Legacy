package com.morlunk.mumbleclient.service.audio;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import android.util.Log;

import com.morlunk.mumbleclient.Globals;
import com.morlunk.mumbleclient.jni.Native;
import com.morlunk.mumbleclient.jni.Native.JitterBufferPacket;
import com.morlunk.mumbleclient.jni.NativeAudio;
import com.morlunk.mumbleclient.service.MumbleProtocol;
import com.morlunk.mumbleclient.service.PacketDataStream;
import com.morlunk.mumbleclient.service.model.User;

/**
 * Thread safe buffer for audio data.
 * Implements audio queue and decoding.
 *
 * @author pcgod, Rantanen
 */
public class AudioUser {
	public interface PacketReadyHandler {
		public void packetReady(AudioUser user);
	}

	private final Queue<Native.JitterBufferPacket> normalBuffer;

	private final int codec;
	
	private long celtMode;
	private long celtDecoder;
	
	private long opusDecoder;
	
	private final Queue<byte[]> dataArrayPool = new ConcurrentLinkedQueue<byte[]>();
	float[] lastFrame;
	int frameSize;
	private final User user;

	private int missedFrames = 0;

	public AudioUser(final User user, final int codec) {
		this.user = user;
		this.codec = codec;

		if(codec == MumbleProtocol.CODEC_ALPHA || codec == MumbleProtocol.CODEC_BETA) {
			celtMode = Native.celt_mode_create(
					MumbleProtocol.SAMPLE_RATE,
					MumbleProtocol.FRAME_SIZE);
			celtDecoder = Native.celt_decoder_create(celtMode, 1);
			lastFrame = new float[MumbleProtocol.FRAME_SIZE];
		} else if(codec == MumbleProtocol.CODEC_OPUS) {
			opusDecoder = NativeAudio.opusDecoderCreate(MumbleProtocol.SAMPLE_RATE, 1);
			lastFrame = new float[MumbleProtocol.FRAME_SIZE*12]; // With opus, we have to make sure we can hold the largest frame size- 120ms, or 5760 samples.
		}
		
		normalBuffer = new ConcurrentLinkedQueue<Native.JitterBufferPacket>();

		Log.i(Globals.LOG_TAG, "AudioUser created");
	}

	public boolean addFrameToBuffer(
		final PacketDataStream pds,
		final PacketReadyHandler readyHandler) {
		
		if(pds.capacity() < 2) {
			return false;
		}

		final int packetHeader = pds.next();

		// Make sure this is supported voice packet.
		//
		// (Yes this check is included in MumbleConnection as well but I believe
		// it should be made here since the decoding support is built into this
		// class anyway. In theory only this class needs to know what packets
		// can be decoded.)
		final int type = (packetHeader >> 5) & 0x7;
		if (type != MumbleProtocol.UDPMESSAGETYPE_UDPVOICECELTALPHA &&
			type != MumbleProtocol.UDPMESSAGETYPE_UDPVOICECELTBETA &&
			type != MumbleProtocol.UDPMESSAGETYPE_UDPVOICEOPUS) {
			return false;
		}

		/* long session = */pds.readLong();
		/* final long sequence = */ pds.readLong();
		
		int samples = 0;

		byte[] data = null;

		if(codec == MumbleProtocol.CODEC_OPUS) {
			long header = pds.readLong();
			int size = (int) (header & ((1 << 13) - 1));
			if(size > 0) {
				data = new byte[size];
				pds.dataBlock(data, size);
				int frames = NativeAudio.opusPacketGetFrames(data, size);
				samples = frames * NativeAudio.opusPacketGetSamplesPerFrame(data, MumbleProtocol.SAMPLE_RATE);
				
				if(samples % MumbleProtocol.FRAME_SIZE != 0)
					return false; // All samples must be divisible by the frame size.
				
				Log.i(Globals.LOG_TAG, "DEBUG: "+samples+" samples, "+frames+" frames");
				
				if(pds.isValid()) {
					JitterBufferPacket jbp = new JitterBufferPacket();
					jbp.data = data;
					jbp.len = size;
					jbp.span = samples;
					
					normalBuffer.add(jbp);
					readyHandler.packetReady(this);
				}
			}
		} else {
			int dataHeader = 0;
			do {			
				dataHeader = pds.next();
				int dataLength = dataHeader & 0x7f;
				if (dataLength > 0) {
					data = acquireDataArray();
					pds.dataBlock(data, dataLength);

					final Native.JitterBufferPacket jbp = new Native.JitterBufferPacket();
					jbp.data = data;
					jbp.len = dataLength;
					
					normalBuffer.add(jbp);

					readyHandler.packetReady(this);
					//frameCount++;

				}
			} while ((dataHeader & 0x80) > 0 && pds.isValid());
		}
		
		return true;
	}

	public void freeDataArray(final byte[] data) {
		dataArrayPool.add(data);
	}

	public User getUser() {
		return this.user;
	}

	/**
	 * Checks if this user has frames and sets lastFrame.
	 *
	 * @return
	 */
	public boolean hasFrame() {
		byte[] data = null;
		int dataLength = 0;

		Native.JitterBufferPacket jbp = normalBuffer.poll();
		if (jbp != null) {
			data = jbp.data;
			dataLength = jbp.len;
			frameSize = jbp.span;
			missedFrames = 0;
		} else {
			missedFrames++;
		}

		if(codec == MumbleProtocol.CODEC_ALPHA || codec == MumbleProtocol.CODEC_BETA) {
			Native.celt_decode_float(celtDecoder, data, dataLength, lastFrame);
		} else if(codec == MumbleProtocol.CODEC_OPUS) {
			NativeAudio.opusDecodeFloat(opusDecoder,
									data == null ? null : data,
									data == null ? 0 : dataLength, 
									lastFrame, 
									MumbleProtocol.FRAME_SIZE*12, 0);
		}
		
		if (data != null) {
			freeDataArray(data);
		}

		return (missedFrames < 10);
	}

	private byte[] acquireDataArray() {
		byte[] data = dataArrayPool.poll();

		if (data == null) {
			data = new byte[128];
		}

		return data;
	}

	@Override
	protected final void finalize() {
		if(codec == MumbleProtocol.CODEC_ALPHA || codec == MumbleProtocol.CODEC_BETA) {
			Native.celt_decoder_destroy(celtDecoder);
			Native.celt_mode_destroy(celtMode);
		} else if(codec == MumbleProtocol.CODEC_OPUS) {
			NativeAudio.opusDecoderDestroy(opusDecoder);
		}
	}
}
