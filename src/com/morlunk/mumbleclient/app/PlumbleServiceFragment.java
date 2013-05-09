package com.morlunk.mumbleclient.app;

import android.app.Activity;

import com.actionbarsherlock.app.SherlockFragment;
import com.morlunk.mumbleclient.service.BaseServiceObserver;
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
	public void onStart() {
		super.onStart();
		
		if(serviceProvider.getService() != null)
			notifyServiceBound();
	}
	
	/**
	 * Notified the fragment that a service has been bound to the activity.
	 * Nothing will occur if the service is not bound.
	 */
	public void notifyServiceBound() {
		/* If we're not bound and the view is available, let the fragment know to configure itself. */
		if(!serviceBound && getView() != null) {
			onServiceBound(serviceProvider.getService());
			serviceBound = true;
		}
	}
	
	/**
	 * Notified the fragment that a service has been unbound to the activity.
	 * Nothing will occur if the service is bound.
	 */
	public void notifyServiceUnbound() {
		if(serviceBound) {
			onServiceUnbound(serviceProvider.getService());
			serviceBound = false;
		}
	}
	
	/**
	 * Convenience method to return the MumbleService from the ServiceProvider.
	 * @return The MumbleService if bound, null otherwise.
	 */
	public MumbleService getService() {
		return serviceProvider.getService();
	}
	
	protected BaseServiceObserver getServiceObserver() {
		return null; // Placeholder
	}
	
	protected void onServiceBound(MumbleService service) {
		if(getServiceObserver() != null)
			service.registerObserver(getServiceObserver());
	}
	
	protected void onServiceUnbound(MumbleService service) {
		if(getServiceObserver() != null)
			service.unregisterObserver(getServiceObserver());
	}
	
	/**
	 * Returns whether or not this fragment has performed the setup required after the service has been bound.
	 */
	public boolean hasBound() {
		return serviceBound;
	}
}
