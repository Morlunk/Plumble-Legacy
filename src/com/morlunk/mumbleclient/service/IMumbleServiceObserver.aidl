// IMumbleServiceObserver.aidl
package com.morlunk.mumbleclient.service;

import com.morlunk.mumbleclient.service.model.Channel;
import com.morlunk.mumbleclient.service.model.User;
import com.morlunk.mumbleclient.service.model.Message;

/** Interface for receiving messages from a Plumble service connection */
interface IMumbleServiceObserver {
	
	void onChannelAdded(in Channel channel);

	void onChannelRemoved(in Channel channel);

	void onChannelUpdated(in Channel channel);

	void onConnectionStateChanged(int state);

	void onCurrentChannelChanged();
	
	void onCurrentUserUpdated();
	
	void onMessageReceived(in Message msg);
	
	void onMessageSent(in Message msg);
	
	void onUserAdded(in User user);
	
	//void onUserRemoved(in User user, UserRemove reject);
	
	void onUserUpdated(in User user);
	
	void onUserTalkingUpdated(in User user);
	
	//void onUserStateUpdated(in User user, UserState state);
	
	void onPermissionDenied(String reason, int denyType);
}