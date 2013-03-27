package com.morlunk.mumbleclient.service.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import android.os.Parcel;
import android.os.Parcelable;

public class Channel implements Parcelable {
	public static final Parcelable.Creator<Channel> CREATOR = new Creator<Channel>() {
		@Override
		public Channel createFromParcel(final Parcel source) {
			return new Channel(source);
		}

		@Override
		public Channel[] newArray(final int size) {
			return new Channel[size];
		}
	};

	public int id;
	public String name;
	public int userCount;
	public int position;
	public int parent;
	private List<User> users = new ArrayList<User>();

	/**
	 * Value signaling whether this channel has just been removed.
	 * Once this value is set the connection signals one last update for the
	 * channel which should result in the channel being removed from all the
	 * caches where it might be stored.
	 */
	public boolean removed = false;

	public Channel() {
	}

	public Channel(final Parcel parcel) {
		readFromParcel(parcel);
	}

	@Override
	public int describeContents() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public final boolean equals(final Object o) {
		if (!(o instanceof Channel)) {
			return false;
		}
		return id == ((Channel) o).id;
	}

	@Override
	public final int hashCode() {
		return id;
	}

	@Override
	public final String toString() {
		return "Channel [id=" + id + ", name=" + name + ", userCount=" +
			   userCount + "]";
	}
	
	public void addUser(User user) {
		users.add(user);
		sortUsers();
	}
	
	public void removeUser(User user) {
		users.remove(user);
		sortUsers();
	}
	
	public List<User> getUsers() {
		return users;
	}
	
	private void sortUsers() {
		// Sort alphabetically
		Collections.sort(users, new Comparator<User>() {
			@Override
			public int compare(User lhs, User rhs) {
				return lhs.name.toLowerCase(Locale.getDefault()).compareTo(rhs.name.toLowerCase(Locale.getDefault()));
			}
		});;
	}

	@Override
	public void writeToParcel(final Parcel dest, final int flags) {
		dest.writeInt(0); // Version

		dest.writeInt(id);
		dest.writeString(name);
		dest.writeInt(userCount);
		dest.writeInt(position);
		dest.writeInt(parent);
		dest.writeList(users);
	}
	
	@SuppressWarnings("unchecked")
	private void readFromParcel(final Parcel in) {
		in.readInt(); // Version

		id = in.readInt();
		name = in.readString();
		userCount = in.readInt();
		position = in.readInt();
		parent = in.readInt();
		users = in.readArrayList(User.class.getClassLoader());
	}
}
