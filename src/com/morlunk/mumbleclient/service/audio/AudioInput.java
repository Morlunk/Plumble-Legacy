package com.morlunk.mumbleclient.service.audio;

import java.util.LinkedList;
import java.util.Observable;
import java.util.Observer;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import com.morlunk.mumbleclient.Globals;
import com.morlunk.mumbleclient.Settings;
import com.morlunk.mumbleclient.jni.Native;
import com.morlunk.mumbleclient.jni.NativeAudio;
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
public class AudioInput implements Runnable, Observer {

	private static final int DETECTION_DELAY = 400; // Wait 400ms after
													// recording to make sure no
													// audio gets cut off

	// Audio recording properties
	private float volumeMultiplier;
	private int audioQuality;
	private boolean voiceActivity;
	private String callMode;
	
	// Network audio data
	private static int frameSize;
	private static int recordingSampleRate;
	private static final int TARGET_SAMPLE_RATE = MumbleProtocol.SAMPLE_RATE;
	private final short[] buffer;
	private int bufferSize;
	private int codec;

	// CELT native instances
	private long celtEncoder;
	private long celtMode;
	// Opus native instances
	private long opusEncoder;

	private final int framesPerPacket = 6;
	private final LinkedList<byte[]> outputQueue = new LinkedList<byte[]>();
	private final short[] resampleBuffer = new short[MumbleProtocol.FRAME_SIZE];
	private int seq;
	private final long speexResamplerState;
	private final MumbleService mService;
	private int detectionThreshold = 1400;
	private long lastDetection = 0;
	private int talkState = AudioOutputHost.STATE_PASSIVE;

	// Recording state
	private AudioRecord audioRecord;
	private boolean running;
	private Thread recordThread;

	public AudioInput(final MumbleService service, final int codec) {
		mService = service;
		this.codec = codec;

		Settings settings = Settings.getInstance(service);
		settings.addObserver(this);

		voiceActivity = settings.isVoiceActivity();
		detectionThreshold = settings.getDetectionThreshold();
		callMode = settings.getCallMode();
		audioQuality = settings.getAudioQuality();
		volumeMultiplier = settings.getAmplitudeBoostMultiplier();

		for (final int s : new int[] { 48000, 44100, 22050, 11025, 8000 }) {
			bufferSize = AudioRecord
					.getMinBufferSize(s, AudioFormat.CHANNEL_IN_MONO,
							AudioFormat.ENCODING_PCM_16BIT);
			if (bufferSize > 0) {
				recordingSampleRate = s;
				break;
			}
		}

		if (bufferSize < 0) {
			throw new RuntimeException("No recording sample rate found");
		}

		Log.i(Globals.LOG_TAG, "Selected recording sample rate: "
				+ recordingSampleRate);

		frameSize = recordingSampleRate / 100;

		buffer = new short[frameSize];

		if (codec == MumbleProtocol.CODEC_OPUS) {
			opusEncoder = NativeAudio.opusEncoderCreate(
					MumbleProtocol.SAMPLE_RATE, 1,
					NativeAudio.OPUS_APPLICATION_VOIP);
			NativeAudio.opusEncoderCtl(opusEncoder,
					NativeAudio.OPUS_SET_VBR_REQUEST, 0);
		} else if (codec == MumbleProtocol.CODEC_BETA
				|| codec == MumbleProtocol.CODEC_ALPHA) {
			celtMode = Native.celt_mode_create(MumbleProtocol.SAMPLE_RATE,
					MumbleProtocol.FRAME_SIZE);
			celtEncoder = Native.celt_encoder_create(celtMode, 1);
			Native.celt_encoder_ctl(celtEncoder,
					celtConstants.CELT_SET_PREDICTION_REQUEST, 0);
			Native.celt_encoder_ctl(celtEncoder,
					celtConstants.CELT_SET_VBR_RATE_REQUEST, audioQuality);
		}

		if (recordingSampleRate != TARGET_SAMPLE_RATE) {
			Log.d(Globals.LOG_TAG, "Initializing Speex resampler.");
			speexResamplerState = Native.speex_resampler_init(1,
					recordingSampleRate, TARGET_SAMPLE_RATE, 3);
		} else {
			speexResamplerState = 0;
		}

		int audioSource = MediaRecorder.AudioSource.DEFAULT;

		if (callMode.equals(Settings.ARRAY_CALL_MODE_SPEAKER)) {
			audioSource = (MediaRecorder.AudioSource.MIC);
		} else if (callMode.equals(Settings.ARRAY_CALL_MODE_VOICE)) {
			audioSource = (MediaRecorder.AudioSource.DEFAULT);
		}

		audioRecord = new AudioRecord(audioSource, recordingSampleRate,
				AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
				64 * 1024);

		/*
		 * if(VERSION.SDK_INT >= 16 && AcousticEchoCanceler.isAvailable()) {
		 * Log.d(Globals.LOG_TAG, "Enabled echo cancellation.");
		 * AcousticEchoCanceler.create(ar.getAudioSessionId()); }
		 */
	}

	@Override
	public final void run() {
		running = true;
		android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);

		if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
			return;
		}
		
		audioRecord.startRecording();

		while (running && mService.isConnected()) {
			final int read = audioRecord.read(buffer, 0, frameSize);

			if (read == AudioRecord.ERROR_BAD_VALUE
					|| read == AudioRecord.ERROR_INVALID_OPERATION) {
				throw new RuntimeException("" + read);
			}

			short[] out;
			if (speexResamplerState != 0) {
				out = resampleBuffer;
				final int[] in_len = new int[] { buffer.length };
				final int[] out_len = new int[] { out.length };
				Native.speex_resampler_process_int(speexResamplerState, 0,
						buffer, in_len, out, out_len);
			} else {
				out = buffer;
			}

			long totalAmplitude = 0;

			// Boost amplitude, if applicable- also, record avg. amplitude.
			for (int x = 0; x < out.length; x++) {
				totalAmplitude += Math.abs(out[x]);
				out[x] += volumeMultiplier * out[x];
			}
			totalAmplitude /= out.length;

			if (voiceActivity && mService != null && mService.isConnected()
					&& mService.getCurrentUser() != null) {
				if (totalAmplitude >= detectionThreshold) {
					lastDetection = System.currentTimeMillis();
				}

				if (System.currentTimeMillis() - lastDetection <= DETECTION_DELAY) {
					if (talkState != AudioOutputHost.STATE_TALKING) {
						mService.getAudioHost().setTalkState(
									mService.getCurrentUser(),
									AudioOutputHost.STATE_TALKING);
						talkState = AudioOutputHost.STATE_TALKING;
					}
				} else {
					if (talkState != AudioOutputHost.STATE_PASSIVE) {
						mService.getAudioHost().setTalkState(
									mService.getCurrentUser(),
									AudioOutputHost.STATE_PASSIVE);
						talkState = AudioOutputHost.STATE_PASSIVE;
					}
				}
			}

			final int compressedSize = Math.min(audioQuality / (100 * 8), 127);
			final byte[] compressed = new byte[compressedSize];
			synchronized (Native.class) {
				if (codec == MumbleProtocol.CODEC_OPUS) {
					NativeAudio.opusEncoderCtl(opusEncoder,
							NativeAudio.OPUS_SET_BITRATE_REQUEST, audioQuality);
					NativeAudio.opusEncode(opusEncoder, out, frameSize,
							compressed, compressedSize);
				} else if (codec == MumbleProtocol.CODEC_BETA
						|| codec == MumbleProtocol.CODEC_ALPHA) {
					Native.celt_encode(celtEncoder, out, compressed,
							compressedSize);
				}
			}
			outputQueue.add(compressed);

			if (outputQueue.size() < framesPerPacket) {
				continue;
			}

			final byte[] outputBuffer = new byte[1024];
			final PacketDataStream pds = new PacketDataStream(outputBuffer);
			while (!outputQueue.isEmpty()) {
				int flags = 0;
				flags |= codec << 5;
				outputBuffer[0] = (byte) (flags & 0xff);

				if (codec == MumbleProtocol.CODEC_OPUS) {
					pds.rewind();
					pds.next();
					seq += 1;
					pds.writeLong(seq);
					byte[] frame = outputQueue.poll();
					long header = frame.length;
					pds.writeLong(header);
					pds.append(frame);
				} else if (codec == MumbleProtocol.CODEC_BETA
						|| codec == MumbleProtocol.CODEC_ALPHA) {
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
				}

				if (talkState == AudioOutputHost.STATE_TALKING
						|| !voiceActivity) {
					mService.sendUdpMessage(outputBuffer, pds.size());
				}
			}
		}

		if (audioRecord != null
				&& audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING)
			audioRecord.stop();
		recordThread = null;
	}

	/**
	 * Returns whether or not the AudioRecord object that this class manages is currently recording.
	 */
	public boolean isRecording() {
		return audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING;
	}

	/**
	 * Spawns an instance of the audio recording thread. If an audio recording
	 * thread is already active, do nothing.
	 */
	public void startRecording() {
		if (recordThread != null) {
			Log.w(Globals.LOG_TAG,
					"Attempted to start recording while a RecordThread was still running!");
			return;
		}

		recordThread = new Thread(this);
		recordThread.start();
	}

	/**
	 * Kills the active audio recording thread. If no audio recording thread is
	 * active, do nothing.
	 */
	public void stopRecording() {
		if (recordThread == null) {
			Log.w(Globals.LOG_TAG,
					"Attempted to stop recording when a RecordThread was not running!");
			return;
		}

		running = false;
	}
	
	/**
	 * Kills the active audio recording thread, and blocks the thread until we know it has stopped recording.
	 */
	public void stopRecordingAndBlock() throws InterruptedException {
		if (recordThread == null) {
			Log.w(Globals.LOG_TAG,
					"Attempted to stop recording when a RecordThread was not running!");
			return;
		}

		running = false;
		recordThread.join();
	}

	/**
	 * Stops the record thread and finalizes all allocated audio and native
	 * resources. The AudioInput object cannot be reused after destruction.
	 */
	public void terminate() {
		if (speexResamplerState != 0)
			Native.speex_resampler_destroy(speexResamplerState);

		if (opusEncoder != 0)
			NativeAudio.opusEncoderDestroy(opusEncoder);

		if (celtEncoder != 0)
			Native.celt_encoder_destroy(celtEncoder);

		if (celtMode != 0)
			Native.celt_mode_destroy(celtMode);

		Settings.getInstance(mService).deleteObserver(this);

		audioRecord.release();
	}

	@Override
	public void update(Observable observable, Object data) {
		Settings settings = (Settings) observable;
		if (data == Settings.OBSERVER_KEY_ALL) {
			voiceActivity = settings.isVoiceActivity();
			detectionThreshold = settings.getDetectionThreshold();
			callMode = settings.getCallMode();
			audioQuality = settings.getAudioQuality();
		} else if (data == Settings.OBSERVER_KEY_AMPLITUDE) {
			volumeMultiplier = settings.getAmplitudeBoostMultiplier();
		}
	}
}
