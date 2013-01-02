package com.morlunk.mumbleclient.service;

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
				final long timestamp = System.currentTimeMillis();

				// UDP
				PacketDataStream pds = new PacketDataStream(udpBuffer);
				pds.next();
				pds.writeLong(timestamp);
				
				if(pds.isValid()) {
					mc.sendUdpMessage(udpBuffer, udpBuffer.length, true);
				}

				// TCP
				final Ping.Builder p = Ping.newBuilder();
				p.setTimestamp(timestamp);
				mc.sendTcpMessage(MumbleProtocol.MessageType.Ping, p);
				Thread.sleep(5000);
			} catch (final InterruptedException e) {
				e.printStackTrace();
				running = false;
			}
		}
	}
}
