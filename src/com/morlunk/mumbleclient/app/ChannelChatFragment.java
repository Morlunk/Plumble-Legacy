package com.morlunk.mumbleclient.app;

import android.app.Activity;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Editable;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextWatcher;
import android.text.util.Linkify;
import android.util.Log;
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
import com.morlunk.mumbleclient.Globals;
import com.morlunk.mumbleclient.R;
import com.morlunk.mumbleclient.service.model.User;

public class ChannelChatFragment extends SherlockFragment {
	
	private ChannelProvider channelProvider;
	private ScrollView chatScroll;
	private TextView chatText;
	private EditText chatTextEdit;
	private ImageButton sendButton;

	private User chatTarget;
	
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
		chatText.append(Html.fromHtml(text));
		chatText.append(Html.fromHtml("<br>"));
		
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
		
		// Linkify (convert pasted links to spannable) and convert back to HTML
		Spannable spanText = new SpannableString(text);
		Linkify.addLinks(spanText, Linkify.WEB_URLS);
		text = Html.toHtml(spanText);
		text = text.substring("<p dir=\"ltr\">".length(), text.length()-"</p>".length()-1); // Remove paragraph tags
		
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
