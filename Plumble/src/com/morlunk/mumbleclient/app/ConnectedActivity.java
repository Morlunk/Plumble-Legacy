package com.morlunk.mumbleclient.app;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.ServiceConnection;

import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.morlunk.mumbleclient.R;
import com.morlunk.mumbleclient.app.ConnectedActivityLogic.Host;
import com.morlunk.mumbleclient.service.IServiceObserver;
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
		public IServiceObserver createServiceObserver() {
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
	protected IServiceObserver mObserver;

	protected IServiceObserver createServiceObserver() {
		return null;
	}

	protected void onConnected() {
	}

	protected void onConnecting() {
	}

	protected void onDisconnected() {
		final String error = mService.getError();
		if (error != null) {
			AlertDialog.Builder alertBuilder = new AlertDialog.Builder(this);
			alertBuilder.setTitle(R.string.connectionRefused);
			
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
			
			alertBuilder.setMessage(error);
			alertBuilder.show();
		} else {
			finish();
		}
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
