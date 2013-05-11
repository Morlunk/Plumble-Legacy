// IMumbleService.aidl
package com.morlunk.mumbleclient.service;

import com.morlunk.mumbleclient.service.IMumbleServiceObserver;
import com.morlunk.mumbleclient.service.model.Channel;
import com.morlunk.mumbleclient.service.model.User;

/** Interface for interacting with a Plumble service and its connection to a Mumble server */
interface IMumbleService {
	
	/** Returns the current connection state of the service. */
	int getConnectionState();
	
	/** Retrieves a map of channels to their users. */
	Map getChannelMap();
	
	/** Retrieves a flat list of all channels, sorted alphebetically and by position. */
	List<Channel> getChannelList();
	
	/** Retrieves a list of users in the specified channel. */
	List<User> getChannelUsers(int channel);
	
	/** Retrieves a flat list of all users, sorted alphebetically. */
	List<User> getOnlineUsers();
	
	/** Retrieves the user for the specified id. */
	User getUser(int id);
	
	/** Retrieves the channel for the specified id. */
	Channel getChannel(int id);
	
	/** Registers an observer to monitor service connection changes. */
	void registerObserver(IMumbleServiceObserver observer);
	
	/** Unregisters an observer to stop monitor service connection changes. */
	void unregisterObserver(IMumbleServiceObserver observer);
}