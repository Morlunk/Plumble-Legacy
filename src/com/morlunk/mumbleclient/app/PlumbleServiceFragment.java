package com.morlunk.mumbleclient.app;

import android.app.Activity;
import android.os.Bundle;

import com.actionbarsherlock.app.SherlockFragment;
import com.morlunk.mumbleclient.service.MumbleService;

/**
 * A fragment class that is notified upon the successful establishment of a service bound to an activity.
 * If the fragment loads before the service is bound, the fragment will be notified of the binding and the appropriate calls will be made.
 * If the service is bound before the fragment is created, then the service will be used to set it up immediately.
 * The activity that this fragment will be bound to MUST implement {@link PlumbleServiceProvider}.
 * @author andrew
 */
public class PlumbleServiceFragment extends SherlockFragment {

	/**
	 * An interface that activities attached to a PlumbleServiceFragment must implement.
	 * @author andrew
	 */
	public interface PlumbleServiceProvider {
		public MumbleService getService();
	}
	
	private PlumbleServiceProvider serviceProvider;
	private boolean serviceBound = false;
	
	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);

		try {
			serviceProvider = (PlumbleServiceProvider) activity;
		} catch (ClassCastException e) {
			throw new ClassCastException(activity.toString()
					+ " must implement PlumbleServiceProvider!");
		}
	}
	
	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		
		if(serviceProvider.getService() != null)
			notifyServiceBound();
	}
	
	@Override
	public void onResume() {
		// TODO Auto-generated method stub
		super.onResume();
	}
	
	@Override
	public void onPause() {
		// TODO Auto-generated method stub
		super.onPause();
	}
	
	/**
	 * Notified the fragment that a service has been bound to the activity.
	 * Nothing will occur if the service is not bound.
	 */
	public void notifyServiceBound() {
		if(!serviceBound && serviceProvider.getService() != null) {
			onServiceBound();
			serviceBound = true;
		}
	}
	
	/**
	 * Convenience method to return the MumbleService from the ServiceProvider.
	 * @return The MumbleService if bound, null otherwise.
	 */
	public MumbleService getService() {
		return serviceProvider.getService();
	}
	
	protected void onServiceBound() {
		// Placeholder
	}
	
	/**
	 * Returns whether or not this fragment has performed the setup required after the service has been bound.
	 */
	public boolean hasBound() {
		return serviceBound;
	}
}
