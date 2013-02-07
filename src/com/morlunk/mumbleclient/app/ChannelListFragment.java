package com.morlunk.mumbleclient.app;

import java.util.ArrayList;
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
import com.morlunk.mumbleclient.app.db.DbAdapter;
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

	private User selectedUser;
	
	/**
	 * Updates the users display with the data from the channelProvider.
	 */
	public void updateChannel() {
		// We need to make sure the fragment has been attached and is shown before updating the users.
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
	
	public void updateUserTalking(User user) {
		usersAdapter.refreshTalkingState(user);
	}
	
	/**
	 * Removes the user from the channel list.
	 * @param user
	 */
	public void removeUser(User user) {
		usersAdapter.removeUser(user);
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
			usersAdapter.refreshUser(user);
			return true;
		}
		return false;
	}
	
	public void setChatTarget(User chatTarget) {
		User oldTarget = selectedUser;
		selectedUser = chatTarget;
		if(usersAdapter != null) {
			if(oldTarget != null)
				usersAdapter.refreshUser(oldTarget);
			usersAdapter.refreshUser(selectedUser);
		}
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
		
		private final Context context;
		private final DbAdapter dbAdapter;
		private final List<User> users = new ArrayList<User>();
		private final Map<User, Boolean> userCommentsSeen = new HashMap<User, Boolean>();

		private final Runnable visibleUsersChangedCallback;

		private final Drawable chatDrawable; // Changes depending on theme.
		
		int totalViews = 0;

		public UserListAdapter(
			final Context context,
			final Runnable visibleUsersChangedCallback) {
			this.context = context;
			this.visibleUsersChangedCallback = visibleUsersChangedCallback;
			this.dbAdapter = MumbleService.getCurrentService().getDatabaseAdapter();
			
			// Fetch theme dependent icon
			Settings settings = Settings.getInstance(context);
			chatDrawable = getResources().getDrawable(settings.getTheme().equals(Settings.ARRAY_THEME_LIGHTDARK) ? R.drawable.ic_action_chat_light : R.drawable.ic_action_chat_dark);
		}

		@Override
		public int getCount() {
			return users.size();
		}

		@Override
		public User getItem(int arg0) {
			return users.get(arg0);
		}

		@Override
		public long getItemId(int arg0) {
			return users.get(arg0).session;
		}
		
		@Override
		public int getViewTypeCount() {
			return 1;
		}
		
		@Override
		public final View getView(final int position, View v, final ViewGroup parent) {
			// All views are the same.
			if (v == null) {
				final LayoutInflater inflater = (LayoutInflater) this.context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
				v = inflater.inflate(R.layout.channel_user_row, null);
			}

			// Tie the view to the current user.
			final User u = (User) getItem(position);
			
			refreshElements(v, u);
			
			return v;
		}

		public final boolean hasUser(final User user) {
			return users.contains(user);
		}

		@Override
		public void notifyDataSetChanged() {
			if (visibleUsersChangedCallback != null) {
				visibleUsersChangedCallback.run();
			}
			super.notifyDataSetChanged();
		}
		
		public void refreshUser(User user) {			
			if(!users.contains(user))
				return;
			
			int userPosition = users.indexOf(user);
			
			View userView = channelUsersList.getChildAt(userPosition-channelUsersList.getFirstVisiblePosition());
			
			// Update comment state
			if(user.comment != null || user.commentHash != null && !MumbleService.getCurrentService().isConnectedServerPublic()) {
				userCommentsSeen.put(user, dbAdapter.isCommentSeen(user.name, user.commentHash != null ? user.commentHash.toStringUtf8() : user.comment));
			}
			
			if(userView != null && userView.isShown())
				refreshElements(userView, user);
		}
		
		public void refreshTalkingState(User user) {
			if(!users.contains(user))
				return;
			
			int userPosition = users.indexOf(user);
			
			View userView = channelUsersList.getChildAt(userPosition-channelUsersList.getFirstVisiblePosition());
			
			if(userView != null && userView.isShown())
				refreshTalkingState(userView, user);
			
		}

		public void removeUser(final User user) {
			users.remove(user);
			userCommentsSeen.remove(user);

			if (user != null) {
				notifyDataSetChanged();
			}
		}

		public void setUsers(final List<User> newUsers) {
			this.users.clear();
			this.userCommentsSeen.clear();
			
			for (User user : newUsers) {
				this.users.add(user);
				// Get the user's comment history
				if(user.comment != null || user.commentHash != null && !MumbleService.getCurrentService().isConnectedServerPublic())
					this.userCommentsSeen.put(user, dbAdapter.isCommentSeen(user.name, user.commentHash != null ? user.commentHash.toStringUtf8() : user.comment));
			}
		}
		
		private void refreshElements(final View view, final User user) {
			final TextView name = (TextView) view.findViewById(R.id.userRowName);
			final ImageView comment = (ImageView) view.findViewById(R.id.commentState);
			final ImageView localMute = (ImageView) view.findViewById(R.id.localMuteState);
			final ImageView chatActive = (ImageView) view.findViewById(R.id.activeChatState);
			
			name.setText(user.name);
			
			refreshTalkingState(view, user);
			
			localMute.setVisibility(user.localMuted ? View.VISIBLE : View.GONE);
			
			if(user.comment != null || user.commentHash != null)
				comment.setImageResource(userCommentsSeen.get(user) ? R.drawable.ic_comment_seen : R.drawable.ic_comment);
			
			comment.setVisibility(user.comment != null || user.commentHash != null ? View.VISIBLE : View.GONE);
			comment.setOnClickListener(new OnCommentClickListener(user));
			
			chatActive.setImageDrawable(chatDrawable);
			chatActive.setVisibility(user.equals(selectedUser) ? View.VISIBLE : View.GONE);
		}
		
		private void refreshTalkingState(final View view, final User user) {
			final ImageView state = (ImageView) view.findViewById(R.id.userRowState);

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
		}
		

		private class OnCommentClickListener implements OnClickListener {
			
			private User user;
			
			public OnCommentClickListener(User user) {
				this.user = user;
			}
			
			@Override
			public void onClick(View v) {
				ImageView commentView = (ImageView)v;
				commentView.setImageResource(R.drawable.ic_comment_seen);

				if(MumbleService.getCurrentService() != null && !MumbleService.getCurrentService().isConnectedServerPublic())
					dbAdapter.setCommentSeen(user.name, user.commentHash != null ? user.commentHash.toStringUtf8() : user.comment);
				
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
		}
	}
}
