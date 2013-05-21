package com.morlunk.mumbleclient.app;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Observable;
import java.util.Observer;

import net.sf.mumble.MumbleProto.PermissionDenied.DenyType;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.SearchManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.ServiceConnection;
import android.media.AudioManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Build.VERSION;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.RemoteException;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Toast;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import com.actionbarsherlock.widget.SearchView;
import com.github.espiandev.showcaseview.ShowcaseView;
import com.github.espiandev.showcaseview.ShowcaseView.ConfigOptions;
import com.morlunk.mumbleclient.Globals;
import com.morlunk.mumbleclient.R;
import com.morlunk.mumbleclient.Settings;
import com.morlunk.mumbleclient.app.PlumbleServiceFragment.PlumbleServiceProvider;
import com.morlunk.mumbleclient.app.TokenDialogFragment.TokenDialogFragmentProvider;
import com.morlunk.mumbleclient.app.db.Favourite;
import com.morlunk.mumbleclient.service.BaseServiceObserver;
import com.morlunk.mumbleclient.service.MumbleService;
import com.morlunk.mumbleclient.service.MumbleService.LocalBinder;
import com.morlunk.mumbleclient.service.model.Channel;
import com.morlunk.mumbleclient.service.model.User;


/**
 * An interface for the activity that manages the channel selection.
 * @author andrew
 *
 */
interface ChannelProvider {
	public void setChatTarget(User chatTarget);
}


public class ChannelActivity extends SherlockFragmentActivity implements PlumbleServiceProvider, ChannelProvider, TokenDialogFragmentProvider, Observer {

	/**
	 * Fragment tags
	 */
	public static final String LIST_FRAGMENT_TAG = "listFragment";
	public static final String CHAT_FRAGMENT_TAG = "chatFragment";
	
	public static final String JOIN_CHANNEL = "join_channel";
	public static final String SAVED_STATE_VISIBLE_CHANNEL = "visible_channel";
	public static final String SAVED_STATE_CHAT_TARGET = "chat_target";
	public static final Integer PROXIMITY_SCREEN_OFF_WAKE_LOCK = 32; // Undocumented feature! This will allow us to enable the phone proximity sensor.
	
	/**
	 * The MumbleService instance that drives this activity's data.
	 */
	private MumbleService mService;
	
	/**
	 * An observer that monitors the state of the service.
	 */
	private ChannelServiceObserver mObserver;
	
	/**
	 * Management of service connection state.
	 */
	private ServiceConnection conn = new ServiceConnection() {

		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			LocalBinder localBinder = (LocalBinder)service;
			mObserver = new ChannelServiceObserver();
			mService = localBinder.getService();
			mService.registerObserver(mObserver);
			listFragment.notifyServiceBound();
			chatFragment.notifyServiceBound();
			onConnected();
		}
		
		@Override
		public void onServiceDisconnected(ComponentName name) {			
			finish();
		}
	};
	
    /**
     * The {@link android.support.v4.view.PagerAdapter} that will provide fragments for each of the
     * sections. We use a {@link android.support.v4.app.FragmentPagerAdapter} derivative, which will
     * keep every loaded fragment in memory. If this becomes too memory intensive, it may be best
     * to switch to a {@link android.support.v4.app.FragmentStatePagerAdapter}.
     */
    PlumbleSectionsPagerAdapter mSectionsPagerAdapter;

    /**
     * The {@link ViewPager} that will host the section contents.
     */
    private ViewPager mViewPager;
    
    // Tabs
    private View channelsIndicator;
    private View chatIndicator;
    
    private MenuItem fullscreenButton;
    
    // Favourites
    private MenuItem searchItem;
    private MenuItem mutedButton;
    private MenuItem deafenedButton;
    
    // User control
    private MenuItem userRegisterItem;
    private MenuItem userCommentItem;
    private MenuItem userInformationItem;
	
	private User chatTarget;
	
	private Button mTalkButton;
	private View pttView;

	// Fragment references (split view exclusive)
	private ChannelListFragment listFragment;
	private ChannelChatFragment chatFragment;
	private View leftSplit;
	private View rightSplit;
	
	// Proximity sensor
	private WakeLock proximityLock;
	private Settings settings;
	
	public final DialogInterface.OnClickListener onDisconnectConfirm = new DialogInterface.OnClickListener() {
		@Override
		public void onClick(final DialogInterface dialog, final int which) {
			disconnect();
		}
	};
	
    @Override
    public void onCreate(Bundle savedInstanceState) {    	
		settings = Settings.getInstance(this);
		settings.addObserver(this);
		
		// Use theme from settings
		int theme = 0;
		if(settings.getTheme().equals(Settings.ARRAY_THEME_LIGHTDARK)) {
			theme = R.style.Theme_Sherlock_Light_DarkActionBar;
		} else if(settings.getTheme().equals(Settings.ARRAY_THEME_DARK)) {
			theme = R.style.Theme_Sherlock;
		}
		setTheme(theme);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_channel);

    	// Bind to service
    	Intent serviceIntent = new Intent(this, MumbleService.class);
		bindService(serviceIntent, conn, 0);
        
        // Handle differences in CallMode
        
        String callMode = settings.getCallMode();
        
        if(callMode.equals(Settings.ARRAY_CALL_MODE_SPEAKER)) {
    		setVolumeControlStream(AudioManager.STREAM_MUSIC);
        } else if(callMode.equals(Settings.ARRAY_CALL_MODE_VOICE)) {
        	setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);
        }
    	
    	// Set up proximity sensor
    	PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
    	proximityLock = powerManager.newWakeLock(PROXIMITY_SCREEN_OFF_WAKE_LOCK, Globals.LOG_TAG);
        
        AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        audioManager.setMode(AudioManager.MODE_IN_CALL);
        
        // Set up PTT button.
    	
    	mTalkButton = (Button) findViewById(R.id.pushtotalk);
    	pttView = findViewById(R.id.pushtotalk_view);
    	mTalkButton.setOnTouchListener(new OnTouchListener() {
    		
    		private static final int TOGGLE_INTERVAL = 250; // 250ms is the interval needed to toggle push to talk.
    		private long lastTouch = 0;
			
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				if(mService == null) {
					return false;
				}
				
				if(event.getAction() == MotionEvent.ACTION_DOWN && !settings.isPushToTalkToggle()) {
					setPushToTalk(true);
				} else if(event.getAction() == MotionEvent.ACTION_UP) {
					if(settings.isPushToTalkToggle())
						setPushToTalk(!mService.isRecording());
					else {
						if(System.currentTimeMillis()-lastTouch <= TOGGLE_INTERVAL) {
							// Do nothing. We leave the push to talk on, as it has toggled.
						} else {
							setPushToTalk(false);
							lastTouch = System.currentTimeMillis();
						}
					}
				}
				
				return true; // We return true so that the selector that changes the background does not fire.
			}
		});
    	
        updatePTTConfiguration();
        
        mViewPager = (ViewPager) findViewById(R.id.pager);
		FragmentManager fragmentManager = getSupportFragmentManager();
		
		listFragment = (ChannelListFragment) fragmentManager.findFragmentByTag(LIST_FRAGMENT_TAG);
		chatFragment = (ChannelChatFragment) fragmentManager.findFragmentByTag(CHAT_FRAGMENT_TAG);
		
		FragmentTransaction remove = fragmentManager.beginTransaction();
		if(listFragment == null)
			listFragment = new ChannelListFragment();
		else
			remove.remove(listFragment);
		
		if(chatFragment == null)
			chatFragment = new ChannelChatFragment();
		else
			remove.remove(chatFragment);
		
		if(!remove.isEmpty()) {
			remove.commit();
			fragmentManager.executePendingTransactions();
		}
		
		if(mViewPager != null) {
			// Set up the ViewPager with the sections adapter.
            mViewPager.setOnPageChangeListener(new OnPageChangeListener() {
				
				@Override
				public void onPageSelected(int arg0) {
					// Hide keyboard if moving to channel list.
					if(arg0 == 0) {
						InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
			            imm.hideSoftInputFromWindow(mViewPager.getApplicationWindowToken(), 0);
					}
					// Update indicator
					channelsIndicator.setVisibility(arg0 == 0 ? View.VISIBLE : View.INVISIBLE);
					chatIndicator.setVisibility(arg0 == 1 ? View.VISIBLE : View.INVISIBLE);
				}
				
				@Override
				public void onPageScrolled(int arg0, float arg1, int arg2) { }
				
				@Override
				public void onPageScrollStateChanged(int arg0) { }
			});
            
            // Set up tabs
            getSupportActionBar().setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM);
            
            View channelTabView = getLayoutInflater().inflate(R.layout.channel_tab_view, null);
            channelsIndicator = channelTabView.findViewById(R.id.tab_channels_indicator);
            chatIndicator = channelTabView.findViewById(R.id.tab_chat_indicator);
            
            ImageButton channelsButton = (ImageButton) channelTabView.findViewById(R.id.tab_channels);
            ImageButton chatButton = (ImageButton) channelTabView.findViewById(R.id.tab_chat);
            channelsButton.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					mViewPager.setCurrentItem(0, true);
				}
			});
            chatButton.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					mViewPager.setCurrentItem(1, true);
				}
			});
            
            getSupportActionBar().setCustomView(channelTabView);
            // Setup a pager that will return a fragment for each of the two primary sections of the app.
            mSectionsPagerAdapter = new PlumbleSectionsPagerAdapter(this, getSupportFragmentManager(), listFragment, chatFragment);
            mViewPager.setAdapter(mSectionsPagerAdapter);
            
        } else {
        	// Create tablet UI
	        leftSplit = findViewById(R.id.left_split);
	        rightSplit = findViewById(R.id.right_split);
	        fragmentManager.beginTransaction()
	        	.add(R.id.chat_fragment, chatFragment, CHAT_FRAGMENT_TAG)
	        	.add(R.id.list_fragment, listFragment, LIST_FRAGMENT_TAG)
	        	.commit();
        }
        
        if(savedInstanceState != null) {
			chatTarget = (User) savedInstanceState.getParcelable(SAVED_STATE_CHAT_TARGET);
        }
    }
    
    // Settings observer
    @Override
    public void update(Observable observable, Object data) {
    	if(data == Settings.OBSERVER_KEY_ALL) {
        	updatePTTConfiguration(); // Update push-to-talk
    	}
    }
    
    private void updatePTTConfiguration() {
    	pttView.setVisibility(settings.isPushToTalk() && settings.isPushToTalkButtonShown() ? View.VISIBLE : View.GONE);
    }
    
    public void setPushToTalk(final boolean talking) {
    	if(mService.isRecording() != talking)
        	mService.setPushToTalk(talking);
    	
    	int pushToTalkBackground = mViewPager != null ? R.color.push_to_talk_background : 0; // Use special 'second action bar' look for background of paged.
    	
    	if(pttView != null)
    		pttView.setBackgroundResource(talking ? R.color.holo_blue_light : pushToTalkBackground);
    }
    
    @Override
    protected void onSaveInstanceState(Bundle outState) {
    	super.onSaveInstanceState(outState);
		
		if(chatTarget != null)
			outState.putParcelable(SAVED_STATE_CHAT_TARGET, chatTarget);
    }
    
    @Override
    protected void onResume() {
    	super.onResume();
		
    	if(settings.getCallMode().equals(Settings.ARRAY_CALL_MODE_VOICE))
    		setProximityEnabled(true);
    		
    	
        if(mService != null && mService.getCurrentUser() != null)
        	updateMuteDeafenMenuItems(mService.isMuted(), mService.isDeafened());
        
        // Clear chat notifications when activity is re-opened
        if(mService != null && settings.isChatNotifyEnabled()) {
        	mService.setActivityVisible(true);
        	mService.clearChatNotification();
        }
    }
    
    @Override
    protected void onPause() {
    	super.onPause();
    	
    	if(settings.getCallMode().equals(Settings.ARRAY_CALL_MODE_VOICE))
    		setProximityEnabled(false);
    	
    	if(mService != null) {
        	mService.setActivityVisible(false);
        	
        	// Turn off push to talk when rotating so it doesn't get stuck on, except if it's in toggled state.
        	//if(settings.isPushToTalk() && !mTalkToggleBox.isChecked()) {
        	//	mService.setRecording(false);
        	//}
    	}
    }
    
    @Override
    protected void onDestroy() {
    	// Unbind to service
		mService.unregisterObserver(mObserver);
		unbindService(conn);
		
		listFragment.notifyServiceUnbound();
		chatFragment.notifyServiceUnbound();
		
    	super.onDestroy();
    }
    
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
    	// Only show favourites and access tokens (DB-related) if the connected server has a DB representation (non-public).
    	MenuItem fullscreenItem = menu.findItem(R.id.menu_fullscreen);
    	MenuItem favouritesViewItem = menu.findItem(R.id.menu_view_favorites_button);
    	MenuItem accessTokensItem = menu.findItem(R.id.menu_access_tokens_button);
    	
    	userRegisterItem = menu.findItem(R.id.menu_user_register);
    	userCommentItem = menu.findItem(R.id.menu_user_comment);
    	userInformationItem = menu.findItem(R.id.menu_user_information);
    	
    	if(mService != null &&
    			mService.getConnectedServer() != null) {
    		
    		favouritesViewItem.setVisible(!mService.isConnectedServerPublic());
    		accessTokensItem.setVisible(!mService.isConnectedServerPublic());
    	}
    	
    	fullscreenItem.setVisible(mViewPager == null); // Only show fullscreen option if in tablet mode
    	
    	return super.onPrepareOptionsMenu(menu);
    }
    
    @TargetApi(Build.VERSION_CODES.FROYO) 
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getSupportMenuInflater().inflate(R.menu.activity_channel, menu);
        
        searchItem = menu.findItem(R.id.menu_search);
        
        if(mViewPager == null)
        	fullscreenButton = menu.findItem(R.id.menu_fullscreen);
        
        if(VERSION.SDK_INT >= 8) { // SearchManager supported by Froyo+.
            SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
            SearchView searchView = (SearchView) menu.findItem(R.id.menu_search).getActionView();
            searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
        } else {
        	searchItem.setVisible(false);
        }
        
        mutedButton = menu.findItem(R.id.menu_mute_button);
        deafenedButton = menu.findItem(R.id.menu_deafen_button);
        
        if(mService != null &&
        		mService.getCurrentUser() != null) {
        	updateMuteDeafenMenuItems(mService.isMuted(), mService.isDeafened());
        }
        
        return true;
    }
    
	@Override
    protected void onNewIntent(Intent intent) {
    	super.onNewIntent(intent);
    	
		// Join channel selected in search suggestions if present
		if(intent != null &&
				intent.getAction() != null &&
				intent.getAction().equals(Intent.ACTION_SEARCH)) {
			String resultType = (String) intent.getSerializableExtra(SearchManager.EXTRA_DATA_KEY);
			Uri data = intent.getData();
			
			if(resultType.equals(ChannelSearchProvider.INTENT_DATA_CHANNEL)) {
				int channelId = Integer.parseInt(data.getLastPathSegment());
				Channel channel = mService.getChannel(channelId);
				listFragment.scrollToChannel(channel);
			} else if(resultType.equals(ChannelSearchProvider.INTENT_DATA_USER)) {
				int session = Integer.parseInt(data.getLastPathSegment());
				User user = mService.getUser(session);
				listFragment.scrollToUser(user);
			}
			
            if(searchItem != null)
            	searchItem.collapseActionView();
		}
    }

	@Override
	public MumbleService getService() {
		return mService;
	}
    
    /**
     * Updates the 'muted' and 'deafened' action bar icons to reflect the audio status.
     */
    private void updateMuteDeafenMenuItems(boolean muted, boolean deafened) {
    	if(mutedButton == null || deafenedButton == null)
    		return;

    	mutedButton.setIcon(!muted ? R.drawable.ic_action_microphone : R.drawable.ic_microphone_muted_strike);
    	deafenedButton.setIcon(!deafened ? R.drawable.ic_action_audio_on : R.drawable.ic_action_audio_muted);
    }
    
    /**
     * Used to control the user settings shown when registered.
     */
    private void updateUserControlMenuItems() {
    	if(mService == null || 
    			mService.getCurrentUser() == null || 
    			userRegisterItem == null || 
    			userCommentItem == null || 
    			userInformationItem == null)
    		return;
    	
		boolean userRegistered = mService.getCurrentUser().isRegistered;
		userRegisterItem.setEnabled(!userRegistered);
		userCommentItem.setEnabled(userRegistered);
		userInformationItem.setEnabled(userRegistered);
    }
    
    /* (non-Javadoc)
     * @see android.app.Activity#onOptionsItemSelected(android.view.MenuItem)
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	
    	switch (item.getItemId()) {
		case R.id.menu_mute_button:
			if(!mService.isMuted()) {
				// Switching to muted
				updateMuteDeafenMenuItems(true, mService.isDeafened());
			} else {
				// Switching to unmuted
				updateMuteDeafenMenuItems(false, false);
			}
			mService.setMuted(!mService.isMuted());
			return true;
		case R.id.menu_deafen_button:
			updateMuteDeafenMenuItems(!mService.isDeafened(), !mService.isDeafened());
			mService.setDeafened(!mService.isDeafened());
			return true;
		case R.id.menu_fullscreen_chat:
			rightSplit.setVisibility(rightSplit.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
			leftSplit.setVisibility(View.VISIBLE);
			fullscreenButton.setIcon(R.drawable.ic_action_unfullscreen);
			return true;
		case R.id.menu_fullscreen_channel:
			leftSplit.setVisibility(leftSplit.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
			rightSplit.setVisibility(View.VISIBLE);
			fullscreenButton.setIcon(R.drawable.ic_action_unfullscreen);
			return true;
		case R.id.menu_fullscreen:
			if(leftSplit.getVisibility() == View.GONE || rightSplit.getVisibility() == View.GONE) {
				leftSplit.setVisibility(View.VISIBLE);
				rightSplit.setVisibility(View.VISIBLE);
				fullscreenButton.setIcon(R.drawable.ic_action_fullscreen);
			}
			return true;
		case R.id.menu_view_favorites_button:
			showFavouritesDialog();
			return true;
		case R.id.menu_user_register:
			new AsyncTask<Void, Void, Void>() {
				@Override
				protected Void doInBackground(Void... params) {
					mService.registerSelf();
					return null;
				}
				
				protected void onPostExecute(Void result) {
					Toast.makeText(ChannelActivity.this, R.string.registerSelfSuccess, Toast.LENGTH_SHORT).show();
				};
			}.execute();
			return true;
		case R.id.menu_user_comment:
			// TODO
			Toast.makeText(this, R.string.coming_soon, Toast.LENGTH_SHORT).show();
			return true;
		case R.id.menu_user_information:
			// TODO
			Toast.makeText(this, R.string.coming_soon, Toast.LENGTH_SHORT).show();
			return true;
		case R.id.menu_clear_chat:
			mService.clearChat();
			chatFragment.clear();
			return true;
		case R.id.menu_search:
			return false;
		case R.id.menu_access_tokens_button:
			TokenDialogFragment dialogFragment = TokenDialogFragment.newInstance();
			//if(mViewPager != null) {
				// Phone
				//getSupportFragmentManager().beginTransaction().replace(R.id.pager, dialogFragment).commit();
			//} else {
				// Tablet
				dialogFragment.show(getSupportFragmentManager(), "tokens");
			//}
			return true;
		case R.id.menu_amplifier:
			AmplifierDialogFragment amplifierDialogFragment = AmplifierDialogFragment.newInstance();
			amplifierDialogFragment.show(getSupportFragmentManager(), "amplifier");
			return true;
		case R.id.menu_preferences:
			Intent intent = new Intent(this, Preferences.class);
			startActivity(intent);
			return true;
		case R.id.menu_disconnect_item:
			disconnect();
			return true;
		}
    	
    	return super.onOptionsItemSelected(item);
    }
    
    @Override
	public boolean onKeyDown(final int keyCode, final KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
			final AlertDialog.Builder b = new AlertDialog.Builder(this);
			b.setTitle(R.string.disconnect);
			b.setMessage(R.string.disconnectSure);
			b.setPositiveButton(android.R.string.yes, onDisconnectConfirm);
			b.setNegativeButton(android.R.string.no, null);
			b.show();

			return true;
		}
		
		// Push to talk hardware key
		if(settings.isPushToTalk() && 
				keyCode == settings.getPushToTalkKey() && 
				event.getAction() == KeyEvent.ACTION_DOWN) {
			setPushToTalk(true);
			return true;
		}

		return super.onKeyDown(keyCode, event);
	}
    
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
		// Push to talk hardware key
    	if(settings.isPushToTalk() && 
				keyCode == settings.getPushToTalkKey() && 
				event.getAction() == KeyEvent.ACTION_UP) {
			setPushToTalk(false);
			return true;
		}
    	
    	return super.onKeyUp(keyCode, event);
    }
	
	/**
	 * Sends the passed access tokens to the server.
	 */
	@SuppressWarnings("unchecked")
	@Override
	public void updateAccessTokens(List<String> tokens) {
		AsyncTask<List<String>, Void, Void> accessTask = new AsyncTask<List<String>, Void, Void>() {

			@Override
			protected Void doInBackground(List<String>... params) {
				List<String> tokens = params[0];
				mService.sendAccessTokens(tokens);
				return null;
			}
		};
		accessTask.execute(tokens);
	}

	@Override
	public List<String> getTokens() {
		return mService.getDatabaseAdapter().fetchAllTokens(mService.getConnectedServer().getId());
	}

	@Override
	public void addToken(String string) {
		mService.getDatabaseAdapter().createToken(mService.getConnectedServer().getId(), string);
	}

	@Override
	public void deleteToken(String string) {
		mService.getDatabaseAdapter().deleteToken(mService.getConnectedServer().getId(), string);
	};
    
    /**
	 * Handles activity initialization when the Service has connected.
	 *
	 * Should be called when there is a reason to believe that the connection
	 * might have became valid. The connection MUST be established but other
	 * validity criteria may still be unfilled such as server synchronization
	 * being complete.
	 *
	 * The method implements the logic required for making sure that the
	 * Connected service is in such a state that it fills all the connection
	 * criteria for ChannelList.
	 *
	 * The method also takes care of making sure that its initialization code
	 * is executed only once so calling it several times doesn't cause problems.
	 */
    
	protected void onConnected() {
		// Tell the service that we are now visible.
        mService.setActivityVisible(true);
        
        // Update user control
        updateUserControlMenuItems();
		
		// Restore push to talk state, if toggled. Otherwise make sure it's turned off.
		if(settings.isPushToTalk() && 
				mService.isRecording()) {
			if(settings.isPushToTalkToggle() && settings.isPushToTalkButtonShown())
				setPushToTalk(true);
			else
				mService.setPushToTalk(false);
		}
		
		if(settings.isPushToTalk() &&
				mService.isRecording())

		if(chatTarget != null) {
			listFragment.setChatTarget(chatTarget);
			chatFragment.setChatTarget(chatTarget);
		}
		
		// Showcase hints
		List<ShowcaseView> showcaseViews = new ArrayList<ShowcaseView>();
		if(settings.isPushToTalk() && settings.isPushToTalkButtonShown()) {
			ConfigOptions pttConfig = new ConfigOptions();
			pttConfig.shotType = ShowcaseView.TYPE_ONE_SHOT;
			pttConfig.showcaseId = Globals.SHOWCASE_PUSH_TO_TALK;
			showcaseViews.add(ShowcaseView.insertShowcaseView(pttView, this, R.string.hint_ptt, R.string.hint_ptt_summary, pttConfig));
		}
		
		if(mViewPager != null) {
			ConfigOptions switcherConfig = new ConfigOptions();
			switcherConfig.shotType = ShowcaseView.TYPE_ONE_SHOT;
			switcherConfig.showcaseId = Globals.SHOWCASE_SWITCHER;
			showcaseViews.add(ShowcaseView.insertShowcaseView(ShowcaseView.ITEM_ACTION_HOME, 0, this, R.string.hint_switching, R.string.hint_switching_summary, switcherConfig));
		}
		
		ShowcaseViewQueue queue = new ShowcaseViewQueue(showcaseViews);
		queue.queueNext();
	}
	
	protected void disconnect() {
		new AsyncTask<Void, Void, Void>() {
			@Override
			protected Void doInBackground(Void... params) {
				mService.disconnect();
				return null;
			}
		}.execute();
	}
	
	/**
	 * http://stackoverflow.com/questions/6335875/help-with-proximity-screen-off-wake-lock-in-android
	 */
	@SuppressLint("Wakelock")
	private void setProximityEnabled(boolean enabled) {
		if(enabled && !proximityLock.isHeld()) {
			proximityLock.acquire();
		} else if(!enabled && proximityLock.isHeld()) {
			try {
				Class<?> lockClass = proximityLock.getClass();
				Method release = lockClass.getMethod("release", int.class);
				release.invoke(proximityLock, 1);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	private void showFavouritesDialog() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		
		builder.setTitle(R.string.favorites);
		
		List<CharSequence> items = new ArrayList<CharSequence>();
		final List<Favourite> activeFavourites = new ArrayList<Favourite>(mService.getFavourites());
		
		for(Favourite favourite : mService.getFavourites()) {
			int channelId = favourite.getChannelId();
			Channel channel = mService.getChannel(channelId);
			
			if(channel != null) {
				items.add(channel.name);
			} else {
				// TODO remove the favourite from DB here if channel is not found.
				activeFavourites.remove(favourite);
			}
		}
		
		if(items.size() > 0) {
			builder.setItems(items.toArray(new CharSequence[items.size()]), new OnClickListener() {
				
				@Override
				public void onClick(DialogInterface dialog, int which) {
					Favourite favourite = activeFavourites.get(which);
					final Channel channel = mService.getChannel(favourite.getChannelId());
					
					new AsyncTask<Channel, Void, Void>() {
						
						@Override
						protected Void doInBackground(Channel... params) {
							mService.joinChannel(params[0].id);
							return null;
						}
					}.execute(channel);
				}
			});
		} else {
			builder.setMessage(R.string.noFavorites);
		}
		
		builder.setNegativeButton(android.R.string.cancel, null);
		
		builder.show();
	}
	
	@Override
	public void setChatTarget(User chatTarget) {
		this.chatTarget = chatTarget;
		chatFragment.setChatTarget(chatTarget);
		
		if(mViewPager != null && chatTarget != null)
			mViewPager.setCurrentItem(1, true); // Scroll over to chat view if targeting a new user
	}
	
	/**
	 * @param reason 
	 * @param valueOf
	 */
	private void permissionDenied(String reason, DenyType denyType) {
		Toast.makeText(getApplicationContext(), R.string.permDenied, Toast.LENGTH_SHORT).show();
	}

    class ChannelServiceObserver extends BaseServiceObserver {
    	
    	@Override
    	public void onConnectionStateChanged(int state) throws RemoteException {
    		if(state == MumbleService.CONNECTION_STATE_DISCONNECTED)
    			finish();
    	}
    	
		@Override
		public void onCurrentUserUpdated() throws RemoteException {
			updateMuteDeafenMenuItems(mService.getCurrentUser().selfMuted, mService.getCurrentUser().selfDeafened);
	        updateUserControlMenuItems();
		}
		
		/* (non-Javadoc)
		 * @see com.morlunk.mumbleclient.service.BaseServiceObserver#onPermissionDenied(int)
		 */
		@Override
		public void onPermissionDenied(String reason, int denyType) throws RemoteException {
			permissionDenied(reason, DenyType.valueOf(denyType));
		}
	}
}
