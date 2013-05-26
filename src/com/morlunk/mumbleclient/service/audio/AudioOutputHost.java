package com.morlunk.mumbleclient.service.audio;

import com.morlunk.mumbleclient.service.model.User;

public interface AudioOutputHost {
	public static final int STATE_PASSIVE = 0;
	public static final int STATE_TALKING = 1;

	public void setTalkState(User user, int talkState);
	public void setSelfMuted(User user, boolean muted);
	public void setSelfDeafened(User user, boolean deafened);
	public boolean isDeafened();
    public boolean isBluetoothActive();
}
