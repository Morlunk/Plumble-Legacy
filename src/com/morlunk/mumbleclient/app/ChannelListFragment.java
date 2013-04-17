package com.morlunk.mumbleclient.app;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.sf.mumble.MumbleProto.RequestBlob;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Build.VERSION;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.BaseExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.ExpandableListView.OnChildClickListener;
import android.widget.ImageView;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockFragment;
import com.morlunk.mumbleclient.R;
import com.morlunk.mumbleclient.app.db.DbAdapter;
import com.morlunk.mumbleclient.app.db.Favourite;
import com.morlunk.mumbleclient.service.MumbleProtocol.MessageType;
import com.morlunk.mumbleclient.service.MumbleService;
import com.morlunk.mumbleclient.service.audio.AudioOutputHost;
import com.morlunk.mumbleclient.service.model.Channel;
import com.morlunk.mumbleclient.service.model.User;

public class ChannelListFragment extends SherlockFragment implements
		OnChildClickListener {

	/**
	 * The parent activity MUST implement ChannelProvider. An exception will be
	 * thrown otherwise.
	 */
	private ChannelProvider channelProvider;

	private ExpandableListView channelUsersList;
	private UserListAdapter usersAdapter;

	private User selectedUser;

	public void updateChannelList() {
		usersAdapter.updateChannelList();
		usersAdapter.notifyDataSetChanged();
		
		// Always make sure channels are expanded
		for(int i=0;i<usersAdapter.getGroupCount();i++) {
			channelUsersList.expandGroup(i);
		}
	}
	
	public void expandChannel(Channel channel) {
		channelUsersList.expandGroup(usersAdapter.channels.indexOf(channel));
	}

	/**
	 * Updates the user specified in the users adapter.
	 * 
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
	 * 
	 * @param user
	 */
	public void removeUser(User user) {
		usersAdapter.notifyDataSetChanged();
	}
	
	/**
	 * Scrolls to the passed user, if they are not visible.
	 * @param user
	 */
	@SuppressLint("NewApi")
	public void scrollToUser(User user) {
		Channel userChannel = user.getChannel();
		int channelPosition = usersAdapter.channels.indexOf(userChannel);
		int userPosition = channelProvider.getService().getChannelMap().get(userChannel).indexOf(user);
		int flatPosition = channelUsersList.getFlatListPosition(ExpandableListView.getPackedPositionForChild(channelPosition, userPosition));
		if(VERSION.SDK_INT >= 8)
			channelUsersList.smoothScrollToPosition(flatPosition);
		else
			channelUsersList.setSelectedChild(channelPosition, userPosition, false);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * android.support.v4.app.Fragment#onCreateView(android.view.LayoutInflater,
	 * android.view.ViewGroup, android.os.Bundle)
	 */
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.channel_list, container, false);

		// Get the UI views
		channelUsersList = (ExpandableListView) view
				.findViewById(R.id.channelUsers);
		channelUsersList.setOnChildClickListener(this);
		
		return view;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.support.v4.app.Fragment#onAttach(android.app.Activity)
	 */
	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);

		try {
			channelProvider = (ChannelProvider) activity;
		} catch (ClassCastException e) {
			throw new ClassCastException(activity.toString()
					+ " must implement ChannelProvider!");
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.support.v4.app.Fragment#onActivityCreated(android.os.Bundle)
	 */
	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		
		// If service is bound, update. Otherwise, we should receive a request to do so once bound from activity.
		if(channelProvider.getService() != null)
			onServiceBound();
		
		registerForContextMenu(channelUsersList);
	}
	
	@Override
	public void onResume() {
		super.onResume();
		
		// Update channel list when resuming.
		if(usersAdapter != null && 
				channelProvider.getService() != null && 
				channelProvider.getService().isConnected())
	        updateChannelList();
	}
	
	public void onServiceBound() {
		usersAdapter = new UserListAdapter(getActivity(),
				channelProvider.getService());
		channelUsersList.setAdapter(usersAdapter);
        updateChannelList();
        scrollToUser(channelProvider.getService().getCurrentUser());
	}

	public void setChatTarget(User chatTarget) {
		User oldTarget = selectedUser;
		selectedUser = chatTarget;
		if (usersAdapter != null) {
			if (oldTarget != null)
				usersAdapter.refreshUser(oldTarget);
			usersAdapter.refreshUser(selectedUser);
		}
	}

	@Override
	public boolean onChildClick(ExpandableListView parent, View v,
			int groupPosition, int childPosition, long id) {
		View flagsView = v.findViewById(R.id.userFlags);
		User user = (User) usersAdapter.getChild(groupPosition, childPosition);
		if(flagsView.getVisibility() == View.GONE)
			usersAdapter.selectedUsers.add(user);
		else
			usersAdapter.selectedUsers.remove(user);
		flagsView.setVisibility(flagsView.getVisibility() == View.GONE ? View.VISIBLE : View.GONE);
		return true;
	}

	class UserListAdapter extends BaseExpandableListAdapter {
		private final Context context;
		private final MumbleService service;
		private final DbAdapter dbAdapter;
		private List<Channel> channels = new ArrayList<Channel>();
		private Map<Channel, List<User>> channelMap = new HashMap<Channel, List<User>>();
		/**
		 * A list of the selected users. Used to restore the expanded state after reloading the adapter.
		 */
		private List<User> selectedUsers = new ArrayList<User>();

		private final Map<User, Boolean> userCommentsSeen = new HashMap<User, Boolean>();

		public UserListAdapter(final Context context,
				final MumbleService service) {
			this.context = context;
			this.service = service;
			this.dbAdapter = this.service.getDatabaseAdapter();
		}

		/**
		 * Fetches a new list of channels from the service.
		 */
		public void updateChannelList() {
			this.channels = service.getSortedChannelList();
			this.channelMap = service.getChannelMap();
		}

		public void refreshUser(User user) {
			if(!service.getUserList().contains(user))
				return;
			
			int channelPosition = channels.indexOf(user.getChannel());
			if (!channelUsersList.isGroupExpanded(channelPosition))
				return;
			int userPosition = channelMap.get(user.getChannel()).indexOf(user);
			long packedPosition = ExpandableListView.getPackedPositionForChild(
					channelPosition, userPosition);
			int position = channelUsersList.getFlatListPosition(packedPosition);
			
			View userView = channelUsersList.getChildAt(position
					- channelUsersList.getFirstVisiblePosition());

			// Update comment state
			if (user.comment != null
					|| user.commentHash != null
					&& !service.isConnectedServerPublic()) {
				userCommentsSeen.put(user, dbAdapter.isCommentSeen(
						user.name,
						user.commentHash != null ? user.commentHash
								.toStringUtf8() : user.comment));
			}

			if (userView != null && userView.isShown() && userView.getTag() != null && userView.getTag().equals(user))
				refreshElements(userView, user);
		}

		public void refreshTalkingState(User user) {
			if(!service.getUserList().contains(user))
				return;
			
			int channelPosition = channels.indexOf(user.getChannel());
			if (!channelUsersList.isGroupExpanded(channelPosition))
				return;
			int userPosition = channelMap.get(user.getChannel()).indexOf(user);
			long packedPosition = ExpandableListView.getPackedPositionForChild(
					channelPosition, userPosition);
			int position = channelUsersList.getFlatListPosition(packedPosition);
			View userView = channelUsersList.getChildAt(position
					- channelUsersList.getFirstVisiblePosition());

			if (userView != null && userView.isShown() && userView.getTag() != null && userView.getTag().equals(user)
					&& service.getUserList().contains(user))
				refreshTalkingState(userView, user);

		}

		private void refreshElements(final View view, final User user) {
			final View titleView = view.findViewById(R.id.channel_user_row_title);
			final TextView name = (TextView) view
					.findViewById(R.id.userRowName);
			final View comment = view.findViewById(R.id.channel_user_row_comment);
			final ImageView commentImage = (ImageView) view
					.findViewById(R.id.channel_user_row_comment_image);
			final View localMute = view.findViewById(R.id.channel_user_row_mute);
			final ImageView localMuteImage = (ImageView) view
					.findViewById(R.id.channel_user_row_mute_image);
			final TextView localMuteText = (TextView) view
					.findViewById(R.id.channel_user_row_mute_text);
			final View chat = view.findViewById(R.id.channel_user_row_chat);
			final ImageView chatImage = (ImageView) view
					.findViewById(R.id.channel_user_row_chat_image);
			final View registered = view.findViewById(R.id.channel_user_row_registered);
			//final ImageView info = (ImageView) view.findViewById(R.id.channel_user_row_info);
			
			name.setText(user.name);
			name.setTypeface(null, user.equals(service.getCurrentUser()) ? Typeface.BOLD : Typeface.NORMAL);

			refreshTalkingState(view, user);

			chatImage.setImageResource(selectedUser != null && selectedUser.equals(user) ? R.drawable.ic_action_chat_active : R.drawable.ic_action_chat_dark);
			chat.setOnClickListener(new OnClickListener() {
				
				@Override
				public void onClick(View v) {
					User oldUser = selectedUser;
					boolean activated = selectedUser == null || !selectedUser.equals(user);
					selectedUser = activated ? user : null;
					channelProvider.setChatTarget(selectedUser);
					chatImage.setImageResource(activated ? R.drawable.ic_action_chat_active : R.drawable.ic_action_chat_dark);
					if(oldUser != null)
						refreshUser(oldUser); // Update chat icon of old user when changing targets
				}
			});
			
			localMuteText.setText(user.localMuted ? R.string.channel_user_row_muted : R.string.channel_user_row_mute);
			localMuteImage.setImageResource(user.localMuted ? R.drawable.ic_action_audio_muted_active : R.drawable.ic_action_audio_muted);
			localMute.setOnClickListener(new OnClickListener() {
				
				@Override
				public void onClick(View v) {
					user.localMuted = !user.localMuted;
					localMuteText.setText(user.localMuted ? R.string.channel_user_row_muted : R.string.channel_user_row_mute);
					localMuteImage.setImageResource(user.localMuted ? R.drawable.ic_action_audio_muted_active : R.drawable.ic_action_audio_muted);
				}
			});

			if (!userCommentsSeen.containsKey(user)) {
				String commentData = user.commentHash != null ? user.commentHash
						.toStringUtf8() : user.comment;
				userCommentsSeen.put(
						user,
						commentData != null ? dbAdapter.isCommentSeen(
								user.name, commentData) : false);
			}

			commentImage.setImageResource(userCommentsSeen.get(user) ? R.drawable.ic_action_comment
					: R.drawable.ic_action_comment_active);
			comment.setVisibility(user.comment != null
					|| user.commentHash != null ? View.VISIBLE : View.GONE);
			comment.setOnClickListener(new OnCommentClickListener(user));
			
			/*
			info.setOnClickListener(new OnClickListener() {
				
				@Override
				public void onClick(View v) {
					// TODO Auto-generated method stub
					
				}
			});
			*/
			
			registered.setVisibility(user.isRegistered ? View.VISIBLE : View.GONE);

			Channel channel = user.getChannel();
			DisplayMetrics metrics = getResources().getDisplayMetrics();

			// Pad the view depending on channel's nested level.
			float margin = (getNestedLevel(channel) + 1)
					* TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
							25, metrics);
			titleView.setPadding((int) margin, titleView.getPaddingTop(),
					titleView.getPaddingRight(), titleView.getPaddingBottom());
		}

		private void refreshTalkingState(final View view, final User user) {
			final ImageView state = (ImageView) view
					.findViewById(R.id.userRowState);

			if (user.selfDeafened) {
				state.setImageResource(R.drawable.ic_deafened);
			} else if (user.selfMuted) {
				state.setImageResource(R.drawable.ic_muted);
			} else if (user.serverDeafened) {
				state.setImageResource(R.drawable.ic_server_deafened);
			} else if (user.serverMuted) {
				state.setImageResource(R.drawable.ic_server_muted);
			} else if (user.suppressed) {
				state.setImageResource(R.drawable.ic_suppressed);
			} else {
				if (user.talkingState == AudioOutputHost.STATE_TALKING) {
					state.setImageResource(R.drawable.ic_talking_on);
				} else {
					state.setImageResource(R.drawable.ic_talking_off);
				}
			}
		}

		public int getNestedLevel(Channel channel) {
			if (channel.parent != 0) {
				for (Channel c : channels) {
					if (c.id == channel.parent) {
						return 1 + getNestedLevel(c);
					}
				}
			}
			return 0;
		}

		@Override
		public Object getChild(int groupPosition, int childPosition) {
			Channel channel = channels.get(groupPosition);
			List<User> channelUsers = channelMap.get(channel);
			return channelUsers.get(childPosition);
		}

		@Override
		public long getChildId(int arg0, int arg1) {
			return 0;
		}

		@SuppressLint("InlinedApi")
		@Override
		public View getChildView(int groupPosition, int childPosition,
				boolean isLastChild, View v, ViewGroup arg4) {
			if (v == null) {
				final LayoutInflater inflater = (LayoutInflater) this.context
						.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
				v = inflater.inflate(R.layout.channel_user_row, null);
			}

			User user = (User) getChild(groupPosition, childPosition);
			
			View flagsView = v.findViewById(R.id.userFlags);
			flagsView.setVisibility(selectedUsers.contains(user) ? View.VISIBLE : View.GONE);

			refreshElements(v, user);
			v.setTag(user);

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
		public View getGroupView(int groupPosition, boolean isExpanded,
				View convertView, ViewGroup parent) {
			View v = convertView;
			if (v == null) {
				final LayoutInflater inflater = (LayoutInflater) this.context
						.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
				v = inflater.inflate(R.layout.channel_row, null);
			}
			final Channel channel = channels.get(groupPosition);

			TextView nameView = (TextView) v
					.findViewById(R.id.channel_row_name);
			TextView countView = (TextView) v
					.findViewById(R.id.channel_row_count);

			nameView.setText(channel.name);
			countView.setText(String.format("(%d)", channel.userCount));

			final ImageView favouriteImageView = (ImageView) v.findViewById(R.id.channel_row_favourite);
			Favourite favourite = service.getFavouriteForChannel(channel);
			favouriteImageView.setImageResource(favourite != null ? R.drawable.ic_action_favourite_on : R.drawable.ic_action_favourite_off);
			favouriteImageView.setOnClickListener(new OnClickListener() {
				
				@Override
				public void onClick(View v) {
					new AsyncTask<Channel, Void, Boolean>() {

						@Override
						protected Boolean doInBackground(Channel... params) {
							Channel favouriteChannel = params[0];
							Favourite f = service.getFavouriteForChannel(favouriteChannel);
							if(f == null)
								dbAdapter.createFavourite(service.getConnectedServer().getId(), channel.id);
							else
								dbAdapter.deleteFavourite(f.getId());
							return f == null; // True: created, False: deleted
						}
						
						protected void onPostExecute(Boolean result) {
							favouriteImageView.setImageResource(result ? R.drawable.ic_action_favourite_on : R.drawable.ic_action_favourite_off);
							service.updateFavourites();
						};
						
					}.execute(channel);
				}
			});
			
			// Pad the view depending on channel's nested level.
			DisplayMetrics metrics = getResources().getDisplayMetrics();
			float margin = getNestedLevel(channel)
					* TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
							25, metrics);
			v.setPadding((int) margin, v.getPaddingTop(), v.getPaddingRight(),
					v.getPaddingBottom());
			
			// Override the expand/collapse paradigm and join channels by pressing them.
			v.setOnClickListener(new OnClickListener() {
				
				@Override
				public void onClick(View v) {
					if(!service.getCurrentUser().getChannel().equals(channel)) {
						new AsyncTask<Void, Void, Void>() {
							
							@Override
							protected Void doInBackground(Void... params) {
								service.joinChannel(channel.id);
								return null;
							}
							
						}.execute();
					}
				}
			});

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

		private class OnCommentClickListener implements OnClickListener {

			private User user;

			public OnCommentClickListener(User user) {
				this.user = user;
			}

			@SuppressLint("NewApi")
			@Override
			public void onClick(View v) {
				ImageView commentView = (ImageView) v.findViewById(R.id.channel_user_row_comment_image);

				if (channelProvider.getService() != null
						&& !channelProvider.getService()
								.isConnectedServerPublic()) {
					commentView.setImageResource(R.drawable.ic_action_comment);
					dbAdapter.setCommentSeen(
							user.name,
							user.commentHash != null ? user.commentHash
									.toStringUtf8() : user.comment);
				}

				AlertDialog.Builder builder = new AlertDialog.Builder(context);
				builder.setTitle(R.string.comment);
				builder.setPositiveButton(R.string.close, null);
				final WebView webView = new WebView(context);
				// TODO: Do it in better way?
				final StringBuilder sb = new StringBuilder();
				sb.append("<center>");
				sb.append(getResources().getString(R.string.retrieving));
				sb.append("</center>");
				String string = sb.toString();
				webView.loadDataWithBaseURL("", string, "text/html", "utf-8",
						"");
				builder.setView(webView);

				final AlertDialog dialog = builder.show();

				if (user.comment != null) {
					webView.loadDataWithBaseURL("", user.comment, "text/html",
							"utf-8", "");
				} else if (user.commentHash != null) {
					// Retrieve comment from blob
					final RequestBlob.Builder blobBuilder = RequestBlob
							.newBuilder();
					blobBuilder.addSessionComment(user.session);

					new AsyncTask<Void, Void, Void>() {
						@Override
						protected Void doInBackground(Void... params) {
							channelProvider.getService().sendTcpMessage(
									MessageType.RequestBlob, blobBuilder);
							// TODO fix. This is messy, we're polling until we
							// get a comment response.
							while (user.comment == null && dialog.isShowing()) {
								try {
									Thread.sleep(100);
								} catch (InterruptedException e) {
									e.printStackTrace();
								}
							}
							return null;
						}

						protected void onPostExecute(Void result) {
							webView.loadDataWithBaseURL("", user.comment,
									"text/html", "utf-8", "");
						};
					}.execute();
				}
			}
		}
	}
}
