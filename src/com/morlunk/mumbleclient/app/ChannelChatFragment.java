package com.morlunk.mumbleclient.app;

import net.sf.mumble.MumbleProto.UserRemove;
import net.sf.mumble.MumbleProto.UserState;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.RemoteException;
import android.text.Editable;
import android.text.Html;
import android.text.TextWatcher;
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

import com.morlunk.mumbleclient.R;
import com.morlunk.mumbleclient.service.BaseServiceObserver;
import com.morlunk.mumbleclient.service.model.Message;
import com.morlunk.mumbleclient.service.model.User;

public class ChannelChatFragment extends PlumbleServiceFragment {
	
	private BaseServiceObserver serviceObserver = new BaseServiceObserver() {
		@Override
		public void onMessageReceived(final Message msg) throws RemoteException {
			updateChat();
		}

		@Override
		public void onMessageSent(final Message msg) throws RemoteException {
			updateChat();
		}

		@Override
		public void onUserRemoved(final User user, UserRemove remove) throws RemoteException {
			updateChat();
		}

		@Override
		public void onUserStateUpdated(final User user, final UserState state) throws RemoteException {
			updateChat();
		}
	};
	
	private ScrollView chatScroll;
	private TextView chatText;
	private EditText chatTextEdit;
	private ImageButton sendButton;

	private User chatTarget;
	
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
	
	public void addChatMessage(String text) {
		if(chatText == null)
			return;
		
		chatText.append(Html.fromHtml(text));
		chatText.append(Html.fromHtml("<br>"));
		
		chatScroll.post(new Runnable() {
			
			@Override
			public void run() {
				chatScroll.smoothScrollTo(0, chatText.getHeight());
			}
		});
	}
	
	@Override
	public void onServiceBound() {
		clear();
		for(String message : getService().getChatMessages()) {
			if(message != null)
				addChatMessage(message);
		}
		getService().registerObserver(serviceObserver);
	}
	
	@Override
	public void onServiceUnbound() {
		getService().unregisterObserver(serviceObserver);
	}
	
	/**
	 * Updates the chat with latest messages from the service.
	 */
	public void updateChat() {
		for(String message : getService().getUnreadChatMessages()) {
			addChatMessage(message);
		}
		getService().clearUnreadChatMessages();
	}

	void sendMessage(final TextView v) {
		String text = v.getText().toString();
		if(text == null || text.equals("")) {
			return;
		}
		
		v.setText("");
		
		// Linkify (convert pasted links to spannable) and convert back to HTML
		/*
		Spannable spanText = new SpannableString(text);
		Linkify.addLinks(spanText, Linkify.WEB_URLS);
		text = Html.toHtml(spanText);
		
		TODO: Fix this. We don't always get the paragraph tags for whatever reason.
		text = text.substring("<p dir=\"ltr\">".length(), text.length()-"</p>".length()-1); // Remove paragraph tags
		*/
		AsyncTask<String, Void, Void> messageTask = new AsyncTask<String, Void, Void>() {
			
			@Override
			protected void onPreExecute() {
				super.onPreExecute();
			}
			
			@Override
			protected Void doInBackground(String... params) {
				if(chatTarget == null) {
					getService().sendChannelTextMessage(params[0], getService().getCurrentChannel());
				} else {
					getService().sendUserTextMessage(params[0], chatTarget);
				}
				return null;
			}
		};
		messageTask.execute(text);
	}

	void updateText() {
		chatText.setText("");
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
