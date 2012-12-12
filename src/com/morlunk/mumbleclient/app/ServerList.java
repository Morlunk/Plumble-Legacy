package com.morlunk.mumbleclient.app;

import java.util.List;

import junit.framework.Assert;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

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
 * The main server list activity.
 *
 * Shows a list of servers and allows connecting to these. Also provides
 * ways to start creating and editing servers.
 *
 * @author pcgod
 *
 */
public class ServerList extends ConnectedListActivity {
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
					android.R.layout.simple_list_item_2,
					null);
			}
			
			Server server = getItem(position);

			TextView nameText = (TextView) view.findViewById(android.R.id.text1);
			TextView userText = (TextView) view.findViewById(android.R.id.text2);

			if(server.getName().equals("")) {
				nameText.setText(server.getHost() + ":" + server.getPort());
				userText.setText(server.getUsername());
			} else {
				nameText.setText(server.getName());
				userText.setText(server.getUsername() + "@" + server.getHost() + ":" +
								 server.getPort());
			}

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

	long serverToDeleteId = -1;
	DbAdapter dbAdapter;

	private static final int ACTIVITY_ADD_SERVER = 0;
	private static final int ACTIVITY_CHANNEL_LIST = 1;

	private static final int MENU_EDIT_SERVER = Menu.FIRST;
	private static final int MENU_DELETE_SERVER = Menu.FIRST + 1;
	private static final int MENU_CONNECT_SERVER = Menu.FIRST + 2;

	private static final String STATE_WAIT_CONNECTION = "com.morlunk.mumbleclient.ServerList.WAIT_CONNECTION";

	private ServerServiceObserver mServiceObserver;
	
	/* (non-Javadoc)
	 * @see android.app.Activity#onContextItemSelected(android.view.MenuItem)
	 */
	@Override
	public boolean onContextItemSelected(android.view.MenuItem item) {
		final AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
		switch (item.getItemId()) {
		case MENU_CONNECT_SERVER:
			onListItemClick(
				getListView(),
				getCurrentFocus(),
				info.position,
				getListAdapter().getItemId(info.position));
			return true;
		case MENU_EDIT_SERVER:
			editServer(getListAdapter().getItemId(info.position));
			return true;
		case MENU_DELETE_SERVER:
			serverToDeleteId = info.id;
			createDeleteServerDialog().show();
			return true;
		default:
			return super.onContextItemSelected(item);
		}
	}

	@Override
	public final void onCreateContextMenu(
		final ContextMenu menu,
		final View v,
		final ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);

		final int menuPosition = ((AdapterView.AdapterContextMenuInfo) menuInfo).position;
		final int serverId = (int) getListView().getItemIdAtPosition(
			menuPosition);
		
		Server server = dbAdapter.fetchServer(serverId);
		
		menu.setHeaderTitle(server.getName());

		menu.add(0, MENU_CONNECT_SERVER, 1, "Connect").setIcon(
			android.R.drawable.ic_menu_view);
		menu.add(0, MENU_EDIT_SERVER, 1, "Edit").setIcon(
			android.R.drawable.ic_menu_edit);
		menu.add(0, MENU_DELETE_SERVER, 1, "Delete").setIcon(
			android.R.drawable.ic_menu_delete);
	}

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
		final Intent i = new Intent(this, ServerInfo.class);
		startActivityForResult(i, ACTIVITY_ADD_SERVER);
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

	private Dialog createDeleteServerDialog() {
		final AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage(R.string.sureDeleteServer).setCancelable(
			false).setPositiveButton(
			"Yes",
			new DialogInterface.OnClickListener() {
				public void onClick(final DialogInterface dialog, final int id) {
					if (serverToDeleteId > 0) {
						dbAdapter.deleteServer(serverToDeleteId);
						serverToDeleteId = -1;
						fillList();
						Toast.makeText(
							ServerList.this,
							R.string.server_deleted,
							Toast.LENGTH_SHORT).show();
					}
				}
			}).setNegativeButton("No", new DialogInterface.OnClickListener() {
			public void onClick(final DialogInterface dialog, final int id) {
				dialog.cancel();
			}
		});

		return builder.create();
	}

	private void editServer(final long id) {
		final Intent i = new Intent(this, ServerInfo.class);
		i.putExtra("serverId", id);
		startActivityForResult(i, ACTIVITY_ADD_SERVER);
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
		Server server = dbAdapter.fetchServer(id);

		registerConnectionReceiver();

		// TODO make 'Server' parcelable and send that instead
		final Intent connectionIntent = new Intent(this, MumbleService.class);
		connectionIntent.setAction(MumbleService.ACTION_CONNECT);
		connectionIntent.putExtra(MumbleService.EXTRA_SERVER_ID, server.getId());
		connectionIntent.putExtra(MumbleService.EXTRA_HOST, server.getHost());
		connectionIntent.putExtra(MumbleService.EXTRA_PORT, server.getPort());
		connectionIntent.putExtra(MumbleService.EXTRA_USERNAME, server.getUsername());
		connectionIntent.putExtra(MumbleService.EXTRA_PASSWORD, server.getPassword());
		startService(connectionIntent);
	}

	@Override
	protected final void onActivityResult(
		final int requestCode,
		final int resultCode,
		final Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		fillList();
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
		
		registerForContextMenu(getListView());

		// Create the service observer. If such exists, onServiceBound will
		// register it.
		if (savedInstanceState != null) {
			mServiceObserver = new ServerServiceObserver();
		}

		dbAdapter = new DbAdapter(this);
		dbAdapter.open();

		fillList();
	}

	@Override
	protected final void onDestroy() {
		super.onDestroy();

		dbAdapter.close();
	}

	@Override
	protected void onDisconnected() {
		// Suppress the default disconnect behavior.
	}

	@Override
	protected final void onListItemClick(
		final ListView l,
		final View v,
		final int position,
		final long id) {
		super.onListItemClick(l, v, position, id);

		connectServer(id);
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
		setListAdapter(new ServerAdapter(this, dbAdapter.fetchAllServers()));
	}
}
