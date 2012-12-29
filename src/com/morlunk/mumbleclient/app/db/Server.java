package com.morlunk.mumbleclient.app.db;

import android.os.Parcel;
import android.os.Parcelable;

public class Server implements Parcelable {
	
	private Integer id;
	private String name;
	private String host;
	private Integer port;
	private String username;
	private String password;
	
	protected Server(Integer id,
			String name,
			String host,
			Integer port,
			String username,
			String password) {
		this.id = id;
		this.name = name;
		this.host = host;
		this.port = port;
		this.username = username;
		this.password = password;
	}
	
	private Server(Parcel in) {
		id = in.readInt();
		name = in.readString();
		host = in.readString();
		port = in.readInt();
		username = in.readString();
		password = in.readString();
	}

	public Integer getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getHost() {
		return host;
	}

	public Integer getPort() {
		return port;
	}

	public String getUsername() {
		return username;
	}

	public String getPassword() {
		return password;
	}

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeInt(id);
		dest.writeString(name);
		dest.writeString(host);
		dest.writeInt(port);
		dest.writeString(username);
		dest.writeString(password);
	}
	
	public static final Parcelable.Creator<Server> CREATOR = new Parcelable.Creator<Server>() {

		@Override
		public Server createFromParcel(Parcel source) {
			return new Server(source);
		}

		@Override
		public Server[] newArray(int size) {
			return new Server[size];
		}
	};
}
