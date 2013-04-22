package com.morlunk.mumbleclient.app;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.sf.mumble.MumbleProto.RequestBlob;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Build;
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
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
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

	private User chatTarget;

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
		if(VERSION.SDK_INT >= 11) {
			DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
			int offset = (int) (displayMetrics.density*55); // 55dp offset
			channelUsersList.smoothScrollToPositionFromTop(flatPosition, offset, 250);
		} else
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
		User oldTarget = chatTarget;
		chatTarget = chatTarget;
		if (usersAdapter != null) {
			if (oldTarget != null)
				usersAdapter.refreshUser(oldTarget);
			usersAdapter.refreshUser(chatTarget);
		}
	}

	@Override
	public boolean onChildClick(ExpandableListView parent, View v,
			int groupPosition, int childPosition, long id) {
		View flagsView = v.findViewById(R.id.userFlags);
		User user = (User) usersAdapter.getChild(groupPosition, childPosition);
		boolean expand = !usersAdapter.selectedUsers.contains(user);
		if(expand)
			usersAdapter.selectedUsers.add(user);
		else
			usersAdapter.selectedUsers.remove(user);
		usersAdapter.expandPane(expand, flagsView, true);
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
		/**
		 * A list of the selected channels. Used to restore the expanded state after reloading the adapter.
		 */
		private List<Channel> selectedChannels = new ArrayList<Channel>();

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
			final TextView comment = (TextView) view.findViewById(R.id.channel_user_row_comment);
			final TextView localMute = (TextView) view.findViewById(R.id.channel_user_row_mute);
			final TextView chat = (TextView) view.findViewById(R.id.channel_user_row_chat);
			final TextView registered = (TextView) view.findViewById(R.id.channel_user_row_registered);
			//final ImageView info = (ImageView) view.findViewById(R.id.channel_user_row_info);
			
			name.setText(user.name);
			name.setTypeface(null, user.equals(service.getCurrentUser()) ? Typeface.BOLD : Typeface.NORMAL);

			refreshTalkingState(view, user);

			int chatImage = chatTarget != null && chatTarget.equals(user) ? R.drawable.ic_action_chat_active : R.drawable.ic_action_chat_dark;
			chat.setCompoundDrawablesWithIntrinsicBounds(0, chatImage, 0, 0);
			chat.setOnClickListener(new OnClickListener() {
				
				@Override
				public void onClick(View v) {
					User oldUser = chatTarget;
					boolean activated = chatTarget == null || !chatTarget.equals(user);
					chatTarget = activated ? user : null;
					channelProvider.setChatTarget(chatTarget);
					int image = activated ? R.drawable.ic_action_chat_active : R.drawable.ic_action_chat_dark;
					chat.setCompoundDrawablesWithIntrinsicBounds(0, image, 0, 0);
					if(oldUser != null)
						refreshUser(oldUser); // Update chat icon of old user when changing targets
				}
			});
			
			localMute.setText(user.localMuted ? R.string.channel_user_row_muted : R.string.channel_user_row_mute);
			int muteImage = user.localMuted ? R.drawable.ic_action_audio_muted_active : R.drawable.ic_action_audio_muted;
			localMute.setCompoundDrawablesWithIntrinsicBounds(0, muteImage, 0, 0);
			localMute.setOnClickListener(new OnClickListener() {
				
				@Override
				public void onClick(View v) {
					user.localMuted = !user.localMuted;
					localMute.setText(user.localMuted ? R.string.channel_user_row_muted : R.string.channel_user_row_mute);
					int image = user.localMuted ? R.drawable.ic_action_audio_muted_active : R.drawable.ic_action_audio_muted;
					localMute.setCompoundDrawablesWithIntrinsicBounds(0, image, 0, 0);
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

			int commentImage = userCommentsSeen.get(user) ? R.drawable.ic_action_comment
					: R.drawable.ic_action_comment_active;
			comment.setCompoundDrawablesWithIntrinsicBounds(0, commentImage, 0, 0);
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
			expandPane(selectedUsers.contains(user), flagsView, false);

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
			
			final View pane = v.findViewById(R.id.channel_row_pane);
			expandPane(selectedChannels.contains(channel), pane, false);

			TextView nameView = (TextView) v
					.findViewById(R.id.channel_row_name);
			TextView countView = (TextView) v
					.findViewById(R.id.channel_row_count);

			nameView.setText(channel.name);
			countView.setText(String.format("(%d)", channel.userCount));
			
			Favourite favourite = service.getFavouriteForChannel(channel);

			final TextView joinView = (TextView) v.findViewById(R.id.channel_row_join);
			final TextView favouriteView = (TextView) v.findViewById(R.id.channel_row_favourite);
			final TextView chatView = (TextView) v.findViewById(R.id.channel_row_chat);
			final TextView commentView = (TextView) v.findViewById(R.id.channel_row_comment);
			
			int chatImage = chatTarget != null && chatTarget.equals(channel) ? R.drawable.ic_action_chat_active : R.drawable.ic_action_chat_dark;
			chatView.setCompoundDrawablesWithIntrinsicBounds(0, chatImage, 0, 0);
			chatView.setOnClickListener(new OnClickListener() {
				
				@Override
				public void onClick(View v) {
					/*
					User oldUser = selectedUser;
					boolean activated = selectedUser == null || !selectedUser.equals(user);
					selectedUser = activated ? user : null;
					channelProvider.setChatTarget(selectedUser);
					chatImage.setImageResource(activated ? R.drawable.ic_action_chat_active : R.drawable.ic_action_chat_dark);
					if(oldUser != null)
						refreshUser(oldUser); // Update chat icon of old user when changing targets
					*/
				}
			});
			
			joinView.setOnClickListener(new OnClickListener() {
				
				@Override
				public void onClick(View v) {
					selectedChannels.remove(channel);
					expandPane(false, pane, true);
					new AsyncTask<Void, Void, Void>() {
						@Override
						protected Void doInBackground(Void... params) {
							service.joinChannel(channel.id);
							return null;
						}
					}.execute();
				}
			});

			int favouriteImage = favourite != null ? R.drawable.ic_action_favourite_on : R.drawable.ic_action_favourite_off;
			favouriteView.setCompoundDrawablesWithIntrinsicBounds(0, favouriteImage, 0, 0);
			favouriteView.setOnClickListener(new OnClickListener() {
				
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
							int image = result ? R.drawable.ic_action_favourite_on : R.drawable.ic_action_favourite_off;
							favouriteView.setCompoundDrawablesWithIntrinsicBounds(0, image, 0, 0);
							service.updateFavourites();
						};
						
					}.execute(channel);
				}
			});
			
			commentView.setOnClickListener(new OnClickListener() {
				
				@Override
				public void onClick(View v) {
					// TODO Auto-generated method stub
					
				}
			});
			
			View channelTitle = v.findViewById(R.id.channel_row_title);
			
			// Pad the view depending on channel's nested level.
			DisplayMetrics metrics = getResources().getDisplayMetrics();
			float margin = getNestedLevel(channel)
					* TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
							25, metrics);
			channelTitle.setPadding((int) margin, channelTitle.getPaddingTop(), channelTitle.getPaddingRight(),
					channelTitle.getPaddingBottom());
			
			// Override the expand/collapse paradigm and show the pane
			v.setOnClickListener(new OnClickListener() {
				
				@Override
				public void onClick(View v) {
					boolean expanding = !selectedChannels.contains(channel);
					if(expanding)
						selectedChannels.add(channel);
					else
						selectedChannels.remove(channel);
					expandPane(expanding, pane, true);
				}
			});

			return v;
		}
		
		/**
		 * Expands or contracts the given pane by using its margin attribute.
		 * Animation only works on API v11+.
		 * @param expand
		 * @param pane
		 * @param animated
		 */
		@TargetApi(Build.VERSION_CODES.HONEYCOMB)
		private void expandPane(Boolean expand, final View pane, boolean animated) {
			ValueAnimator valueAnimator;
			
			int contractedMargin = -pane.getLayoutParams().height;
			
			if(animated && VERSION.SDK_INT >= 11) {
				if(expand)
					valueAnimator = ValueAnimator.ofInt(contractedMargin, 0);
				else
					valueAnimator = ValueAnimator.ofInt(0, contractedMargin);
				valueAnimator.setDuration(250);
				valueAnimator.addUpdateListener(new AnimatorUpdateListener() {
					
					@Override
					public void onAnimationUpdate(ValueAnimator animation) {
						Integer value = (Integer) animation.getAnimatedValue();
						LinearLayout.LayoutParams layoutParams = (LayoutParams) pane.getLayoutParams();
						layoutParams.bottomMargin = value.intValue();
						if(!pane.isLayoutRequested()) // Prevent requestLayout from clogging
							pane.requestLayout();
					}
				});
				valueAnimator.start();
			} else {
				LinearLayout.LayoutParams layoutParams = (LayoutParams) pane.getLayoutParams();
				layoutParams.bottomMargin = expand ? 0 : contractedMargin;
				pane.requestLayout();
			}
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
				TextView commentView = (TextView) v.findViewById(R.id.channel_user_row_comment);

				if (channelProvider.getService() != null
						&& !channelProvider.getService()
								.isConnectedServerPublic()) {
					commentView.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.ic_comment_seen, 0, 0);
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
