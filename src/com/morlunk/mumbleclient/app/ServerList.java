package com.morlunk.mumbleclient.app;

import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import junit.framework.Assert;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.database.DataSetObserver;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;
import android.util.Xml;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.SpinnerAdapter;
import android.widget.TextView;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.ActionBar.OnNavigationListener;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import com.crittercism.app.Crittercism;
import com.morlunk.mumbleclient.Globals;
import com.morlunk.mumbleclient.R;
import com.morlunk.mumbleclient.app.db.PublicServer;
import com.morlunk.mumbleclient.app.db.Server;
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

		public ServerAdapter(Context context, List<Server> servers) {
			super(context, android.R.id.text1, servers);
			this.context = context;
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
			
			ServerInfoResponse infoResponse = infoResponses.get(server.getId());
			// If there is a null value for the server info (rather than none at all), the request must have failed.
			boolean requestExists = infoResponses.containsKey(server.getId());
			boolean requestFailure = requestExists && infoResponse == null;

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
			
			Button button1 = (Button) view.findViewById(R.id.server_row_button1);
			Button button2 = (Button) view.findViewById(R.id.server_row_button2);
			
			button1.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					connectServer(server.getId());
				}
			});
			button2.setOnClickListener(new OnClickListener() {
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
			
			TextView serverVersionText = (TextView) view.findViewById(R.id.server_row_version_status);
			TextView serverUsersText = (TextView) view.findViewById(R.id.server_row_usercount);
			ProgressBar serverInfoProgressBar = (ProgressBar) view.findViewById(R.id.server_row_ping_progress);
			
			serverVersionText.setVisibility(!requestExists ? View.INVISIBLE : View.VISIBLE);
			serverUsersText.setVisibility(!requestExists ? View.INVISIBLE : View.VISIBLE);
			serverInfoProgressBar.setVisibility(!requestExists ? View.VISIBLE : View.INVISIBLE);

			if(infoResponse != null) {
				serverVersionText.setText(getResources().getString(R.string.online)+" ("+infoResponse.getVersionString()+")");
				serverUsersText.setText(infoResponse.getCurrentUsers()+"/"+infoResponse.getMaximumUsers());
			} else if(requestFailure) {
				serverVersionText.setText(R.string.offline);
				serverUsersText.setText("");
			}
			
			return view;
		}
	}
	
	private class PublicServerAdapter extends ArrayAdapter<PublicServer> {
		private Context context;

		public PublicServerAdapter(Context context, List<PublicServer> servers) {
			super(context, android.R.id.text1, servers);
			this.context = context;
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
					R.layout.public_server_list_row,
					null);
			}
			
			final PublicServer server = getItem(position);
			
			ServerInfoResponse infoResponse = infoResponses.get(server.getId());
			// If there is a null value for the server info (rather than none at all), the request must have failed.
			boolean requestExists = infoResponses.containsKey(server.getId());
			boolean requestFailure = requestExists && infoResponse == null;
			
			if(!requestExists) {
				pingServerInfo(server);
			}
			
			TextView nameText = (TextView) view.findViewById(R.id.server_row_name);
			TextView addressText = (TextView) view.findViewById(R.id.server_row_address);
			
			if(server.getName().equals("")) {
				nameText.setText(server.getHost());
			} else {
				nameText.setText(server.getName());
			}
			
			addressText.setText(server.getHost()+":"+server.getPort());
			
			Button button1 = (Button) view.findViewById(R.id.server_row_button1);
			
			button1.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					connectPublicServer(server);
				}
			});
			
			TextView serverVersionText = (TextView) view.findViewById(R.id.server_row_version_status);
			TextView serverUsersText = (TextView) view.findViewById(R.id.server_row_usercount);
			ProgressBar serverInfoProgressBar = (ProgressBar) view.findViewById(R.id.server_row_ping_progress);
			
			serverVersionText.setVisibility(!requestExists ? View.INVISIBLE : View.VISIBLE);
			serverUsersText.setVisibility(!requestExists ? View.INVISIBLE : View.VISIBLE);
			serverInfoProgressBar.setVisibility(!requestExists ? View.VISIBLE : View.INVISIBLE);
			
			
			if(infoResponse != null) {
				serverVersionText.setText(getResources().getString(R.string.online)+" ("+infoResponse.getVersionString()+")");
				serverUsersText.setText(infoResponse.getCurrentUsers()+"/"+infoResponse.getMaximumUsers());
			} else if(requestFailure) {
				serverVersionText.setText(R.string.offline);
				serverUsersText.setText("");
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
	
	private class ServerInfoTask extends AsyncTask<Server, Void, ServerInfoResponse> {
		
		private Server server;
		
		@Override
		protected ServerInfoResponse doInBackground(Server... params) {
			server = params[0];
			try {
				InetAddress host = InetAddress.getByName(server.getHost());
				
				// Create ping message
				ByteBuffer buffer = ByteBuffer.allocate(12);
				buffer.putInt(0); // Request type
				buffer.putLong((long)server.getId()); // Identifier
				DatagramPacket requestPacket = new DatagramPacket(buffer.array(), 12, host, server.getPort());
				
				// Send packet and wait for response
				DatagramSocket socket = new DatagramSocket();
				socket.setSoTimeout(1000);
				socket.setReceiveBufferSize(1024);
				socket.send(requestPacket);
				
				byte[] responseBuffer = new byte[24];
				DatagramPacket responsePacket = new DatagramPacket(responseBuffer, responseBuffer.length);
				try {
					socket.receive(responsePacket);
				} catch (SocketTimeoutException e) {
					return null;
				}
				
				ServerInfoResponse response = new ServerInfoResponse(responseBuffer);
								
				Log.i(Globals.LOG_TAG, "DEBUG: Server version: "+response.getVersionString()+"\nUsers: "+response.getCurrentUsers()+"/"+response.getMaximumUsers());
				
				return response;
				
			} catch (UnknownHostException e) {
				e.printStackTrace();
			} catch (SocketException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			return null;
		}
		
		@Override
		protected void onPostExecute(ServerInfoResponse result) {
			super.onPostExecute(result);
			
			infoResponses.put(server.getId(), result);
			serverAdapter.notifyDataSetChanged();
		}
		
	}

	private static final String STATE_WAIT_CONNECTION = "com.morlunk.mumbleclient.ServerList.WAIT_CONNECTION";
	
	private ServerServiceObserver mServiceObserver;
	private GridView gridView;
	private ServerAdapter serverAdapter;
	@SuppressLint("UseSparseArrays") // We use Map instead of SparseArray so we can contain null values for keys.
	private Map<Integer, ServerInfoResponse> infoResponses = new HashMap<Integer, ServerInfoResponse>();
	
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
			startActivity(i);
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
	
	private void deleteServer(final long id) {
		AlertDialog.Builder alertBuilder = new AlertDialog.Builder(this);
		alertBuilder.setMessage(R.string.sureDeleteServer);
		alertBuilder.setPositiveButton(R.string.delete, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				mService.getDatabaseAdapter().deleteServer(id);
				fillFavoritesList();
			}
		});
		alertBuilder.setNegativeButton(android.R.string.cancel, null);
		alertBuilder.show();
	}
	
	/**
	 * Pings the passed host to retrieve server information.
	 * @see ServerInfoResponse
	 * @param server The server to ping.
	 */
	private void pingServerInfo(Server server) {
		new ServerInfoTask().execute(server);
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
		Server server = mService.getDatabaseAdapter().fetchServer(id);

		registerConnectionReceiver();
		
		final Intent connectionIntent = new Intent(this, MumbleService.class);
		connectionIntent.setAction(MumbleService.ACTION_CONNECT);
		connectionIntent.putExtra(MumbleService.EXTRA_SERVER, server);
		startService(connectionIntent);
	}

	/**
	 * Starts connecting to a public server.
	 *
	 * @param id
	 */
	protected final void connectPublicServer(final PublicServer server) {
		
		AlertDialog.Builder alertBuilder = new AlertDialog.Builder(this);
		
		// Allow username entry
		final EditText usernameField = new EditText(this);
		usernameField.setHint(R.string.serverPassword);
		alertBuilder.setView(usernameField);

		alertBuilder.setTitle(R.string.serverUsername);
		
		alertBuilder.setPositiveButton(R.string.retry, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				PublicServer newServer = server;
				newServer.setUsername(usernameField.getText().toString());
				registerConnectionReceiver();
				Intent connectionIntent = new Intent(ServerList.this, MumbleService.class);
				connectionIntent.setAction(MumbleService.ACTION_CONNECT);
				connectionIntent.putExtra(MumbleService.EXTRA_SERVER, server);
				startService(connectionIntent);
			}
		});
		
		alertBuilder.show();
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
		
		setContentView(R.layout.main);
		
		getSupportActionBar().setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
		getSupportActionBar().setListNavigationCallbacks(new ServerSpinnerAdapter(), new OnNavigationListener() {
			
			@Override
			public boolean onNavigationItemSelected(int itemPosition, long itemId) {
				switch (itemPosition) {
				case 0:
					// Favorites
					fillFavoritesList();
					return true;
				case 1:
					// LAN
					return true;
				case 2:
					// Public
					fillPublicList();
					return true;
				}
				return false;
			}
		});
		getSupportActionBar().setDisplayShowTitleEnabled(false);
		
		gridView = (GridView) findViewById(R.id.serverGrid);

		// Create the service observer. If such exists, onServiceBound will
		// register it.
		if (savedInstanceState != null) {
			mServiceObserver = new ServerServiceObserver();
		}
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
		fillFavoritesList();
	}

	private void fillFavoritesList() {
		List<Server> servers = mService.getDatabaseAdapter().fetchAllServers();

		serverAdapter = new ServerAdapter(this, servers);
		gridView.setAdapter(serverAdapter);
		
		// Clear and reload server ping responses
		infoResponses.clear();
		for(Server server : servers) {
			pingServerInfo(server);
		}
	}
	
	private void fillPublicList() {
		new PublicServerFetchTask().execute();
	}

	@Override
	public void serverInfoUpdated() {
		fillFavoritesList();
	}
	
	class ServerSpinnerAdapter implements SpinnerAdapter {

		@Override
		public int getCount() {
			return 3;
		}

		@Override
		public Object getItem(int arg0) {
			switch (arg0) {
			case 0:
				return getString(R.string.server_list_title_favorite);
			case 1:
				return getString(R.string.server_list_title_lan);
			case 2:
				return getString(R.string.server_list_title_public_internet);
			default:
				return null;
			}
		}

		@Override
		public long getItemId(int arg0) {
			return 0;
		}

		@Override
		public int getItemViewType(int arg0) {
			return 0;
		}

		@Override
		public View getView(int arg0, View arg1, ViewGroup arg2) {
			if(arg1 == null) {
				arg1 = ((LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE)).inflate(R.layout.sherlock_spinner_dropdown_item, arg2, false);
			}
			
			String title = (String) getItem(arg0);
			
			TextView spinnerTitle = (TextView) arg1.findViewById(android.R.id.text1);
			spinnerTitle.setTextColor(getResources().getColor(android.R.color.primary_text_dark));
			spinnerTitle.setText(title);
			
			return arg1;
		}

		@Override
		public int getViewTypeCount() {
			return 1;
		}

		@Override
		public boolean hasStableIds() {
			return false;
		}

		@Override
		public boolean isEmpty() {
			return false;
		}

		@Override
		public void registerDataSetObserver(DataSetObserver arg0) {
			
		}

		@Override
		public void unregisterDataSetObserver(DataSetObserver arg0) {
			
		}

		@Override
		public View getDropDownView(int position, View convertView,
				ViewGroup parent) {
			return getView(position, convertView, parent);
		}
		
	}
	
	class PublicServerFetchTask extends AsyncTask<Void, Void, List<PublicServer>> {
		
		private static final String MUMBLE_PUBLIC_URL = "http://www.mumble.info/list2.cgi";

		@Override
		protected List<PublicServer> doInBackground(Void... params) {
			try {
				// Fetch XML from server
				URL url = new URL(MUMBLE_PUBLIC_URL);
				HttpURLConnection connection = (HttpURLConnection) url.openConnection();
				connection.setRequestMethod("GET");
				connection.addRequestProperty("version", Globals.PROTOCOL_VERSION_STRING);
				connection.connect();
				InputStream stream = connection.getInputStream();				
				
				XmlPullParser parser = Xml.newPullParser();
				parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
				parser.setInput(stream, "UTF-8");
				parser.nextTag();
				
				List<PublicServer> serverList = new ArrayList<PublicServer>();
				
				parser.require(XmlPullParser.START_TAG, null, "servers");
				while(parser.next() != XmlPullParser.END_TAG) {
			        if (parser.getEventType() != XmlPullParser.START_TAG) {
			            continue;
			        }
			        
			        serverList.add(readEntry(parser));
				}
				parser.require(XmlPullParser.END_TAG, null, "servers");
				
				return serverList;
			} catch (Exception e) {
				e.printStackTrace();
			}
			return null;
		}
		
		@Override
		protected void onPostExecute(List<PublicServer> result) {
			super.onPostExecute(result);
			
			if(result != null)
				gridView.setAdapter(new PublicServerAdapter(ServerList.this, result));
		}
		
		private PublicServer readEntry(XmlPullParser parser) throws XmlPullParserException, IOException {			
			String name = parser.getAttributeValue(null, "name");
			String ca = parser.getAttributeValue(null, "ca");
			String continentCode = parser.getAttributeValue(null, "continent_code");
			String country = parser.getAttributeValue(null, "country");
			String countryCode = parser.getAttributeValue(null, "country_code");
			String ip = parser.getAttributeValue(null, "ip");
			String port = parser.getAttributeValue(null, "port");
			String region = parser.getAttributeValue(null, "region");
			String url = parser.getAttributeValue(null, "url");
			
			// Generate unique ID
			int id = 100+parser.getLineNumber();
			
			parser.nextTag();
			
			PublicServer server = new PublicServer(id, name, ca, continentCode, country, countryCode, ip, Integer.parseInt(port), region, url);
			
			return server;
		}
		
	}
}
