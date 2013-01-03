package com.morlunk.mumbleclient.app;

import net.sf.mumble.MumbleProto.UserState;
import android.app.Activity;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Editable;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.format.DateUtils;
import android.text.style.ForegroundColorSpan;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

import com.actionbarsherlock.app.SherlockFragment;
import com.morlunk.mumbleclient.R;
import com.morlunk.mumbleclient.service.model.Message;
import com.morlunk.mumbleclient.service.model.User;

public class ChannelChatFragment extends SherlockFragment {

	private static final String CHAT_TEXT_KEY = "chatText";
	
	private ChannelProvider channelProvider;
	private ScrollView chatScroll;
	private TextView chatText;
	private EditText chatTextEdit;
	private ImageButton sendButton;

	private User chatTarget;
	
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		
		if(savedInstanceState != null) {
			if(savedInstanceState.containsKey(CHAT_TEXT_KEY)) {
				chatText.setText(Html.fromHtml(savedInstanceState.getString(CHAT_TEXT_KEY)));
			}
		}
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
	
	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		if(chatText != null)
			outState.putString(CHAT_TEXT_KEY, Html.toHtml((Spanned) chatText.getText()));
	}
	
	/* (non-Javadoc)
	 * @see android.support.v4.app.Fragment#onCreateView(android.view.LayoutInflater, android.view.ViewGroup, android.os.Bundle)
	 */
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.chat_view, container, false);
		chatScroll = (ScrollView) view.findViewById(R.id.chatScroll);
		chatText = (TextView) view.findViewById(R.id.chatText);
		chatTextEdit = (EditText) view.findViewById(R.id.chatTextEdit);
		
		sendButton = (ImageButton) view.findViewById(R.id.chatTextSend);
		sendButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				sendMessage(chatTextEdit);
			}
		});
		
		chatTextEdit.setOnEditorActionListener(new OnEditorActionListener() {
			@Override
			public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
				sendMessage(v);
				return true;
			}
		});
		
		chatTextEdit.addTextChangedListener(new TextWatcher() {
			
			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				sendButton.setEnabled(chatTextEdit.getText().length() != 0);
			}
			
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count,
					int after) { }
			@Override
			public void afterTextChanged(Editable s) { }
		});
		
		updateText();
		updateChatTargetText();
		return view;
	}
	
	/**
	 * Convenience method to insert a span for colouring a string to a SSB. For channel/user names.
	 */
	private void appendNameHighlight(String name, SpannableStringBuilder sb) {
		sb.append(name);
		sb.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.abs__holo_blue_light)), sb.length()-name.length(), sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
	}
	
	private void addChatText(Spannable text) {
		chatText.append(text);
		chatText.append(Html.fromHtml("<br>"));
		
		chatScroll.post(new Runnable() {
			
			@Override
			public void run() {
				chatScroll.smoothScrollTo(0, chatText.getHeight());
			}
		});
	}

	void addMessage(final Message msg) {
		final SpannableStringBuilder sb = new SpannableStringBuilder();
		sb.append("[");
		sb.append(DateUtils.formatDateTime(
			getActivity(),
			msg.timestamp,
			DateUtils.FORMAT_SHOW_TIME));
		sb.append("] ");

		if (msg.direction == Message.DIRECTION_SENT) {
			String targetString;
			if(msg.target != null)
				targetString = msg.target.name;
			else if(msg.channel != null)
				targetString = msg.channel.name;
			else
				targetString = "Unknown";
			sb.append("To ");
			appendNameHighlight(targetString, sb);
		} else {
			if (msg.channelIds > 0) {
				sb.append("(C) ");
			}
			if (msg.treeIds > 0) {
				sb.append("(T) ");
			}
			
			String actorName;
			
			if(msg.actor == null || msg.actor.name == null)
				actorName = "Server";
			else
				actorName = msg.actor.name;
			
			appendNameHighlight(actorName, sb);
		}
		sb.append(": ");
		sb.append(Html.fromHtml(msg.message));
		
		addChatText(sb);
	}

	void sendMessage(final TextView v) {
		String text = v.getText().toString();
		if(text == null || text.equals("")) {
			return;
		}
		
		AsyncTask<String, Void, Void> messageTask = new AsyncTask<String, Void, Void>() {
			
			@Override
			protected void onPreExecute() {
				super.onPreExecute();
			}
			
			@Override
			protected Void doInBackground(String... params) {
				if(chatTarget == null) {
					channelProvider.sendChannelMessage(params[0]);
				} else {
					channelProvider.sendUserMessage(params[0], chatTarget);
				}
				return null;
			}
			
			@Override
			protected void onPostExecute(Void result) {
				super.onPostExecute(result);
				v.setText("");
			}
		};
		messageTask.execute(text);
	}

	void updateText() {
//		chatText.beginBatchEdit();
		chatText.setText("");
//		for (final String s : ServerList.client.chatList) {
//			chatText.append(s);
//		}
//		chatText.endBatchEdit();
//		chatText.post(new Runnable() {
//			@Override
//			public void run() {
//				chatText.scrollTo(0, chatText.getHeight());
//			}
//		});
	}
	
	public void clear() {
		if(chatText != null) {
			updateText();
		}
	}
	
	public void setChatTarget(User user) {
		chatTarget = user;
		if(chatTextEdit != null) {
			updateChatTargetText();
		}
	}
	
	/**
	 * Updates hint displaying chat target.
	 */
	public void updateChatTargetText() {		
		if(chatTarget == null) {
			chatTextEdit.setHint(R.string.messageToChannel);
		} else {
			chatTextEdit.setHint(getString(R.string.messageToUser, chatTarget.name));
		}
	}

	public void userStateUpdated(User user, UserState userState) {
		/*
		 *  Hi localizers,
		 *  
		 *  Please wait until I move all strings here into strings.xml before starting localization. Thanks.
		 *  
		 *  - Andrew
		 */
		
		final SpannableStringBuilder sb = new SpannableStringBuilder();
		sb.append("[");
		sb.append(DateUtils.formatDateTime(
			getActivity(),
			System.currentTimeMillis(),
			DateUtils.FORMAT_SHOW_TIME));
		sb.append("] ");
		
		User actor = null;
		if(userState.hasActor()) {
			actor = channelProvider.getUserWithIdentifier(userState.getActor());
		}
		
		// Connect action
		if(user == null) {
			appendNameHighlight(userState.getName(), sb);
			sb.append(" connected.");
			addChatText(sb);
			return;
		}
		
		// Channel move actions
		if(userState.hasChannelId() && userState.getChannelId() == channelProvider.getChannel().id) {
			appendNameHighlight(user.name, sb);
			sb.append(" moved in from ");
			appendNameHighlight(user.getChannel().name, sb); // This doesn't return the correct result. TODO fix!
			sb.append(" by ");
			if(actor != null) {
				appendNameHighlight(actor.name, sb);
			} else {
				sb.append("the server");
			}
			sb.append(".");
			addChatText(sb);
			return;
		}
		
		// Mute/deafen actions within the current user's channel
		if(user.getChannel().id == channelProvider.getChannel().id) {
			if(userState.hasSelfDeaf() || userState.hasSelfMute()) {
				if(userState.getSession() == channelProvider.getCurrentUser().session) {
					if(userState.getSelfMute() && userState.getSelfDeaf()) {
						sb.append("Muted and deafened.");
					} else if(userState.getSelfMute()) {
						sb.append("Muted.");
					} else {
						sb.append("Unmuted.");
					}
				} else {
					appendNameHighlight(user.name, sb);
					if(userState.getSelfMute() && userState.getSelfDeaf()) {
						sb.append(" is now muted and deafened.");
					} else if(userState.getSelfMute()) {
						sb.append(" is now muted.");
					} else {
						sb.append(" is now unmuted.");
					}
				}
				addChatText(sb);
				return;
			}
		}
	}
}
