package com.morlunk.mumbleclient.service.model;

import junit.framework.Assert;
import android.os.Parcel;
import android.os.Parcelable;

import com.google.protobuf.ByteString;

public class User implements Parcelable {
	public static final Parcelable.Creator<User> CREATOR = new Creator<User>() {
		@Override
		public User createFromParcel(final Parcel source) {
			return new User(source);
		}

		@Override
		public User[] newArray(final int size) {
			return new User[size];
		}
	};

	public static final int TALKINGSTATE_PASSIVE = 0;
	public static final int TALKINGSTATE_TALKING = 1;
	public static final int TALKINGSTATE_SHOUTING = 2;
	public static final int TALKINGSTATE_WHISPERING = 3;

	public int session;
	public String name;
	public String comment;
	public ByteString commentHash;
	public float averageAvailable;
	public int talkingState;
	public boolean isCurrent;
	public boolean isRegistered;
	
	public boolean selfMuted;
	public boolean selfDeafened;
	public boolean suppressed;
	public boolean serverMuted;
	public boolean serverDeafened;
	
	public boolean localMuted = false;

	private Channel channel;

	public User() {
	}

	public User(final Parcel in) {
		readFromParcel(in);
	}

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public final boolean equals(final Object o) {
		if (!(o instanceof User)) {
			return false;
		}
		return session == ((User) o).session;
	}

	public final Channel getChannel() {
		return this.channel;
	}

	@Override
	public final int hashCode() {
		return session;
	}

	public void setChannel(final Channel newChannel) {
		// Moving user to another channel?
		// If so, remove the user from the original first.
		if (this.channel != null) {
			this.channel.userCount--;
		}

		// User should never leave channel without joining a new one?
		Assert.assertNotNull(newChannel);

		this.channel = newChannel;
		this.channel.userCount++;
	}

	@Override
	public final String toString() {
		return "User [session=" + session + ", name=" + name + ", channel=" +
			   channel + "]";
	}

	@Override
	public void writeToParcel(final Parcel dest, final int flags) {
		dest.writeInt(0); // Version

		dest.writeInt(session);
		dest.writeString(name);
		dest.writeInt(localMuted ? 1 : 0);
		dest.writeString(comment);
		dest.writeString(commentHash != null ? commentHash.toStringUtf8() : "");
		dest.writeFloat(averageAvailable);
		dest.writeInt(talkingState);
		dest.writeBooleanArray(new boolean[] { isCurrent, selfMuted, selfDeafened, suppressed, serverMuted, serverDeafened, isRegistered });
		dest.writeParcelable(channel, 0);
	}

	private void readFromParcel(final Parcel in) {
		in.readInt(); // Version

		session = in.readInt();
		name = in.readString();
		localMuted = in.readInt() == 1;
		comment = in.readString();
		commentHash = ByteString.copyFromUtf8(in.readString());
		averageAvailable = in.readFloat();
		talkingState = in.readInt();
		final boolean[] boolArr = new boolean[6];
		in.readBooleanArray(boolArr);
		isCurrent = boolArr[0];
		selfMuted = boolArr[1];
		selfDeafened = boolArr[2];
		suppressed = boolArr[3];
		serverMuted = boolArr[4];
		serverDeafened = boolArr[5];
		isRegistered = boolArr[6];
		channel = in.readParcelable(Channel.class.getClassLoader());
	}
}
