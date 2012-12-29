package com.morlunk.mumbleclient.app;

import java.util.List;

import junit.framework.Assert;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;

import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import com.crittercism.app.Crittercism;
import com.morlunk.mumbleclient.Globals;
import com.morlunk.mumbleclient.R;
import com.morlunk.mumbleclient.app.db.DbAdapter;
import com.morlunk.mumbleclient.app.db.Server;
import com.morlunk.mumbleclient.jni.NativeAudio;
import com.morlunk.mumbleclient.service.BaseServiceObserver;
import com.morlunk.mumbleclient.service.MumbleService;

/**
 * Called whenever server info is changed.
 * @author morlunk
 *
 */
interface ServerInfoListener {
	public void serverInfoUpdated();
}

/**
 * The main server list activity.
 *
 * Shows a list of servers and allows connecting to these. Also provides
 * ways to start creating and editing servers.
 *
 * @author pcgod
 *
 */
public class ServerList extends ConnectedListActivity implements ServerInfoListener {
	private class ServerAdapter extends ArrayAdapter<Server> {
		private Context context;
		private List<Server> servers;

		public ServerAdapter(Context context, List<Server> servers) {
			super(context, android.R.id.text1, servers);
			this.context = context;
			this.servers = servers;
		}

		@Override
		public final int getCount() {
			return servers.size();
		}
		
		@Override
		public Server getItem(int position) {
			return servers.get(position);
		}
		
		@Override
		public long getItemId(int position) {
			return getItem(position).getId();
		}

		@Override
		public final View getView(
			final int position,
			final View v,
			final ViewGroup parent) {
			View view = v;
			
			if(v == null) {
				LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
				view = inflater.inflate(
					R.layout.server_list_row,
					null);
			}
			
			final Server server = getItem(position);

			TextView nameText = (TextView) view.findViewById(R.id.server_row_name);
			TextView userText = (TextView) view.findViewById(R.id.server_row_user);
			TextView addressText = (TextView) view.findViewById(R.id.server_row_address);

			if(server.getName().equals("")) {
				nameText.setText(server.getHost());
			} else {
				nameText.setText(server.getName());
			}
			
			userText.setText(server.getUsername());
			addressText.setText(server.getHost()+":"+server.getPort());
			
			Button connectButton = (Button) view.findViewById(R.id.server_row_connect);
			Button editButton = (Button) view.findViewById(R.id.server_row_edit);
			connectButton.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					connectServer(server.getId());
				}
			});
			editButton.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					editServer(server.getId());
				}
			});
			
			ImageButton deleteButton = (ImageButton) view.findViewById(R.id.server_row_delete);
			deleteButton.setOnClickListener(new OnClickListener() {
				
				@Override
				public void onClick(View v) {
					deleteServer(server.getId());
				}
			});

			return view;
		}
	}

	private class ServerServiceObserver extends BaseServiceObserver {
		@Override
		public void onConnectionStateChanged(final int state)
			throws RemoteException {
			checkConnectionState();
		}
	}
	
	private static final int ACTIVITY_CHANNEL_LIST = 1;

	private static final String STATE_WAIT_CONNECTION = "com.morlunk.mumbleclient.ServerList.WAIT_CONNECTION";

	private ServerServiceObserver mServiceObserver;
	private ListView listView;
	
	@Override
	public final boolean onCreateOptionsMenu(final Menu menu) {
		super.onCreateOptionsMenu(menu);
		getSupportMenuInflater().inflate(R.menu.activity_server_list, menu);
		return true;
	}
	
	@Override
	public boolean onMenuItemSelected(int featureId, MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menu_add_server_item:
			addServer();
			return true;
		case R.id.menu_preferences:
			final Intent prefs = new Intent(this, Preferences.class);
			startActivity(prefs);
			return true;
		default:
			return super.onMenuItemSelected(featureId, item);
		}
	}
	

	private void addServer() {
		ServerInfo infoDialog = new ServerInfo();
		infoDialog.show(getSupportFragmentManager(), "serverInfo");
	}

	/**
	 * Monitors the connection state after clicking a server entry.
	 */
	private final boolean checkConnectionState() {
		switch (mService.getConnectionState()) {
		case MumbleService.CONNECTION_STATE_CONNECTING:
		case MumbleService.CONNECTION_STATE_SYNCHRONIZING:
		case MumbleService.CONNECTION_STATE_CONNECTED:
			unregisterConnectionReceiver();
			final Intent i = new Intent(this, ChannelActivity.class);
			startActivityForResult(i, ACTIVITY_CHANNEL_LIST);
			return true;
		case MumbleService.CONNECTION_STATE_DISCONNECTED:
			// TODO: Error message checks.
			// This can be reached if the user leaves ServerList after clicking
			// server but before the connection intent reaches the service.
			// In this case the service connects and can be disconnected before
			// the connection state is checked again.
			Log.i(Globals.LOG_TAG, "ServerList: Disconnected");
			break;
		default:
			Assert.fail("Unknown connection state");
		}

		return false;
	}
	
	private void editServer(long id) {
		ServerInfo infoDialog = new ServerInfo();
		Bundle args = new Bundle();
		args.putLong("serverId", id);
		infoDialog.setArguments(args);
		infoDialog.show(getSupportFragmentManager(), "serverInfo");
	}
	
	private void deleteServer(long id) {
		DbAdapter adapter = new DbAdapter(this);
		adapter.open();
		adapter.deleteServer(id);
		adapter.close();
		fillList();
	}
	
	private void registerConnectionReceiver() {
		if (mServiceObserver != null) {
			return;
		}

		mServiceObserver = new ServerServiceObserver();

		if (mService != null) {
			mService.registerObserver(mServiceObserver);
		}
	}

	private void unregisterConnectionReceiver() {
		if (mServiceObserver == null) {
			return;
		}

		if (mService != null) {
			mService.unregisterObserver(mServiceObserver);
		}

		mServiceObserver = null;
	}

	/**
	 * Starts connecting to a server.
	 *
	 * @param id
	 */
	protected final void connectServer(final long id) {
		DbAdapter adapter = new DbAdapter(this);
		adapter.open();
		Server server = adapter.fetchServer(id);
		adapter.close();

		registerConnectionReceiver();
		
		final Intent connectionIntent = new Intent(this, MumbleService.class);
		connectionIntent.setAction(MumbleService.ACTION_CONNECT);
		connectionIntent.putExtra(MumbleService.EXTRA_SERVER, server);
		startService(connectionIntent);
	}

	@Override
	protected final void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		boolean debuggable =  ( 0 != ( getApplicationInfo().flags &= ApplicationInfo.FLAG_DEBUGGABLE ) );
		
		if(!debuggable) {
			Crittercism.init(getApplicationContext(), "50650bc62cd95250d3000004");
		} else {
			Log.i(Globals.LOG_TAG, "Crittercism disabled in debug build.");
		}
		
		// OPUS TEST CRAP
		long decoder = NativeAudio.opusDecoderCreate(48000, 1);
		Log.i(Globals.LOG_TAG, "Decoder pointer: "+decoder);
		NativeAudio.opusDecoderDestroy(decoder);
		
		setContentView(R.layout.main);
		
		listView = (ListView) findViewById(android.R.id.list);

		// Create the service observer. If such exists, onServiceBound will
		// register it.
		if (savedInstanceState != null) {
			mServiceObserver = new ServerServiceObserver();
		}

		fillList();
	}

	@Override
	protected void onDisconnected() {
		// Suppress the default disconnect behavior.
	}

	@Override
	protected void onPause() {
		unregisterConnectionReceiver();
		super.onPause();
	}
	
	/* (non-Javadoc)
	 * @see com.morlunk.mumbleclient.app.ConnectedListActivity#onResume()
	 */
	@Override
	protected void onResume() {
		super.onResume();
		
		if(mService != null && mService.getConnectionState() == MumbleService.CONNECTION_STATE_CONNECTED) {
			// If already connected, just jump to channel list.
			startActivity(new Intent(this, ChannelActivity.class));
		}
	}

	@Override
	protected void onSaveInstanceState(final Bundle outState) {
		super.onSaveInstanceState(outState);
		if (mServiceObserver != null) {
			outState.putBoolean(STATE_WAIT_CONNECTION, true);
		}
	}

	@Override
	protected void onServiceBound() {
		if (mServiceObserver != null) {
			if (!checkConnectionState()) {
				mService.registerObserver(mServiceObserver);
			}
		}
	}

	private void fillList() {
		DbAdapter dbAdapter = new DbAdapter(this);
		dbAdapter.open();
		listView.setAdapter(new ServerAdapter(this, dbAdapter.fetchAllServers()));
		dbAdapter.close();
	}

	@Override
	public void serverInfoUpdated() {
		fillList();
	}
}
