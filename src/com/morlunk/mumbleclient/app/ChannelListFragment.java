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
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.ExpandableListView.ExpandableListContextMenuInfo;
import android.widget.ImageView;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockFragment;
import com.morlunk.mumbleclient.R;
import com.morlunk.mumbleclient.Settings;
import com.morlunk.mumbleclient.app.db.DbAdapter;
import com.morlunk.mumbleclient.service.MumbleProtocol.MessageType;
import com.morlunk.mumbleclient.service.MumbleService;
import com.morlunk.mumbleclient.service.audio.AudioOutputHost;
import com.morlunk.mumbleclient.service.model.Channel;
import com.morlunk.mumbleclient.service.model.User;

public class ChannelListFragment extends SherlockFragment implements OnItemClickListener {
	
	/**
	 * The parent activity MUST implement ChannelProvider. An exception will be thrown otherwise.
	 */
	private ChannelProvider channelProvider;
	
	private ExpandableListView channelUsersList;
	private UserListAdapter usersAdapter;

	private User selectedUser;
	
	public void updateChannelList() {
		usersAdapter.updateChannelList();
		usersAdapter.notifyDataSetChanged();
	}
	
	/**
	 * Updates the users display with the data from the channelProvider.
	 */
	public void updateChannel(Channel channel) {
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
		usersAdapter.notifyDataSetChanged();
	}
	
	/* (non-Javadoc)
	 * @see android.support.v4.app.Fragment#onCreateView(android.view.LayoutInflater, android.view.ViewGroup, android.os.Bundle)
	 */
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.channel_list, container, false);

		// Get the UI views
		channelUsersList = (ExpandableListView) view.findViewById(R.id.channelUsers);
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

		usersAdapter = new UserListAdapter(getActivity(), channelProvider.getService());
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
		ExpandableListContextMenuInfo info = (ExpandableListContextMenuInfo) item.getMenuInfo();
		User user = (User) usersAdapter.getChild(ExpandableListView.getPackedPositionGroup(info.packedPosition), ExpandableListView.getPackedPositionChild(info.packedPosition));
		
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
	
	class UserListAdapter extends BaseExpandableListAdapter {
		Comparator<User> userComparator = new Comparator<User>() {
			@Override
			public int compare(final User object1, final User object2) {
				return object1.name.toLowerCase(Locale.ENGLISH)
						.compareTo(object2.name.toLowerCase(Locale.ENGLISH));
			}
		};
		
		private final Context context;
		private final MumbleService service;
		private final DbAdapter dbAdapter;
		private List<Channel> channels = new ArrayList<Channel>();
		
		private final Map<User, Boolean> userCommentsSeen = new HashMap<User, Boolean>();

		private final Drawable chatDrawable; // Changes depending on theme.
		
		int totalViews = 0;

		public UserListAdapter(
			final Context context,
			final MumbleService service) {
			// FIXME fix service code, no singletons
			this.context = context;
			this.service = MumbleService.getCurrentService();
			this.dbAdapter = this.service.getDatabaseAdapter();
			
			// Fetch theme dependent icon
			Settings settings = Settings.getInstance(context);
			chatDrawable = getResources().getDrawable(settings.getTheme().equals(Settings.ARRAY_THEME_LIGHTDARK) ? R.drawable.ic_action_chat_light : R.drawable.ic_action_chat_dark);
			
			updateChannelList();
		}
		
		/**
		 * Fetches a new list of channels from the service.
		 */
		public void updateChannelList() {
			this.channels = service.getSortedChannelList();
		}
		
		public void refreshUser(User user) {			
			int position = getPositionOfUser(user);
			
			View userView = channelUsersList.getChildAt(position-channelUsersList.getFirstVisiblePosition());
			
			// Update comment state
			if(user.comment != null || user.commentHash != null && !MumbleService.getCurrentService().isConnectedServerPublic()) {
				userCommentsSeen.put(user, dbAdapter.isCommentSeen(user.name, user.commentHash != null ? user.commentHash.toStringUtf8() : user.comment));
			}
			
			if(userView != null && userView.isShown())
				refreshElements(userView, user);
		}
		
		public void refreshTalkingState(User user) {
			int position = getPositionOfUser(user);
			
			View userView = channelUsersList.getChildAt(position-channelUsersList.getFirstVisiblePosition());
			
			if(userView != null && userView.isShown() && service.getUserList().contains(user))
				refreshTalkingState(userView, user);
			
		}
		
		/**
		 * Gets the position of the passed user in the channel hierarchy. Position includes channel headers.
		 * @param user
		 */
		private int getPositionOfUser(final User user) {
			int position = 0;
			for(int i=0;i<channels.size();i++) {
				Channel channel = channels.get(i);
				position++;
				if(channel.id == user.getChannel().id)
					position += service.getChannelUsers(channel).indexOf(user); // add to current user position in channel
				else
					position += channelUsersList.isGroupExpanded(i) ? channel.userCount : 0; // go to next section, adding all users to the position if expanded
			}
			return position;
		}
		
		private void refreshElements(final View view, final User user) {
			final TextView name = (TextView) view.findViewById(R.id.userRowName);
			final ImageView comment = (ImageView) view.findViewById(R.id.commentState);
			final ImageView localMute = (ImageView) view.findViewById(R.id.localMuteState);
			final ImageView chatActive = (ImageView) view.findViewById(R.id.activeChatState);
			
			name.setText(user.name);
			
			refreshTalkingState(view, user);
			
			localMute.setVisibility(user.localMuted ? View.VISIBLE : View.GONE);
			
			if(userCommentsSeen.containsKey(user))
				comment.setImageResource(userCommentsSeen.get(user) ? R.drawable.ic_comment_seen : R.drawable.ic_comment);
			
			comment.setVisibility(user.comment != null || user.commentHash != null ? View.VISIBLE : View.GONE);
			comment.setOnClickListener(new OnCommentClickListener(user));
			
			chatActive.setImageDrawable(chatDrawable);
			chatActive.setVisibility(user.equals(selectedUser) ? View.VISIBLE : View.GONE);
		}
		
		private void refreshTalkingState(final View view, final User user) {
			final ImageView state = (ImageView) view.findViewById(R.id.userRowState);
			
			if(user.selfDeafened) {
				state.setImageResource(R.drawable.ic_deafened);
			} else if(user.selfMuted) {
				state.setImageResource(R.drawable.ic_muted);
			} else if(user.serverDeafened) {
				state.setImageResource(R.drawable.ic_server_deafened);
			} else if(user.serverMuted) {
				state.setImageResource(R.drawable.ic_server_muted);
			} else if(user.suppressed) {
				state.setImageResource(R.drawable.ic_suppressed);
			} else {
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

				if(MumbleService.getCurrentService() != null && !MumbleService.getCurrentService().isConnectedServerPublic()) {
					commentView.setImageResource(R.drawable.ic_comment_seen);
					dbAdapter.setCommentSeen(user.name, user.commentHash != null ? user.commentHash.toStringUtf8() : user.comment);
				}
				
				AlertDialog.Builder builder = new AlertDialog.Builder(context);
				builder.setTitle(R.string.comment);
				builder.setPositiveButton(R.string.close, null);
				final WebView webView = new WebView(context);
				//TODO: Do it in better way?
				final StringBuilder sb = new StringBuilder();
				sb.append("<center>");
				sb.append(getResources().getString(R.string.retrieving));
				sb.append("</center>");
				String string = sb.toString();
				webView.loadDataWithBaseURL("", string, "text/html", "utf-8", "");
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


		@Override
		public Object getChild(int groupPosition, int childPosition) {
			Channel channel = channels.get(groupPosition);
			List<User> channelUsers = channelProvider.getService().getChannelUsers(channel);
			return channelUsers.get(childPosition);
		}

		@Override
		public long getChildId(int arg0, int arg1) {
			return 0;
		}

		@Override
		public View getChildView(int groupPosition, int childPosition, boolean isLastChild, View v,
				ViewGroup arg4) {
			// All views are the same.
			if (v == null) {
				final LayoutInflater inflater = (LayoutInflater) this.context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
				v = inflater.inflate(R.layout.channel_user_row, null);
			}
			
			User user = (User) getChild(groupPosition, childPosition);
			
			refreshElements(v, user);
			
			return v;
		}

		@Override
		public int getChildrenCount(int arg0) {
			return channels.get(arg0).userCount;
		}

		@Override
		public Object getGroup(int arg0) {
			return channels.get(arg0);
		}

		@Override
		public int getGroupCount() {
			return channels.size();
		}

		@Override
		public long getGroupId(int arg0) {
			return 0;
		}

		@Override
		public View getGroupView(int groupPosition, boolean isExpanded, View convertView,
				ViewGroup parent) {
			View v = convertView;
			if(v == null) {
				final LayoutInflater inflater = (LayoutInflater) this.context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
				v = inflater.inflate(R.layout.channel_row, null);
			}
			Channel channel = channels.get(groupPosition);
			
			TextView nameView = (TextView)v.findViewById(R.id.channel_row_name);
			TextView countView = (TextView)v.findViewById(R.id.channel_row_count);
			
			nameView.setText(channel.name);
			countView.setText(String.format("(%d)", channel.userCount));
			
			return v;
		}

		@Override
		public boolean hasStableIds() {
			return false;
		}

		@Override
		public boolean isChildSelectable(int arg0, int arg1) {
			return true;
		}
	}
}
