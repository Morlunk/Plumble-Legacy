package com.morlunk.mumbleclient.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import com.actionbarsherlock.app.SherlockDialogFragment;
import com.morlunk.mumbleclient.R;
import com.morlunk.mumbleclient.app.db.DbAdapter;
import com.morlunk.mumbleclient.app.db.Server;
import com.morlunk.mumbleclient.service.MumbleService;

public class ServerInfo extends SherlockDialogFragment {
	private EditText nameEdit;
	private EditText hostEdit;
	private EditText portEdit;
	private EditText usernameEdit;

	private Server server;
	
	private ServerInfoListener serverListener;
	
	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		
		if(getArguments() != null) {
			long serverId = this.getArguments().getLong("serverId", -1);
			if(serverId != -1) {
				DbAdapter adapter = new DbAdapter(activity);
				adapter.open();
				server = adapter.fetchServer(serverId);
				adapter.close();
			}
		}
		
		try {
			serverListener = (ServerInfoListener)activity;
		} catch(ClassCastException exception) {
			throw new ClassCastException(activity.toString() + " must implement ServerInfoListener");
		}
	}
	
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		LayoutInflater inflater = (LayoutInflater) getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		AlertDialog.Builder alertBuilder = new AlertDialog.Builder(getActivity());

		View view = inflater.inflate(R.layout.server_add, null, false);
		alertBuilder.setView(view);
		
		nameEdit = (EditText) view.findViewById(R.id.serverNameEdit);
		hostEdit = (EditText) view.findViewById(R.id.serverHostEdit);
		portEdit = (EditText) view.findViewById(R.id.serverPortEdit);
		usernameEdit = (EditText) view.findViewById(R.id.serverUsernameEdit);
		if (server != null) {
			nameEdit.setText(server.getName());
			hostEdit.setText(server.getHost());
			portEdit.setText(String.valueOf(server.getPort()));
			usernameEdit.setText(server.getUsername());
		}
		
		alertBuilder.setPositiveButton(server != null ? R.string.save : R.string.add, new OnClickListener() {
			
			@Override
			public void onClick(DialogInterface dialog, int which) {
				save();
			}
		});
		
		alertBuilder.setNegativeButton(android.R.string.cancel, new OnClickListener() {
			
			@Override
			public void onClick(DialogInterface dialog, int which) {
				dismiss();
			}
		});
		
		alertBuilder.setOnCancelListener(new OnCancelListener() {
			
			@Override
			public void onCancel(DialogInterface dialog) {
				dismiss();
			}
		});
		
		alertBuilder.setTitle(server != null ? R.string.serverChange : R.string.serverAdd);
		return alertBuilder.create();
	}
	
	public void save() {

		String name = (nameEdit).getText().toString().trim();
		String host = (hostEdit).getText().toString().trim();

		int port;
		try {
			port = Integer.parseInt((portEdit).getText().toString());
		} catch (final NumberFormatException ex) {
			port = 64738;
		}

		String username = (usernameEdit).getText().toString().trim();

		DbAdapter db = new DbAdapter(getActivity());
		
		db.open();
		if (server != null) {
			db.updateServer(server.getId(), name, host, port, username, server.getPassword());
		} else {
			db.createServer(name, host, port, username, "");
		}
		db.close();

		dismiss();
		serverListener.serverInfoUpdated();
	}
}
