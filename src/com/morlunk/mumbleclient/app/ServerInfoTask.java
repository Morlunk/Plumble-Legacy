package com.morlunk.mumbleclient.app;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;

import android.os.AsyncTask;
import android.util.Log;

import com.morlunk.mumbleclient.Globals;
import com.morlunk.mumbleclient.app.db.Server;

/**
 * Pings the requested server and returns a ServerInfoResponse.
 * Will return a 'dummy' ServerInfoResponse in the case of failure.
 * @author morlunk
 *
 */
public class ServerInfoTask extends AsyncTask<Server, Void, ServerInfoResponse> {
	
	private Server server;
	
	@Override
	protected ServerInfoResponse doInBackground(Server... params) {
		server = params[0];
		try {
			InetAddress host = InetAddress.getByName(server.getHost());
			
			// Create ping message
			ByteBuffer buffer = ByteBuffer.allocate(12);
			buffer.putInt(0); // Request type
			buffer.putLong((long)server.getId()); // Identifier
			DatagramPacket requestPacket = new DatagramPacket(buffer.array(), 12, host, server.getPort());
			
			// Send packet and wait for response
			DatagramSocket socket = new DatagramSocket();
			socket.setSoTimeout(1000);
			socket.setReceiveBufferSize(1024);
			socket.send(requestPacket);
			
			byte[] responseBuffer = new byte[24];
			DatagramPacket responsePacket = new DatagramPacket(responseBuffer, responseBuffer.length);
			socket.receive(responsePacket);
			
			ServerInfoResponse response = new ServerInfoResponse(responseBuffer);
							
			Log.i(Globals.LOG_TAG, "DEBUG: Server version: "+response.getVersionString()+"\nUsers: "+response.getCurrentUsers()+"/"+response.getMaximumUsers());
			
			return response;
			
		} catch (Exception e) {
			e.printStackTrace();
		}

		return new ServerInfoResponse(); // Return dummy in case of failure
	}
	
}