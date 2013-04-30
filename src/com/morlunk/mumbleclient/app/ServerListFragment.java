package com.morlunk.mumbleclient.app;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build.VERSION;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.PopupMenu.OnMenuItemClickListener;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockFragment;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.github.espiandev.showcaseview.ShowcaseView;
import com.github.espiandev.showcaseview.ShowcaseView.ConfigOptions;
import com.morlunk.mumbleclient.Globals;
import com.morlunk.mumbleclient.R;
import com.morlunk.mumbleclient.app.db.DbAdapter;
import com.morlunk.mumbleclient.app.db.Server;

/**
 * Displays a list of servers, and allows the user to connect and edit them.
 * @author morlunk
 *
 */
public class ServerListFragment extends SherlockFragment implements OnItemClickListener {
	
	private ServerConnectHandler connectHandler;
	private GridView serverGrid;
	private ServerAdapter serverAdapter;
	private Map<Server, ServerInfoResponse> infoResponses = new HashMap<Server, ServerInfoResponse>();
	
	// Showcases
	private ShowcaseView serverAddShowcaseView;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setHasOptionsMenu(true);
	}
	
	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		
		try {
			connectHandler = (ServerConnectHandler)activity;
		} catch (ClassCastException e) {
			throw new ClassCastException(activity.toString()+" must implement ServerConnectHandler!");
		}
	}
	
	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		updateServers();
	}
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.fragment_server_list, container, false);
		serverGrid = (GridView) view.findViewById(R.id.serverGrid);
		serverGrid.setOnItemClickListener(this);
		registerForContextMenu(serverGrid);
		return view;
	}
	
	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		super.onCreateOptionsMenu(menu, inflater);
		inflater.inflate(R.menu.fragment_server_list, menu);
		
		// Hint to add server
		if(serverAddShowcaseView == null) {
			ConfigOptions serverAddConfig = new ConfigOptions();
			serverAddConfig.shotType = ShowcaseView.TYPE_ONE_SHOT;
			serverAddConfig.showcaseId = Globals.SHOWCASE_SERVER_ADD;
			serverAddShowcaseView = ShowcaseView.insertShowcaseViewWithType(ShowcaseView.ITEM_ACTION_ITEM, R.id.menu_add_server_item, getActivity(), R.string.hint_server_add, R.string.hint_server_add_summary, serverAddConfig);
		}
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if(item.getItemId() == R.id.menu_add_server_item) {
			addServer();
			if(serverAddShowcaseView != null)
				serverAddShowcaseView.hide(); // Hide showcase when pressed.
			return true;
		}
		return super.onOptionsItemSelected(item);
	}
	
	private void addServer() {
		ServerInfo infoDialog = new ServerInfo();
		infoDialog.show(getFragmentManager(), "serverInfo");
	}
	
	private void editServer(long id) {
		ServerInfo infoDialog = new ServerInfo();
		Bundle args = new Bundle();
		args.putLong("serverId", id);
		infoDialog.setArguments(args);
		infoDialog.show(getFragmentManager(), "serverInfo");
	}
	
	private void shareServer(Server server) {
		// Build Mumble server URL
		String serverUrl = "mumble://"+server.getHost()+":"+server.getPort()+"/";
		
		Intent intent = new Intent();
		intent.setAction(Intent.ACTION_SEND);
		intent.putExtra(Intent.EXTRA_TEXT, getString(R.string.shareMessage, serverUrl));
		intent.setType("text/plain");
		startActivity(intent);
	}
	
	private void deleteServer(final Server server) {
		AlertDialog.Builder alertBuilder = new AlertDialog.Builder(getActivity());
		alertBuilder.setMessage(R.string.sureDeleteServer);
		alertBuilder.setPositiveButton(R.string.delete, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				DbAdapter adapter = new DbAdapter(getActivity());
				adapter.open();
				adapter.deleteServer(server.getId());
				adapter.close();
				serverAdapter.remove(server);
			}
		});
		alertBuilder.setNegativeButton(android.R.string.cancel, null);
		alertBuilder.show();
	}
	
	public void updateServers() {
		List<Server> servers = connectHandler.getServers();
		serverAdapter = new ServerAdapter(getActivity(), servers);
		serverGrid.setAdapter(serverAdapter);
		
		for(final Server server : servers) {
			new ServerInfoTask() {
				protected void onPostExecute(ServerInfoResponse result) {
					super.onPostExecute(result);
					infoResponses.put(server, result);
					serverAdapter.notifyDataSetChanged();
				};
			}.execute(server);
		}
	}
	
	@Override
	public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
		connectHandler.connectToServer(serverAdapter.getItem(arg2));
	}
	
	private class ServerAdapter extends ArrayAdapter<Server> {
		
		public ServerAdapter(Context context, List<Server> servers) {
			super(context, android.R.id.text1, servers);
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
				LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
				view = inflater.inflate(
					R.layout.server_list_row,
					null);
			}
			
			final Server server = getItem(position);
			
			ServerInfoResponse infoResponse = infoResponses.get(server);
			// If there is a null value for the server info (rather than none at all), the request must have failed.
			boolean requestExists = infoResponse != null;
			boolean requestFailure = infoResponse != null && infoResponse.isDummy();

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
			
			ImageView moreButton = (ImageView) view.findViewById(R.id.server_row_more);
			moreButton.setOnClickListener(new OnClickListener() {
				
				@SuppressLint("NewApi")
				@Override
				public void onClick(View v) {
					if(VERSION.SDK_INT >= 11) {
						PopupMenu popupMenu = new PopupMenu(getContext(), v);
						android.view.MenuInflater inflater = popupMenu.getMenuInflater();
						inflater.inflate(R.menu.popup_server_row, popupMenu.getMenu());
						ServerPopupMenuItemClickListener listener = new ServerPopupMenuItemClickListener(server);
						popupMenu.setOnMenuItemClickListener(listener);
						popupMenu.show();
					} else {
						// Create dialog because PopupMenu is API 11+
						AlertDialog.Builder optionsBuilder = new AlertDialog.Builder(getContext());
						optionsBuilder.setItems(new CharSequence[] { getString(R.string.edit), getString(R.string.share), getString(R.string.delete)},
								new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int which) {
								switch (which) {
								case 0:
									editServer(server.getId());
									break;
								case 1:
									shareServer(server);
									break;
								case 2:
									deleteServer(server);
									break;
								}
							}
						});
						optionsBuilder.show();
					}
				}
			});
			
			TextView serverVersionText = (TextView) view.findViewById(R.id.server_row_version_status);
			TextView serverUsersText = (TextView) view.findViewById(R.id.server_row_usercount);
			ProgressBar serverInfoProgressBar = (ProgressBar) view.findViewById(R.id.server_row_ping_progress);
			
			serverVersionText.setVisibility(!requestExists ? View.INVISIBLE : View.VISIBLE);
			serverUsersText.setVisibility(!requestExists ? View.INVISIBLE : View.VISIBLE);
			serverInfoProgressBar.setVisibility(!requestExists ? View.VISIBLE : View.INVISIBLE);

			if(infoResponse != null && !requestFailure) {
				serverVersionText.setText(getResources().getString(R.string.online)+" ("+infoResponse.getVersionString()+")");
				serverUsersText.setText(infoResponse.getCurrentUsers()+"/"+infoResponse.getMaximumUsers());
			} else if(requestFailure) {
				serverVersionText.setText(R.string.offline);
				serverUsersText.setText("");
			}
			
			return view;
		}
	}
	
	private class ServerPopupMenuItemClickListener implements OnMenuItemClickListener {
		
		private Server server;
		
		public ServerPopupMenuItemClickListener(Server server) {
			this.server = server;
		}
		
		public boolean onMenuItemClick(android.view.MenuItem item) {
			switch (item.getItemId()) {
			case R.id.menu_edit_item:
				editServer(server.getId());
				return true;
			case R.id.menu_share_item:
				shareServer(server);
				return true;
			case R.id.menu_delete_item:
				deleteServer(server);
				return true;
			}
			return false;
		}
	}
}
