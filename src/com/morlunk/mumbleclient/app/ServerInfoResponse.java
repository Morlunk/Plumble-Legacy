package com.morlunk.mumbleclient.app;

import java.nio.ByteBuffer;

/**
 * Response from server pings.
 * @see http://mumble.sourceforge.net/Protocol
 * @author morlunk
 */
public class ServerInfoResponse {
	private long identifier;
	private int version;
	private int currentUsers;
	private int maximumUsers;
	private int allowedBandwidth;
	
	/**
	 * Creates a ServerInfoResponse object with the bytes obtained from the server.
	 * @param response The response to the UDP pings sent by the server.
	 * @see http://mumble.sourceforge.net/Protocol
	 */
	public ServerInfoResponse(byte[] response) {
		ByteBuffer buffer = ByteBuffer.wrap(response);
		this.version = buffer.getInt();
		this.identifier = buffer.getLong();
		this.currentUsers = buffer.getInt();
		this.maximumUsers = buffer.getInt();
		this.allowedBandwidth = buffer.getInt();
	}
	
	public ServerInfoResponse(int version, int currentUsers, int maximumUsers,
			int allowedBandwidth) {
		this.version = version;
		this.currentUsers = currentUsers;
		this.maximumUsers = maximumUsers;
		this.allowedBandwidth = allowedBandwidth;
	}
	
	public long getIdentifier() {
		return identifier;
	}

	public int getVersion() {
		return version;
	}
	
	public String getVersionString() {
		byte[] versionBytes = ByteBuffer.allocate(4).putInt(version).array();
		return String.format("%d.%d.%d", (int)versionBytes[1], (int)versionBytes[2], (int)versionBytes[3]);
	}

	public int getCurrentUsers() {
		return currentUsers;
	}

	public int getMaximumUsers() {
		return maximumUsers;
	}

	public int getAllowedBandwidth() {
		return allowedBandwidth;
	}
}
