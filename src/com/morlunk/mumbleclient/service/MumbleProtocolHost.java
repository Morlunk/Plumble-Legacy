package com.morlunk.mumbleclient.service;

import net.sf.mumble.MumbleProto.Reject;
import net.sf.mumble.MumbleProto.UserRemove;
import net.sf.mumble.MumbleProto.UserState;

import com.morlunk.mumbleclient.service.model.Channel;
import com.morlunk.mumbleclient.service.model.Message;
import com.morlunk.mumbleclient.service.model.User;

/**
 * Callback interface for Protocol to communicate back to the Service
 *
 * @author Rantanen
 *
 */
public interface MumbleProtocolHost {
	public void channelAdded(Channel channel);

	public void channelRemoved(int channelId);

	public void channelUpdated(Channel channel);

	public void currentChannelChanged();

	public void currentUserUpdated();

	public void messageReceived(Message msg);

	public void messageSent(Message msg);

	public void setError(Reject reject);

	public void setSynchronized(boolean synced);

	public void userAdded(User user);

	public void userRemoved(int userId, UserRemove remove);

	public void userStateUpdated(User user, UserState us);
	
	public void permissionDenied(String reason, int denyType);

	public void userUpdated(User user);
}
