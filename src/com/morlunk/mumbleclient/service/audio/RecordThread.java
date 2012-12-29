package com.morlunk.mumbleclient.service.audio;

import java.util.LinkedList;
import java.util.Observable;
import java.util.Observer;

import android.annotation.TargetApi;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import com.morlunk.mumbleclient.Globals;
import com.morlunk.mumbleclient.Settings;
import com.morlunk.mumbleclient.jni.Native;
import com.morlunk.mumbleclient.jni.celtConstants;
import com.morlunk.mumbleclient.service.MumbleProtocol;
import com.morlunk.mumbleclient.service.MumbleService;
import com.morlunk.mumbleclient.service.PacketDataStream;

/**
 * Thread responsible for recording voice and sending it over to server.
 *
 * @author pcgod
 *
 */
public class RecordThread implements Runnable, Observer {
	
	private float volumeMultiplier;
	private int audioQuality;
	private static int frameSize;
	private static int recordingSampleRate;
	private static final int TARGET_SAMPLE_RATE = MumbleProtocol.SAMPLE_RATE;
	private final short[] buffer;
	private int bufferSize;
	private boolean voiceActivity = false;
	private String callMode;
	private final long celtEncoder;
	private final long celtMode;
	private final int framesPerPacket = 6;
	private final LinkedList<byte[]> outputQueue = new LinkedList<byte[]>();
	private final short[] resampleBuffer = new short[MumbleProtocol.FRAME_SIZE];
	private int seq;
	private final long speexResamplerState;
	private final MumbleService mService;
	
	private static final int DETECTION_DELAY = 400; // Wait 400ms after record
	private int detectionThreshold = 1400;
	private long lastDetection = 0;
	private int talkState = AudioOutputHost.STATE_PASSIVE;

	public RecordThread(final MumbleService service, final boolean voiceActivity) {
		mService = service;
		this.voiceActivity = voiceActivity;

		Settings settings = Settings.getInstance(service);
		settings.addObserver(this);
		update(settings, null);

		for (final int s : new int[] { 48000, 44100, 22050, 11025, 8000 }) {
			bufferSize = AudioRecord.getMinBufferSize(
				s,
				AudioFormat.CHANNEL_IN_MONO,
				AudioFormat.ENCODING_PCM_16BIT);
			if (bufferSize > 0) {
				recordingSampleRate = s;
				break;
			}
		}

		if (bufferSize < 0) {
			throw new RuntimeException("No recording sample rate found");
		}

		Log.i(Globals.LOG_TAG, "Selected recording sample rate: " +
							  recordingSampleRate);

		frameSize = recordingSampleRate / 100;

		buffer = new short[frameSize];
		celtMode = Native.celt_mode_create(
			MumbleProtocol.SAMPLE_RATE,
			MumbleProtocol.FRAME_SIZE);
		celtEncoder = Native.celt_encoder_create(celtMode, 1);
		Native.celt_encoder_ctl(
			celtEncoder,
			celtConstants.CELT_SET_PREDICTION_REQUEST,
			0);
		Native.celt_encoder_ctl(
			celtEncoder,
			celtConstants.CELT_SET_VBR_RATE_REQUEST,
			audioQuality);

		if (recordingSampleRate != TARGET_SAMPLE_RATE) {
			speexResamplerState = Native.speex_resampler_init(
				1,
				recordingSampleRate,
				TARGET_SAMPLE_RATE,
				3);
		} else {
			speexResamplerState = 0;
		}
	}

	@TargetApi(11)
	@Override
	public final void run() {
		final boolean running = true;
		android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
		
		int audioSource = MediaRecorder.AudioSource.MIC;
		
		if(callMode.equals(Settings.ARRAY_CALL_MODE_SPEAKER)) {
			audioSource = (MediaRecorder.AudioSource.MIC);
		} else if(callMode.equals(Settings.ARRAY_CALL_MODE_VOICE)) {
			audioSource = (MediaRecorder.AudioSource.DEFAULT);
		}
		
		AudioRecord ar = null;
		try {
			ar = new AudioRecord(audioSource,
				recordingSampleRate,
				AudioFormat.CHANNEL_IN_MONO,
				AudioFormat.ENCODING_PCM_16BIT,
				64 * 1024);

			if (ar.getState() != AudioRecord.STATE_INITIALIZED) {
				return;
			}

			ar.startRecording();
			
			while (running && mService.isConnected() && !Thread.interrupted()) {
				final int read = ar.read(buffer, 0, frameSize);

				if (read == AudioRecord.ERROR_BAD_VALUE ||
					read == AudioRecord.ERROR_INVALID_OPERATION) {
					throw new RuntimeException("" + read);
				}

				short[] out;
				if (speexResamplerState != 0) {
					out = resampleBuffer;
					final int[] in_len = new int[] { buffer.length };
					final int[] out_len = new int[] { out.length };
					Native.speex_resampler_process_int(
						speexResamplerState,
						0,
						buffer,
						in_len,
						out,
						out_len);
				} else {
					out = buffer;
				}
				
				long totalAmplitude = 0;
				
				// Boost amplitude, if applicable- also, record avg. amplitude.
				for(int x=0;x<out.length;x++) {
					totalAmplitude += Math.abs(out[x]);
					out[x] += volumeMultiplier*out[x];
				}
				totalAmplitude /= out.length;
				
				if(voiceActivity &&
						mService != null &&
						mService.isConnected() &&
						mService.getCurrentUser() != null) {					
					if(totalAmplitude >= detectionThreshold) {
						lastDetection = System.currentTimeMillis();
					}
					
					if(System.currentTimeMillis() - lastDetection <= DETECTION_DELAY) {
						if(talkState != AudioOutputHost.STATE_TALKING) {
							mService.getAudioHost().setTalkState(mService.getCurrentUser(), AudioOutputHost.STATE_TALKING);
							talkState = AudioOutputHost.STATE_TALKING;
						}
					} else {
						if(talkState != AudioOutputHost.STATE_PASSIVE) {
							mService.getAudioHost().setTalkState(mService.getCurrentUser(), AudioOutputHost.STATE_PASSIVE);
							talkState = AudioOutputHost.STATE_PASSIVE;
						}
					}
				}
				
				final int compressedSize = Math.min(audioQuality / (100 * 8),
						127);
				final byte[] compressed = new byte[compressedSize];
				synchronized (Native.class) {
					Native.celt_encode(celtEncoder, out, compressed,
							compressedSize);
				}
				outputQueue.add(compressed);

				if (outputQueue.size() < framesPerPacket) {
					continue;
				}

				final byte[] outputBuffer = new byte[1024];
				final PacketDataStream pds = new PacketDataStream(outputBuffer);
				while (!outputQueue.isEmpty()) {
					int flags = 0;
					flags |= mService.getCodec() << 5;
					outputBuffer[0] = (byte) flags;

					pds.rewind();
					// skip flags
					pds.next();
					seq += framesPerPacket;
					pds.writeLong(seq);
					for (int i = 0; i < framesPerPacket; ++i) {
						final byte[] tmp = outputQueue.poll();
						if (tmp == null) {
							break;
						}
						int head = (short) tmp.length;
						if (i < framesPerPacket - 1) {
							head |= 0x80;
						}

						pds.append(head);
						pds.append(tmp);
					}
					
					if(talkState == AudioOutputHost.STATE_TALKING || !voiceActivity) {
						mService.sendUdpMessage(outputBuffer, pds.size());
					}
				}
			}
		} finally {
			if (ar != null) {
				ar.release();
			}
		}
	}

	@Override
	protected final void finalize() {
		if (speexResamplerState != 0) {
			Native.speex_resampler_destroy(speexResamplerState);
		}
		Native.celt_encoder_destroy(celtEncoder);
		Native.celt_mode_destroy(celtMode);
	}
	
	@Override
	public void update(Observable observable, Object data) {
		Settings settings = (Settings) observable;
		volumeMultiplier = settings.getAmplitudeBoostMultiplier();
		detectionThreshold = settings.getDetectionThreshold();
		callMode = settings.getCallMode();
		audioQuality = settings.getAudioQuality();
	}
}
