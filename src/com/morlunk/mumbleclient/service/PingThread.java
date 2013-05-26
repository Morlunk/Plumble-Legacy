package com.morlunk.mumbleclient.service;

import android.util.Log;
import com.morlunk.mumbleclient.Globals;
import net.sf.mumble.MumbleProto.Ping;

class PingThread implements Runnable {
	private boolean running = true;
	private final MumbleConnection mc;
	private final byte[] udpBuffer = new byte[16];

	public PingThread(final MumbleConnection mc_) {
		this.mc = mc_;

		// Type: Ping
		udpBuffer[0] = MumbleProtocol.UDPMESSAGETYPE_UDPPING << 5;
	}

	@Override
	public final void run() {
		while (running && mc.isConnectionAlive()) {
			try {
				final long timestamp = mc.getElapsedTime();
				
				// UDP
				udpBuffer[1] = (byte) ((timestamp >> 56) & 0xFF);
				udpBuffer[2] = (byte) ((timestamp >> 48) & 0xFF);
				udpBuffer[3] = (byte) ((timestamp >> 40) & 0xFF);
				udpBuffer[4] = (byte) ((timestamp >> 32) & 0xFF);
				udpBuffer[5] = (byte) ((timestamp >> 24) & 0xFF);
				udpBuffer[6] = (byte) ((timestamp >> 16) & 0xFF);
				udpBuffer[7] = (byte) ((timestamp >> 8) & 0xFF);
				udpBuffer[8] = (byte) ((timestamp) & 0xFF);

				mc.sendUdpMessage(udpBuffer, udpBuffer.length, true);

				// TCP
				CryptState cs = mc.cryptState;
				final Ping.Builder p = Ping.newBuilder();
				p.setTimestamp(timestamp);
				p.setGood(cs.uiGood);
				p.setLate(cs.uiLate);
				p.setLost(cs.uiLost);
				p.setResync(cs.uiResync);
				
				mc.sendTcpMessage(MumbleProtocol.MessageType.Ping, p);
                Thread.sleep(5000);
			} catch (final InterruptedException e) {
				e.printStackTrace();
				running = false;
			}
		}
	}
}
