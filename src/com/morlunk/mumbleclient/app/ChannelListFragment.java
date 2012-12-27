package com.morlunk.mumbleclient.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.sf.mumble.MumbleProto.RequestBlob;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockFragment;
import com.morlunk.mumbleclient.R;
import com.morlunk.mumbleclient.Settings;
import com.morlunk.mumbleclient.service.MumbleProtocol.MessageType;
import com.morlunk.mumbleclient.service.MumbleService;
import com.morlunk.mumbleclient.service.audio.AudioOutputHost;
import com.morlunk.mumbleclient.service.model.User;

/**
 * The main connection view.
 *
 * The state of this activity depends closely on the state of the underlying
 * MumbleService. When the activity is started it can't really do anything else
 * than initialize its member variables until it has acquired a reference to the
 * MumbleService.
 *
 * Once the MumbleService reference has been acquired the activity is in one of
 * the three states:
 * <dl>
 * <dt>Connecting to server
 * <dd>MumbleService has just been started and ChannelList should wait until the
 * connection has been established. In this case the ChannelList should be very
 * careful as it doesn't have a visible channel and the Service doesn't have a
 * current channel.
 *
 * <dt>Connected to server
 * <dd>When the Activity is resumed during an established Mumble connection it
 * has connection immediately available and is free to act freely.
 *
 * <dt>Disconnecting or Disconnected
 * <dd>If the ChannelList is resumed after the Service has been disconnected the
 * List should exit immediately.
 * </dl>
 *
 * NOTE: Service enters 'Connected' state when it has received and processed
 * server sync message. This means that at this point the service should be
 * fully initialized.
 *
 * And just so the state wouldn't be too easy the connection can be cancelled.
 * Disconnecting the service is practically synchronous operation. Intents
 * broadcast by the Service aren't though. This means that after the ChannelList
 * disconnects the service it might still have some unprocessed intents queued
 * in a queue. For this reason all intents that require active connection must
 * take care to check that the connection is still alive.
 *
 * @author pcgod, Rantanen
 *
 */
public class ChannelListFragment extends SherlockFragment implements OnItemClickListener {	
	
	/**
	 * The parent activity MUST implement ChannelProvider. An exception will be thrown otherwise.
	 */
	private ChannelProvider channelProvider;
	
	protected ListView channelUsersList;
	private UserListAdapter usersAdapter;
	private TextView noUsersText;

	private User selectedUser;
	
	/**
	 * Updates the users display with the data from the channelProvider.
	 */
	public void updateChannel() {
		// We need to make sure the fragment has been attached and is shown before updating the users.
		usersAdapter.setVisibleChannel(channelProvider.getChannel().id);
		usersAdapter.setUsers(channelProvider.getChannelUsers());
		usersAdapter.notifyDataSetChanged();
	}
	
	/**
	 * Updates the user specified in the users adapter.
	 * @param user
	 */
	public void updateUser(User user) {
		usersAdapter.refreshUser(user);
	}
	
	/**
	 * Removes the user from the channel list.
	 * @param user
	 */
	public void removeUser(User user) {
		usersAdapter.removeUser(user.session);
	}
	
	/* (non-Javadoc)
	 * @see android.support.v4.app.Fragment#onCreateView(android.view.LayoutInflater, android.view.ViewGroup, android.os.Bundle)
	 */
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.channel_list, container, false);

		// Get the UI views
		channelUsersList = (ListView) view.findViewById(R.id.channelUsers);
		channelUsersList.setOnItemClickListener(this);
		noUsersText = (TextView) view.findViewById(R.id.noUsersText);
		
		return view;
	}
	
	/* (non-Javadoc)
	 * @see android.support.v4.app.Fragment#onAttach(android.app.Activity)
	 */
	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		
		try {
			channelProvider = (ChannelProvider)activity;
		} catch (ClassCastException e) {
			throw new ClassCastException(activity.toString()+" must implement ChannelProvider!");
		}
	}
	
	/* (non-Javadoc)
	 * @see android.support.v4.app.Fragment#onActivityCreated(android.os.Bundle)
	 */
	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		usersAdapter = new UserListAdapter(getActivity(), null);
		channelUsersList.setAdapter(usersAdapter);
		registerForContextMenu(channelUsersList);
		channelUsersList.setEmptyView(noUsersText);
	}
	
	/* (non-Javadoc)
	 * @see android.support.v4.app.Fragment#onCreateContextMenu(android.view.ContextMenu, android.view.View, android.view.ContextMenu.ContextMenuInfo)
	 */
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
			ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		getActivity().getMenuInflater().inflate(R.menu.channel_list_context, menu);
	}
	
	public boolean onContextItemSelected(MenuItem item) {
		AdapterView.AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
		User user = (User) usersAdapter.getItem(info.position);
		
		switch (item.getItemId()) {
		case R.id.menu_local_mute_item:
			user.localMuted = !user.localMuted;
			usersAdapter.notifyDataSetChanged();
			return true;
		}
		return false;
	}
	
	public void setChatTarget(User chatTarget) {
		selectedUser = chatTarget;
		if(usersAdapter != null)
			usersAdapter.notifyDataSetChanged();
	}

	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
		User listSelectedUser = (User) parent.getItemAtPosition(position);
		User newSelectedUser = selectedUser == listSelectedUser ? null : listSelectedUser; // Unset if is already selected user
		setChatTarget(newSelectedUser);
		channelProvider.setChatTarget(newSelectedUser);
	};
	
	class UserListAdapter extends BaseAdapter {
		Comparator<User> userComparator = new Comparator<User>() {
			@Override
			public int compare(final User object1, final User object2) {
				return object1.name.toLowerCase(Locale.ENGLISH)
						.compareTo(object2.name.toLowerCase(Locale.ENGLISH));
			}
		};

		/**
		 * Tracks the current row elements of a user.
		 */
		private final Context context;
		private final Map<Integer, User> users = new HashMap<Integer, User>();
		private final Map<Integer, String> visibleUserNames = new HashMap<Integer, String>();
		private final List<User> visibleUserList = new ArrayList<User>();
		private int visibleChannel = -1;

		private final Runnable visibleUsersChangedCallback;

		private final Drawable chatDrawable; // Changes depending on theme.
		
		int totalViews = 0;

		public UserListAdapter(
			final Context context,
			final Runnable visibleUsersChangedCallback) {
			this.context = context;
			this.visibleUsersChangedCallback = visibleUsersChangedCallback;
			
			// Fetch theme dependent icon
			Settings settings = new Settings(context);
			chatDrawable = getResources().getDrawable(settings.getTheme().equals(Settings.ARRAY_THEME_LIGHTDARK) ? R.drawable.ic_action_chat_light : R.drawable.ic_action_chat_dark);
		}

		@Override
		public int getCount() {
			return this.visibleUserList.size();
		}

		@Override
		public Object getItem(final int arg0) {
			return this.visibleUserList.get(arg0);
		}

		@Override
		public long getItemId(final int arg0) {
			return this.visibleUserList.get(arg0).session;
		}

		/**
		 * Get a View for displaying data in the specified position.
		 *
		 * Since UserListAdapter can refresh individual users it is important that
		 * it maintains a 1:1 relationship between user, View and RowElement.
		 * <p>
		 * The logical trivial relationships are listed below. These relationships
		 * cannot be 1:* relationships in any case.
		 * <ul>
		 * <li>User id maps to one RowElement through userElements.
		 * <li>RowElement has one View as its member.
		 * <li>View has a single tag that is set to the user id.
		 * </ul>
		 * The relationships that must be enforced are listed below. These
		 * relationships may become 1:* relationships.
		 * <ul>
		 * <li>Several users may own a single RowElement in userElements. (I)
		 * <li>Several RowElements may refer to the same View. (II)
		 * <li>Several Views may be tagged with same user id. (III)
		 * </ul>
		 * The enforcing for the latter three rules is made either when using an old
		 * view that is currently tied to a user (a) or when creating a new view for
		 * a user that is currently tied to another view (b).
		 */
		@Override
		public final View getView(final int position, View v, final ViewGroup parent) {
			// All views are the same.
			if (v == null) {
				final LayoutInflater inflater = (LayoutInflater) this.context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
				v = inflater.inflate(R.layout.channel_user_row, null);
			}

			// Tie the view to the current user.
			final User u = this.visibleUserList.get(position);
			v.setTag(u.session);

			refreshElements(v, u);
			return v;
		}

		@Override
		public boolean hasStableIds() {
			return true;
		}

		public final boolean hasUser(final User user) {
			return visibleUserNames.containsValue(user.session);
		}

		@Override
		public void notifyDataSetChanged() {
			repopulateUsers();
			super.notifyDataSetChanged();
		}

		public final void refreshUser(final User user) {
			final boolean oldVisible = visibleUserNames.get(user.session) != null;
			final boolean newVisible = user.getChannel().id == visibleChannel;

			users.put(user.session, user);

			int oldLocation = -1;
			if (oldVisible) {
				for (int i = 0; i < visibleUserList.size(); i++) {
					if (visibleUserList.get(i).session == user.session) {
						oldLocation = i;
						break;
					}
				}
			}

			int newLocation = 0;
			if (newVisible) {
				newLocation = Collections.binarySearch(
					visibleUserList,
					user,
					userComparator);
			}

			int newInsertion = (newLocation < 0) ? (-newLocation - 1) : newLocation;

			if (oldVisible && newVisible) {
				// If the new would be inserted next to the old one, replace the old
				// as it should be removed anyway.
				if (oldLocation == newInsertion || oldLocation == newInsertion + 1) {
					setVisibleUser(oldLocation, user);

					// Since we just replaced a user we can update view without
					// full refresh.
					refreshUserAtPosition(oldLocation, user);
				} else {
					removeVisibleUser(oldLocation);

					// If the old one was removed before the new one, move the
					// new index to the left
					if (oldLocation < newInsertion) {
						newInsertion--;
					}

					addVisibleUser(newInsertion, user);

					// Perform full refresh as order changed.
					super.notifyDataSetChanged();
				}
			} else if (oldVisible) {
				removeVisibleUser(oldLocation);
				super.notifyDataSetChanged();
			} else if (newVisible) {
				addVisibleUser(newInsertion, user);
				super.notifyDataSetChanged();
			}

			if ((oldVisible || newVisible) && visibleUsersChangedCallback != null) {
				visibleUsersChangedCallback.run();
			}
		}

		public void removeUser(final int id) {
			final User user = users.remove(id);

			if (user != null && user.getChannel().id == visibleChannel) {
				final int userLocation = Collections.binarySearch(
					visibleUserList,
					user,
					userComparator);

				removeVisibleUser(userLocation);
				super.notifyDataSetChanged();

				if (visibleUsersChangedCallback != null) {
					visibleUsersChangedCallback.run();
				}
			}
		}

		public void setUsers(final List<User> users) {
			this.users.clear();
			for (final User user : users) {
				this.users.put(user.session, user);
			}
			repopulateUsers();
		}

		public void setVisibleChannel(final int channelId) {
			visibleChannel = channelId;
			repopulateUsers();
		}

		private void addVisibleUser(final int position, final User user) {
			visibleUserList.add(position, user);
			visibleUserNames.put(user.session, user.name);
		}

		private void addVisibleUser(final User user) {
			visibleUserList.add(user);
			visibleUserNames.put(user.session, user.name);
		}

		private final void refreshElements(final View view, final User user) {
			// If this view has been used for another user already, don't update
			// it with the information from this user.
			if ((Integer) view.getTag() != user.session) {
				return;
			}

			final TextView name = (TextView) view.findViewById(R.id.userRowName);
			final ImageView state = (ImageView) view.findViewById(R.id.userRowState);
			final ImageView comment = (ImageView) view.findViewById(R.id.commentState);
			final ImageView localMute = (ImageView) view.findViewById(R.id.localMuteState);
			final ImageView chatActive = (ImageView) view.findViewById(R.id.activeChatState);
			
			name.setText(user.name);

			switch (user.userState) {
			case User.USERSTATE_DEAFENED:
				state.setImageResource(R.drawable.ic_deafened);
				break;
			case User.USERSTATE_MUTED:
				state.setImageResource(R.drawable.ic_muted);
				break;
			default:
				if (user.talkingState == AudioOutputHost.STATE_TALKING) {
					state.setImageResource(R.drawable.ic_talking_on);
				} else {
					state.setImageResource(R.drawable.ic_talking_off);
				}
			}
			
			localMute.setVisibility(user.localMuted ? View.VISIBLE : View.GONE);
			
			comment.setVisibility(user.comment != null || user.commentHash != null ? View.VISIBLE : View.GONE);
			comment.setOnClickListener(new OnClickListener() {
				
				@Override
				public void onClick(View v) {
					AlertDialog.Builder builder = new AlertDialog.Builder(context);
					builder.setTitle("Comment");
					builder.setPositiveButton("Close", null);
					final WebView webView = new WebView(context);
					webView.loadDataWithBaseURL("", "<center>Retrieving...</center>", "text/html", "utf-8", "");
					builder.setView(webView);
					
					final AlertDialog dialog = builder.show();
					
					if(user.comment != null) {
						webView.loadDataWithBaseURL("", user.comment, "text/html", "utf-8", "");
					} else if(user.commentHash != null) {
						// Retrieve comment from blob
						final RequestBlob.Builder blobBuilder = RequestBlob.newBuilder();
						blobBuilder.addSessionComment(user.session);
						
						new AsyncTask<Void, Void, Void>() {
							@Override
							protected Void doInBackground(Void... params) {
								MumbleService.getCurrentService().sendTcpMessage(MessageType.RequestBlob, blobBuilder);
								// TODO fix. This is messy, we're polling until we get a comment response.
								while(user.comment == null && dialog.isShowing()) {
									try {
										Thread.sleep(100);
									} catch (InterruptedException e) {
										e.printStackTrace();
									}
								}
								return null;
							}
							
							protected void onPostExecute(Void result) {
								webView.loadDataWithBaseURL("", user.comment, "text/html", "utf-8", "");
							};
						}.execute();
					}
				}
			});
			
			chatActive.setImageDrawable(chatDrawable);
			chatActive.setVisibility(user.equals(selectedUser) ? View.VISIBLE : View.GONE);
			
			view.invalidate();
		}

		private final void refreshUserAtPosition(final int position, final User user) {
			final int firstPosition = channelUsersList.getFirstVisiblePosition();
			if (firstPosition <= position) {
				// getChildAt gets the Nth visible item inside the stupidList.
				// This is documented in Google I/O. :P
				// http://www.youtube.com/watch?v=wDBM6wVEO70#t=52m30s
				final View v = channelUsersList.getChildAt(position - firstPosition);

				if (v != null) {
					refreshElements(v, user);
				}
			}
		}

		private void removeVisibleUser(final int position) {
			final User u = visibleUserList.remove(position);
			visibleUserNames.remove(u.session);
		}

		private void repopulateUsers() {
			visibleUserList.clear();
			visibleUserNames.clear();
			for (final User user : users.values()) {
				if (user.getChannel().id == visibleChannel) {
					addVisibleUser(user);
				}
			}

			Collections.sort(visibleUserList, userComparator);

			if (visibleUsersChangedCallback != null) {
				visibleUsersChangedCallback.run();
			}

			super.notifyDataSetChanged();
		}

		private void setVisibleUser(final int position, final User user) {
			visibleUserList.set(position, user);
			visibleUserNames.put(user.session, user.name);
		}
	}
}
