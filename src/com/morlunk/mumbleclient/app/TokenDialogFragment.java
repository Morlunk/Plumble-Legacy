package com.morlunk.mumbleclient.app;

import java.util.List;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

import com.morlunk.mumbleclient.Globals;
import com.morlunk.mumbleclient.R;

public class TokenDialogFragment extends DialogFragment {

	interface TokenDialogFragmentProvider {
		public List<String> getTokens();
		public void addToken(String string);
		public void deleteToken(String string);
		public void updateAccessTokens(List<String> tokens);
	}
	
	private TokenDialogFragmentProvider tokenProvider;
	
	private List<String> tokens;
	
	private ListView tokenList;
	private TokenAdapter tokenAdapter;
	
	private EditText tokenField;
	
	public static TokenDialogFragment newInstance() {
		TokenDialogFragment dialogFragment = new TokenDialogFragment();
		return dialogFragment;
	}
	
	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		
		try {
			tokenProvider = (TokenDialogFragmentProvider)activity;
		} catch(ClassCastException exception) {
			throw new ClassCastException(activity.toString() + " must implement TokenDialogFragmentListener");
		}
		
		
		tokens = tokenProvider.getTokens();
		tokenAdapter = new TokenAdapter(activity, tokens);
	}
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		
		if(getDialog() != null)
			getDialog().setTitle(R.string.accessTokens);
		
		View view = inflater.inflate(R.layout.fragment_tokens, null, false);
		
		tokenList = (ListView) view.findViewById(R.id.tokenList);
		tokenList.setAdapter(tokenAdapter);
		
		tokenField = (EditText) view.findViewById(R.id.tokenField);
		tokenField.setOnEditorActionListener(new OnEditorActionListener() {
			
			@Override
			public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
				if(actionId == EditorInfo.IME_ACTION_SEND) {
					addToken();
					return true;
				}
				return false;
			}
		});
		
		Button addButton = (Button) view.findViewById(R.id.tokenAddButton);
		addButton.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				addToken();
			}
		});
		
		Button cancelButton = (Button) view.findViewById(R.id.tokenCloseButton);
		cancelButton.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				dismiss();
			}
		});
		
		return view;
	}
	
	@Override
	public void onDismiss(DialogInterface dialog) {
		super.onDismiss(dialog);
		
		if(tokenProvider != null) {
			tokenProvider.updateAccessTokens(tokens);
		}
	}
	
	@TargetApi(8)
	private void addToken() {
		String tokenText = tokenField.getText().toString().trim();
		
		if(tokenText.equals("")) {
			return;
		}
		
		tokenField.setText("");
		
		Log.i(Globals.LOG_TAG, "Adding token: "+tokenText);
		
		tokens.add(tokenText);
		tokenAdapter.notifyDataSetChanged();
		
		tokenProvider.addToken(tokenText);
		
		if(Build.VERSION.SDK_INT >= 8)
			tokenList.smoothScrollToPosition(tokens.size()-1);
	}
	
	class TokenAdapter extends ArrayAdapter<String> {

		public TokenAdapter(Context context,
				List<String> objects) {
			super(context, android.R.layout.simple_list_item_1, objects);
		}
		
		@Override
		public View getView(final int position, View convertView, ViewGroup parent) {
			View view = convertView;
			if(convertView == null) {
				view = getActivity().getLayoutInflater().inflate(R.layout.token_row, null, false);
			}
			
			final String token = getItem(position);
			
			TextView title = (TextView) view.findViewById(R.id.tokenItemTitle);
			title.setText(token);
			
			ImageButton deleteButton = (ImageButton) view.findViewById(R.id.tokenItemDelete);
			deleteButton.setOnClickListener(new OnClickListener() {
				
				@Override
				public void onClick(View v) {
					tokenProvider.deleteToken(token);
					tokens.remove(position);
					notifyDataSetChanged();
				}
			});
			
			return view;
		}
		
	}

}
