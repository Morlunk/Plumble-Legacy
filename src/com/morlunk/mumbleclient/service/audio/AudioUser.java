package com.morlunk.mumbleclient.service.audio;

import java.util.Arrays;
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
	
	private long mCeltMode;
	private long mCeltDecoder;
	private long mOpusDecoder;

    private long mJitterBuffer;

    private final Queue<byte[]> mFrames = new ConcurrentLinkedQueue<byte[]>();
	private final int mCodec;
    private int mFrameSize = MumbleProtocol.FRAME_SIZE;
    private int mAudioBufferSize;
    private int mConsumedSamples;
    private int mBufferFilled;
    private int mMissedFrames;
    private boolean mHasTerminator = false;
    private boolean mLastAlive = true;

    private float[] mBuffer = null;

	private final User mUser;

	public AudioUser(final User user, final int codec) {
		mUser = user;
		mCodec = codec;

		if(codec == MumbleProtocol.CODEC_ALPHA || codec == MumbleProtocol.CODEC_BETA) {
            mAudioBufferSize = mFrameSize;
			mCeltMode = Native.celt_mode_create(
					MumbleProtocol.SAMPLE_RATE,
					MumbleProtocol.FRAME_SIZE);
			mCeltDecoder = Native.celt_decoder_create(mCeltMode, 1);
		} else if(codec == MumbleProtocol.CODEC_OPUS) {
			// With opus, we have to make sure we can hold the largest frame size- 120ms, or 5760 samples.
            mAudioBufferSize = mFrameSize*12;
			mOpusDecoder = NativeAudio.opusDecoderCreate(mAudioBufferSize, 1);
		}

        mJitterBuffer = Native.jitter_buffer_init(mFrameSize);
        int margin = 10 * mFrameSize;
        Native.jitter_buffer_ctl(mJitterBuffer, Native.JITTER_BUFFER_SET_MARGIN, new int[] { margin });

		Log.i(Globals.LOG_TAG, "AudioUser created");
	}

	public boolean addFrameToBuffer(
		final PacketDataStream pds,
		final PacketReadyHandler readyHandler) {

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

		long session = pds.readLong();
		long sequence = pds.readLong();
		
		int samples = 0;

		if(mCodec == MumbleProtocol.CODEC_OPUS) {
			long header = pds.readLong();
			int size = (int) (header & ((1 << 13) - 1));
			if(size > 0) {
				byte[] data = new byte[size];
				pds.dataBlock(data, size);
				int frames = NativeAudio.opusPacketGetFrames(data, size);
				samples = frames * NativeAudio.opusPacketGetSamplesPerFrame(data, MumbleProtocol.SAMPLE_RATE);
				
				if(samples % MumbleProtocol.FRAME_SIZE != 0)
					return false; // All samples must be divisible by the frame size.
			}
		} else {
			int header = 0;
			do {			
				header = pds.next();
                samples += mFrameSize;
                pds.skip(header & 0x7f);
			} while ((header & 0x80) > 0 && pds.isValid());
		}

        if(!pds.isValid()) {
            Log.e(Globals.LOG_TAG, "Invalid packet data stream used when adding frame to buffer!");
            return false;
        }

        // Rewind and get all data
        pds.rewind();
        byte[] data = new byte[pds.capacity()];
        pds.dataBlock(data, pds.capacity());

        final JitterBufferPacket jbp = new JitterBufferPacket();
        jbp.data = data;
        jbp.len = data.length;
        jbp.span = samples;
        jbp.timestamp = sequence * mFrameSize;

        Native.jitter_buffer_put(mJitterBuffer, jbp);
        readyHandler.packetReady(this);
		
		return true;
	}

	public boolean needSamples(int bufferSize) {
        for(int i = mConsumedSamples; i < mBufferFilled;i++) {
            mBuffer[i-mConsumedSamples] = mBuffer[i]; // Shift samples left after consumption of buffer
        }

        mBufferFilled -= mConsumedSamples;

        mConsumedSamples = bufferSize;

        if(mBufferFilled >= bufferSize)
            return mLastAlive;

        boolean nextAlive = mLastAlive;
        float[] output = new float[mAudioBufferSize];

        while(mBufferFilled < bufferSize) {
            int decodedSamples = mFrameSize;
            resizeBuffer(mBufferFilled + mAudioBufferSize);

            // Shift buffer to current frame
            System.arraycopy(mBuffer, mBufferFilled, output, 0, mAudioBufferSize);

            if(!mLastAlive) {
                // If this is a new frame, clear the buffer
                Arrays.fill(mBuffer, 0);
            } else {
                if(mFrames.size() == 0) {
                    byte[] data = new byte[4096];

                    JitterBufferPacket jbp = new JitterBufferPacket();
                    jbp.data = data;
                    jbp.len = 4096;

                    int startOffset = 0;

                    if(Native.jitter_buffer_get(mJitterBuffer, jbp, new int[] { startOffset }) == Native.JITTER_BUFFER_OK) {
                        PacketDataStream pds = new PacketDataStream(jbp.data);

                        mMissedFrames = 0;
                        pds.next(); // Skip flags
                        mHasTerminator = false;

                        /* long session = */ pds.readLong();
                        /* long sequence = */ pds.readLong();

                        if(mCodec == MumbleProtocol.UDPMESSAGETYPE_UDPVOICEOPUS) {
                            long header = pds.readLong();
                            int size = (int) (header & ((1 << 13) - 1));
                            mHasTerminator = (header & (1 << 13)) == 1;
                            if(size > 0) {
                                byte[] frameData = new byte[size];
                                pds.dataBlock(frameData, size);
                                mFrames.add(frameData);
                            }
                        } else {
                            int header = 0;
                            do {
                                header = pds.next();
                                if(header != 0) {
                                    int size = header & 0x7f;
                                    byte[] frameData = new byte[size];
                                    pds.dataBlock(frameData, size);
                                    mFrames.add(frameData);
                                } else {
                                    mHasTerminator = true;
                                }
                            } while((header & 0x80) == 1 && pds.isValid());
                        }
                    } else {
                        Native.jitter_buffer_update_delay(mJitterBuffer, jbp, new int[] { startOffset });

                        mMissedFrames++;
                        if(mMissedFrames > 10)
                            nextAlive = false;
                    }
                }

                if(mFrames.size() > 0) {
                    byte[] frameData = mFrames.poll();

                    if(mCodec == MumbleProtocol.UDPMESSAGETYPE_UDPVOICEOPUS)  {
                        decodedSamples = NativeAudio.opusDecodeFloat(mOpusDecoder, frameData, frameData.length, output, mAudioBufferSize, 0);
                    } else {
                        if(frameData.length != 0)
                            Native.celt_decode_float(mCeltDecoder, frameData, frameData.length, output);
                    }
                } else {
                    if(mCodec == MumbleProtocol.UDPMESSAGETYPE_UDPVOICEOPUS)  {
                        decodedSamples = NativeAudio.opusDecodeFloat(mOpusDecoder, null, 0, output, mFrameSize, 0);
                    } else {
                        Native.celt_decode_float(mCeltDecoder, null, 0, output);
                    }
                }

                Log.d(Globals.LOG_TAG, "Decoded: " + decodedSamples);

                for(int i=0; i < decodedSamples/mFrameSize; i++) {
                    Native.jitter_buffer_tick(mJitterBuffer); // Tick for each sample decoded
                }
            }

            mBufferFilled += decodedSamples;
        }

        boolean lastAlive = mLastAlive;
        mLastAlive = nextAlive;
        return lastAlive;
	}

    /**
     * Resizes the buffer to the new size specified.
     * Will create the buffer if necessary.
     * @param newSize The size to set the buffer to (in samples).
     */
    public void resizeBuffer(int newSize) {
        float[] newBuffer = new float[newSize];
        if(mBuffer != null)
            System.arraycopy(mBuffer, 0, newBuffer, 0, mBuffer.length);
        mBuffer = newBuffer;
    }
	
	boolean isStreaming() {
		if(mCodec == MumbleProtocol.CODEC_ALPHA || mCodec == MumbleProtocol.CODEC_BETA) {
			return mMissedFrames < 10;
		} else if(mCodec == MumbleProtocol.CODEC_OPUS) {
			// Make sure our buffer isn't entirely missed frames. (buffer holds 12 480 sample frames max)
			return mMissedFrames < (mAudioBufferSize/mFrameSize);
		}
		return false;
	}

	@Override
	protected final void finalize() {
		if(mCodec == MumbleProtocol.CODEC_ALPHA || mCodec == MumbleProtocol.CODEC_BETA) {
			Native.celt_decoder_destroy(mCeltDecoder);
			Native.celt_mode_destroy(mCeltMode);
		} else if(mCodec == MumbleProtocol.CODEC_OPUS) {
			NativeAudio.opusDecoderDestroy(mOpusDecoder);
		}
        Native.jitter_buffer_destroy(mJitterBuffer);
	}

    public User getUser() {
        return mUser;
    }

    public float[] getBuffer() {
        return mBuffer;
    }

    public int getCodec() {
        return mCodec;
    }
}
