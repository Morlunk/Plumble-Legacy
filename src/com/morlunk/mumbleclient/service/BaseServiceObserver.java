package com.morlunk.mumbleclient.service;

import net.sf.mumble.MumbleProto.UserRemove;
import net.sf.mumble.MumbleProto.UserState;
import android.os.IBinder;
import android.os.RemoteException;

import com.morlunk.mumbleclient.service.model.Channel;
import com.morlunk.mumbleclient.service.model.Message;
import com.morlunk.mumbleclient.service.model.User;

public class BaseServiceObserver {
	public IBinder asBinder() {
		return null;
	}

	public void onChannelAdded(final Channel channel) throws RemoteException {
	}

	public void onChannelRemoved(final Channel channel) throws RemoteException {
	}

	public void onChannelUpdated(final Channel channel) throws RemoteException {
	}

	public void onConnectionStateChanged(final int state)
		throws RemoteException {
	}

	public void onCurrentChannelChanged() throws RemoteException {
	}

	public void onCurrentUserUpdated() throws RemoteException {
	}

	public void onMessageReceived(final Message msg) throws RemoteException {
	}

	public void onMessageSent(final Message msg) throws RemoteException {
	}

	public void onUserAdded(final User user) throws RemoteException {
	}

	public void onUserRemoved(final User user, final UserRemove reject) throws RemoteException {
	}
	
	public void onUserUpdated(final User user) throws RemoteException {
	}
	
	public void onUserTalkingUpdated(final User user) {
	}

	public void onUserStateUpdated(final User user, UserState state) throws RemoteException {
	}
	
	public void onPermissionDenied(String reason, int denyType) throws RemoteException {
	}
}
