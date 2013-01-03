package com.morlunk.mumbleclient.service;

import android.os.RemoteException;
import android.util.Log;

import com.morlunk.mumbleclient.Globals;

public abstract class AbstractHost {
	abstract class ProtocolMessage implements Runnable {
		@Override
		public final void run() {
			if (isDisabled()) {
				Log.w(
					Globals.LOG_TAG,
					"Ignoring message, Service is disconnected");
			}

			process();

			for (final BaseServiceObserver observer : getObservers()) {
				try {
					broadcast(observer);
				} catch (final RemoteException e) {
					Log.e(
						Globals.LOG_TAG,
						"Error while broadcasting service state",
						e);
				}
			}
		}

		protected abstract void broadcast(BaseServiceObserver observer)
			throws RemoteException;

		protected abstract Iterable<BaseServiceObserver> getObservers();

		protected abstract void process();
	}

	boolean disabled = false;

	public void disable() {
		this.disabled = true;
	}

	public boolean isDisabled() {
		return disabled;
	}
}
