package com.morlunk.mumbleclient.app;

import net.sf.mumble.MumbleProto.Reject;
import net.sf.mumble.MumbleProto.UserRemove;
import net.sf.mumble.MumbleProto.Reject.RejectType;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.ServiceConnection;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.morlunk.mumbleclient.R;
import com.morlunk.mumbleclient.app.ConnectedActivityLogic.Host;
import com.morlunk.mumbleclient.app.db.DbAdapter;
import com.morlunk.mumbleclient.app.db.Server;
import com.morlunk.mumbleclient.service.BaseServiceObserver;
import com.morlunk.mumbleclient.service.MumbleService;

/**
 * Base class for activities that want to access the MumbleService
 *
 * Note: Remember to consider ConnectedListActivity when modifying this class.
 *
 * @author Rantanen
 *
 */
public class ConnectedActivity extends SherlockFragmentActivity {
	private final Host logicHost = new Host() {
		@Override
		public boolean bindService(
			final Intent intent,
			final ServiceConnection mServiceConn,
			final int bindAutoCreate) {
			return ConnectedActivity.this.bindService(
				intent,
				mServiceConn,
				bindAutoCreate);
		}

		@Override
		public BaseServiceObserver createServiceObserver() {
			return ConnectedActivity.this.createServiceObserver();
		}

		@Override
		public void finish() {
			ConnectedActivity.this.finish();
		}

		@Override
		public Context getApplicationContext() {
			return ConnectedActivity.this.getApplicationContext();
		}

		@Override
		public MumbleService getService() {
			return mService;
		}

		@Override
		public void onConnected() {
			ConnectedActivity.this.onConnected();
		}

		@Override
		public void onConnecting() {
			ConnectedActivity.this.onConnecting();
		}

		@Override
		public void onDisconnected() {
			ConnectedActivity.this.onDisconnected();
		}

		@Override
		public void onServiceBound() {
			ConnectedActivity.this.onServiceBound();
		}

		@Override
		public void onSynchronizing() {
			ConnectedActivity.this.onSynchronizing();
		}

		@Override
		public void setService(final MumbleService service) {
			mService = service;
		}

		@Override
		public void unbindService(final ServiceConnection mServiceConn) {
			ConnectedActivity.this.unbindService(mServiceConn);
		}
	};

	private final ConnectedActivityLogic logic = new ConnectedActivityLogic(
		logicHost);

	protected MumbleService mService;
	protected BaseServiceObserver mObserver;

	protected BaseServiceObserver createServiceObserver() {
		return null;
	}

	protected void onConnected() {
	}

	protected void onConnecting() {
	}

	protected void onDisconnected() {
		final Object response = mService.getDisconnectResponse();
		
		if(response != null) {
			AlertDialog.Builder alertBuilder = new AlertDialog.Builder(this);
			if(response instanceof Reject) {
				Reject reject = (Reject)response;
				if(reject.getType() == RejectType.WrongUserPW || reject.getType() == RejectType.WrongServerPW) {		
					// Allow password entry
					final EditText passwordField = new EditText(this);
					passwordField.setHint(R.string.serverPassword);
					passwordField.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
					passwordField.setImeOptions(EditorInfo.IME_ACTION_GO);
					passwordField.setOnEditorActionListener(new OnEditorActionListener() {
						@Override
						public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
							if(actionId == EditorInfo.IME_ACTION_GO) {
								reconnectWithPassword(passwordField.getText().toString());
								return true;
							}
							return false;
						}
					});
					alertBuilder.setView(passwordField);

					alertBuilder.setTitle(reject.getReason());
					
					alertBuilder.setPositiveButton(R.string.retry, new OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							reconnectWithPassword(passwordField.getText().toString());
						}
					});
					alertBuilder.setOnCancelListener(new OnCancelListener() {
						@Override
						public void onCancel(DialogInterface dialog) {
							finish();
						}
					});
				} else {
					
					alertBuilder.setPositiveButton("Ok", new OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							finish();
						}
					});
					alertBuilder.setOnCancelListener(new OnCancelListener() {
						@Override
						public void onCancel(DialogInterface dialog) {
							finish();
						}
					});
					alertBuilder.setMessage(reject.getReason());
				}
			} else if (response instanceof UserRemove) {
				UserRemove remove = (UserRemove) response;

				alertBuilder.setTitle(R.string.disconnected);

				alertBuilder.setPositiveButton("Ok", new OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						finish();
					}
				});
				alertBuilder.setOnCancelListener(new OnCancelListener() {
					@Override
					public void onCancel(DialogInterface dialog) {
						finish();
					}
				});
				alertBuilder.setMessage(getResources().getString(R.string.kickedMessage, remove.getReason()));
			} else if(response instanceof String) {
				String responseString = (String) response;
				alertBuilder.setTitle(R.string.disconnected);

				alertBuilder.setPositiveButton("Ok", new OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						finish();
					}
				});
				alertBuilder.setOnCancelListener(new OnCancelListener() {
					@Override
					public void onCancel(DialogInterface dialog) {
						finish();
					}
				});
				alertBuilder.setMessage(responseString);
			}
			
			alertBuilder.show();
			
		} else if(mService.getConnectionState() == MumbleService.CONNECTION_STATE_DISCONNECTED){
			finish();
		}
	}
	
	private void reconnectWithPassword(String password) {
		Server server = mService.getConnectedServer();
		// Update server
		DbAdapter adapter = new DbAdapter(ConnectedActivity.this);
		adapter.open();
		adapter.updateServer(server.getId(), server.getName(), server.getHost(), server.getPort(), server.getUsername(), password);
		Server updatedServer = adapter.fetchServer(server.getId()); // Update server object again
		adapter.close();
		mService.connectToServer(updatedServer);
	}

	@Override
	protected void onPause() {
		super.onPause();
		logic.onPause();
	}

	@Override
	protected void onResume() {
		super.onResume();
		logic.onResume();
	}

	protected void onServiceBound() {
	}

	protected void onSynchronizing() {
	}
}
