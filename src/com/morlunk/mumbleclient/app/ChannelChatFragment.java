package com.morlunk.mumbleclient.app;

import android.app.Activity;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
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

	private ChannelProvider channelProvider;
	private ScrollView chatScroll;
	private TextView chatText;
	private EditText chatTextEdit;

	private User chatTarget;
	
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
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
	 * @see android.support.v4.app.Fragment#onResume()
	 */
	@Override
	public void onResume() {
		super.onResume();
		
		// Clear chat text. It'll reload from the data source.
		chatText.setText(null);
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
		
		chatTextEdit.setOnEditorActionListener(new OnEditorActionListener() {
			@Override
			public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
				sendMessage(v);
				return true;
			}
		});
		
		ImageButton sendButton = (ImageButton) view.findViewById(R.id.chatTextSend);
		sendButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				sendMessage(chatTextEdit);
			}
		});
		
		updateText();
		updateChatTargetText();
		return view;
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
			sb.append(targetString);
			sb.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.abs__holo_blue_light)), sb.length()-targetString.length(), sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
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
			
			sb.append(actorName);
			sb.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.abs__holo_blue_light)), sb.length()-actorName.length(), sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
		}
		sb.append(": ");
		sb.append(Html.fromHtml(msg.message));
		sb.append(Html.fromHtml("<br>"));
		
		chatText.append(sb);
		
		chatScroll.post(new Runnable() {
			
			@Override
			public void run() {
				chatScroll.smoothScrollTo(0, chatText.getHeight());
			}
		});
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
}
