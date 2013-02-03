package com.morlunk.mumbleclient.app;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockFragment;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.morlunk.mumbleclient.R;
import com.morlunk.mumbleclient.app.db.Server;
import com.morlunk.mumbleclient.service.MumbleService;

/**
 * Displays a list of servers, and allows the user to connect and edit them.
 * @author morlunk
 *
 */
public class ServerListFragment extends SherlockFragment {
	
	private ServerConnectHandler connectHandler;
	private GridView serverGrid;
	private ServerAdapter serverAdapter;
	private Map<Server, ServerInfoResponse> infoResponses = new HashMap<Server, ServerInfoResponse>();
	
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
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.fragment_server_list, container, false);
		serverGrid = (GridView) view.findViewById(R.id.serverGrid);
		return view;
	}
	
	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		super.onCreateOptionsMenu(menu, inflater);
		inflater.inflate(R.menu.fragment_server_list, menu);
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if(item.getItemId() == R.id.menu_add_server_item) {
			addServer();
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
	
	private void deleteServer(final Server server) {
		AlertDialog.Builder alertBuilder = new AlertDialog.Builder(getActivity());
		alertBuilder.setMessage(R.string.sureDeleteServer);
		alertBuilder.setPositiveButton(R.string.delete, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				MumbleService.getCurrentService().getDatabaseAdapter().deleteServer(server.getId());
				serverAdapter.remove(server);
			}
		});
		alertBuilder.setNegativeButton(android.R.string.cancel, null);
		alertBuilder.show();
	}
	
	public void setServers(List<Server> servers) {
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
			
			Button button1 = (Button) view.findViewById(R.id.server_row_button1);
			Button button2 = (Button) view.findViewById(R.id.server_row_button2);
			
			button1.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					connectHandler.connectToServer(server);
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
					deleteServer(server);
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
}
