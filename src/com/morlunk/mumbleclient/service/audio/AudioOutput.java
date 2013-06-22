package com.morlunk.mumbleclient.service.audio;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;

import com.morlunk.mumbleclient.Globals;
import com.morlunk.mumbleclient.Settings;
import com.morlunk.mumbleclient.service.MumbleProtocol;
import com.morlunk.mumbleclient.service.PacketDataStream;
import com.morlunk.mumbleclient.service.audio.AudioUser.PacketReadyHandler;
import com.morlunk.mumbleclient.service.model.User;

/**
 * Audio output thread.
 * Handles the playback of UDP packets added with addFrameToBuffer.
 *
 * @author pcgod, Rantanen
 */
public class AudioOutput implements Runnable {
	private final PacketReadyHandler packetReadyHandler = new PacketReadyHandler() {
		@Override
		public void packetReady(final AudioUser user) {
			synchronized (userPackets) {
				if (!userPackets.containsKey(user.getUser())) {
					userPackets.put(user.getUser(), user);
					userPackets.notify();
				}
			}
		}
	};

	private final static int standbyTreshold = 5000;
	private final Settings settings;

	private boolean shouldRun;
	private final AudioTrack at;
	private final int bufferSize;
	private final int minBufferSize;

	final Map<User, AudioUser> userPackets = new HashMap<User, AudioUser>();
	private final Map<User, AudioUser> users = new HashMap<User, AudioUser>();

	/**
	 * Buffer used to hold temporary float values while mixing multiple
	 * inputs. Only for use in the audio thread.
	 */
	final float[] tempMix = new float[MumbleProtocol.FRAME_SIZE*12];

	private final AudioOutputHost host;

	public AudioOutput(final Context ctx, final AudioOutputHost host, final boolean bluetoothConnected) {
		this.settings = Settings.getInstance(ctx);
		this.host = host;

        Log.d(Globals.LOG_TAG, "Setting up audio output with bluetooth "+bluetoothConnected);

        int audioStream;
        if(bluetoothConnected || Settings.ARRAY_CALL_MODE_VOICE.equals(settings.getCallMode())) {
            audioStream = AudioManager.STREAM_VOICE_CALL;
        } else {
            audioStream = AudioManager.STREAM_MUSIC;
        }

		minBufferSize = AudioTrack.getMinBufferSize(
			MumbleProtocol.SAMPLE_RATE,
			AudioFormat.CHANNEL_OUT_MONO,
			AudioFormat.ENCODING_PCM_16BIT);

		// Double the buffer size to reduce stuttering.
		final int desiredBufferSize = minBufferSize * 2;

		// Resolve the minimum frame count that fills the minBuffer requirement.
		final int frameCount = (int) Math.ceil((double) desiredBufferSize /
											   (MumbleProtocol.FRAME_SIZE));

		bufferSize = frameCount * (MumbleProtocol.FRAME_SIZE);

		at = new AudioTrack(
			audioStream,
			MumbleProtocol.SAMPLE_RATE,
			AudioFormat.CHANNEL_OUT_MONO,
			AudioFormat.ENCODING_PCM_16BIT,
			bufferSize,
			AudioTrack.MODE_STREAM);

		// Set this here so this.start(); this.shouldRun = false; doesn't
		// result in run() setting shouldRun to true afterwards and continuing
		// running.
		shouldRun = true;
	}

	public void addFrameToBuffer(
		final User u,
		final byte[] audioData,
		final long sequence,
        final int codec) {
		AudioUser user = users.get(u);
		if (user == null || user.getCodec() != codec) {
			user = new AudioUser(u, codec);
			users.put(u, user);
			// Don't add the user to userPackets yet. The collection should
			// have only users with ready frames. Since this method is
			// called only from the TCP connection thread it will never
			// create a new AudioUser while a previous one is still decoding.
		}

		user.addFrameToBuffer(audioData, sequence, packetReadyHandler);
	}

	public void run() {
		android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
		try {
			audioLoop();
		} catch (final InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void stop() {
		shouldRun = false;
		synchronized (userPackets) {
			userPackets.notify();
		}
	}

	private void audioLoop() throws InterruptedException {
		final short[] out = new short[MumbleProtocol.FRAME_SIZE*12];
		final List<AudioUser> mix = new LinkedList<AudioUser>();

		int buffered = 0;
		boolean playing = false;

		while (shouldRun) {
			mix.clear();

			// Get mix frames from the AudioUsers
			fillMixFrames(mix);

			// If there is output, play it now if not deafened.
			if (mix.size() > 0 && 
					!host.isDeafened()) {
				// Mix all the frames into one array.
				mix(out, mix);

				at.write(out, 0, out.length);

				// Make sure we are playing when there are enough samples
				// buffered.
				if (!playing) {
					buffered += out.length;

					if (buffered >= minBufferSize) {
						at.play();
						playing = true;
						buffered = 0;

						Log.i(
							Globals.LOG_TAG,
							"AudioOutput: Enough data buffered. Starting audio.");
					}
				}

				// Continue with playback since we know that there is at least
				// one AudioUser in userPackets that wasn't removed as it had
				// frames for mixing.
				continue;
			}

			// Wait for more input.
			playing &= !pauseForInput();
			if (!playing && buffered > 0) {
				Log.w(
					Globals.LOG_TAG,
					"AudioOutput: Stopped playing while buffered data present.");
			}
		}
		at.flush();
		at.stop();
	}

	private void fillMixFrames(final List<AudioUser> mix) {
		synchronized (userPackets) {
			final Iterator<AudioUser> i = userPackets.values().iterator();
			while (i.hasNext()) {
				final AudioUser user = i.next();
				if (user.needSamples(minBufferSize)) {
					if(!user.getUser().localMuted) {
						mix.add(user);
					}
				} else {
					i.remove();
				}
			}
		}
	}

	private void mix(final short[] clipOut, final List<AudioUser> mix) {
		// Reset mix buffer.
		Arrays.fill(tempMix, 0);

		// Sum the buffers.
		for (final AudioUser user : mix) {
			for (int i = 0; i < MumbleProtocol.FRAME_SIZE*12; i++) {
				tempMix[i] += user.getBuffer()[i];
			}
		}

		// Clip buffer for real output.
		for (int i = 0; i < MumbleProtocol.FRAME_SIZE*12; i++) {
			clipOut[i] = (short) (Short.MAX_VALUE * (tempMix[i] < -1.0f ? -1.0f
				: (tempMix[i] > 1.0f ? 1.0f : tempMix[i])));
		}
	}

	private boolean pauseForInput() throws InterruptedException {
		long silentTime;
		boolean paused = false;
		synchronized (userPackets) {
			silentTime = System.currentTimeMillis();

			// Wait with the audio on
			while (shouldRun && userPackets.isEmpty() &&
				   (silentTime + standbyTreshold) > System.currentTimeMillis()) {

				userPackets.wait((silentTime + standbyTreshold) -
								 System.currentTimeMillis() + 1);
			}

			// If conditions are still not filled, pause audio and wait more.
			if (shouldRun && userPackets.isEmpty()) {
				at.pause();
				paused = true;
				Log.i(
					Globals.LOG_TAG,
					"AudioOutput: Standby timeout reached. Audio paused.");

				while (shouldRun && userPackets.isEmpty()) {
					userPackets.wait();
				}
			}
		}
		return paused;
	}
}
