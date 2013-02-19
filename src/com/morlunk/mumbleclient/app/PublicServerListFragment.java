package com.morlunk.mumbleclient.app;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Build.VERSION;
import android.os.Bundle;
import android.util.Log;
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
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockFragment;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.morlunk.mumbleclient.Globals;
import com.morlunk.mumbleclient.R;
import com.morlunk.mumbleclient.app.db.PublicServer;
import com.morlunk.mumbleclient.service.MumbleService;

/**
 * Displays a list of public servers that can be connected to, sorted, and favourited.
 * @author morlunk
 *
 */
public class PublicServerListFragment extends SherlockFragment {
	
	public static final int MAX_ACTIVE_PINGS = 50;
	
	private ServerConnectHandler connectHandler;
	private GridView serverGrid;
	private ProgressBar serverProgress;
	private PublicServerAdapter serverAdapter;
	private int activePingCount = 0;
	
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
		serverProgress = (ProgressBar) view.findViewById(R.id.serverProgress);
		serverProgress.setVisibility(View.VISIBLE);
		return view;
	}
	
	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		super.onCreateOptionsMenu(menu, inflater);
		inflater.inflate(R.menu.fragment_public_server_list, menu);
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if(item.getItemId() == R.id.menu_sort_server_item) {
			showSortDialog();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}
	
	public void setServers(List<PublicServer> servers) {
		serverProgress.setVisibility(View.GONE);
		serverAdapter = new PublicServerAdapter(getActivity(), servers);
		serverGrid.setAdapter(serverAdapter);
	}
	
	public boolean isFilled() {
		return serverAdapter != null;
	}
	
	private void showSortDialog() {
		AlertDialog.Builder alertBuilder = new AlertDialog.Builder(getActivity());
		alertBuilder.setTitle(R.string.sortBy);
		alertBuilder.setItems(new String[] { getString(R.string.name), getString(R.string.country)}, new SortClickListener());
		alertBuilder.show();
	}
	
	private class PublicServerAdapter extends ArrayAdapter<PublicServer> {
		private Map<PublicServer, ServerInfoResponse> infoResponses = new HashMap<PublicServer, ServerInfoResponse>();
		
		public PublicServerAdapter(Context context, List<PublicServer> servers) {
			super(context, android.R.id.text1, servers);
		}

		@SuppressLint("NewApi")
		@Override
		public final View getView(
			final int position,
			final View v,
			final ViewGroup parent) {
			View view = v;
			
			if(v == null) {
				LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
				view = inflater.inflate(
					R.layout.public_server_list_row,
					null);
			}
			
			final PublicServer server = getItem(position);
			
			TextView nameText = (TextView) view.findViewById(R.id.server_row_name);
			TextView addressText = (TextView) view.findViewById(R.id.server_row_address);
			
			if(server.getName().equals("")) {
				nameText.setText(server.getHost());
			} else {
				nameText.setText(server.getName());
			}
			
			addressText.setText(server.getHost()+":"+server.getPort());
			
			TextView locationText = (TextView) view.findViewById(R.id.server_row_location);
			locationText.setText(server.getCountry());
			
			Button button1 = (Button) view.findViewById(R.id.server_row_button1);
			
			button1.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					connectHandler.connectToPublicServer(server);
				}
			});
			
			// Ping server if available
			if(!infoResponses.containsKey(server) && activePingCount < MAX_ACTIVE_PINGS) {
				activePingCount++;
				final View serverView = view; // Create final instance of view for use in asynctask
				ServerInfoTask task = new ServerInfoTask() {
					protected void onPostExecute(ServerInfoResponse result) {
						super.onPostExecute(result);
						infoResponses.put(server, result);
						if(serverView != null && serverView.isShown())
							updateInfoResponseView(serverView, server);
						activePingCount--;
						Log.d(Globals.LOG_TAG, "DEBUG: Servers remaining in queue: "+activePingCount);
					};
				};
				
				// Execute on parallel threads if API >= 11. RACE CAR THREADING, WOOOOOOOOOOOOOOOOOOOOOO
				if(VERSION.SDK_INT >= 11)
					task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, server);
				else
					task.execute(server);
			}
			
			ImageButton favoriteButton = (ImageButton)view.findViewById(R.id.server_row_favorite);
			
			favoriteButton.setOnClickListener(new OnClickListener() {

				@Override
				public void onClick(View v) {
					AlertDialog.Builder alertBuilder = new AlertDialog.Builder(getContext());
					
					// Allow username entry
					final EditText usernameField = new EditText(getContext());
					usernameField.setHint(R.string.serverUsername);
					alertBuilder.setView(usernameField);

					alertBuilder.setTitle(R.string.addFavorite);
					
					alertBuilder.setPositiveButton(R.string.add, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							MumbleService.getCurrentService().getDatabaseAdapter().createServer(server.getName(), server.getHost(), server.getPort(), usernameField.getText().toString(), "");
							connectHandler.publicServerFavourited();
						}
					});
					
					alertBuilder.show();
				}
				
			});
			
			updateInfoResponseView(view, server);
			
			return view;
		}
		
		private void updateInfoResponseView(View view, PublicServer server) {
			ServerInfoResponse infoResponse = infoResponses.get(server);
			// If there is a null value for the server info (rather than none at all), the request must have failed.
			boolean requestExists = infoResponse != null;
			boolean requestFailure = infoResponse != null && infoResponse.isDummy();
			
			TextView serverVersionText = (TextView) view.findViewById(R.id.server_row_version_status);
			TextView serverUsersText = (TextView) view.findViewById(R.id.server_row_usercount);
			ProgressBar serverInfoProgressBar = (ProgressBar) view.findViewById(R.id.server_row_ping_progress);
			
			serverVersionText.setVisibility(!requestFailure && !requestExists ? View.INVISIBLE : View.VISIBLE);
			serverUsersText.setVisibility(!requestFailure && !requestExists ? View.INVISIBLE : View.VISIBLE);
			serverInfoProgressBar.setVisibility(!requestExists ? View.VISIBLE : View.INVISIBLE);
			
			if(infoResponse != null && !requestFailure) {
				serverVersionText.setText(getResources().getString(R.string.online)+" ("+infoResponse.getVersionString()+")");
				serverUsersText.setText(infoResponse.getCurrentUsers()+"/"+infoResponse.getMaximumUsers());
			} else if(requestFailure) {
				serverVersionText.setText(R.string.offline);
				serverUsersText.setText("");
			} else {
				serverVersionText.setText(R.string.noServerInfo);
			}
		}
	}
	
	private class SortClickListener implements DialogInterface.OnClickListener {

		private static final int SORT_NAME = 0;
		private static final int SORT_COUNTRY = 1;
		
		private Comparator<PublicServer> nameComparator = new Comparator<PublicServer>() {
			@Override
			public int compare(PublicServer lhs, PublicServer rhs) {
				return lhs.getName().compareTo(rhs.getName());
			}
		};

		private Comparator<PublicServer> countryComparator = new Comparator<PublicServer>() {
			@Override
			public int compare(PublicServer lhs, PublicServer rhs) {
				return lhs.getCountry().compareTo(rhs.getCountry());
			}
		};
		
		
		@Override
		public void onClick(DialogInterface dialog, int which) {
			ArrayAdapter<PublicServer> arrayAdapter = serverAdapter;
			if(which == SORT_NAME) {
				arrayAdapter.sort(nameComparator);
			} else if(which == SORT_COUNTRY) {
				arrayAdapter.sort(countryComparator);
			}
		}
	}
}
