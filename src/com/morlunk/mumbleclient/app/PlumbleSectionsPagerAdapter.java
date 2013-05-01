package com.morlunk.mumbleclient.app;

import java.util.Locale;

import android.content.Context;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.PagerAdapter;
import android.view.View;
import android.view.ViewGroup;

import com.morlunk.mumbleclient.R;

/**
 * A {@link PagerAdapter} that returns a fragment corresponding to one of the
 * primary sections of the app. Never destroys or creates fragments. Inspired by
 * Jake Wharton's adjacent-fragment-pager-sample.
 */
public class PlumbleSectionsPagerAdapter extends PagerAdapter {
	private Context context;

	private FragmentManager fragmentManager;
	private FragmentTransaction currentTransaction;
	private Fragment currentPrimaryItem;

	private ChannelListFragment listFragment;
	private ChannelChatFragment chatFragment;

	public PlumbleSectionsPagerAdapter(Context context,
			FragmentManager fragmentManager, ChannelListFragment listFragment,
			ChannelChatFragment chatFragment) {
		this.fragmentManager = fragmentManager;
		this.context = context;
		this.listFragment = listFragment;
		this.chatFragment = chatFragment;
	}

	private String getFragmentTag(int position) {
		return position == 0 ? ChannelActivity.LIST_FRAGMENT_TAG : ChannelActivity.CHAT_FRAGMENT_TAG;
	}
	
	@Override
	public int getCount() {
		return 2;
	}

	@Override
	public Object instantiateItem(View container, int position) {
		if (currentTransaction == null)
			currentTransaction = fragmentManager.beginTransaction();

		Fragment fragment = position == 0 ? listFragment : chatFragment;
		currentTransaction.add(container.getId(), fragment,
				getFragmentTag(position));
		if (fragment != currentPrimaryItem) {
			fragment.setMenuVisibility(false);
			fragment.setUserVisibleHint(false);
		}
		return fragment;
	}
	
	@Override
	public void destroyItem(ViewGroup container, int position, Object object) {
		
	}

	@Override
	public void finishUpdate(ViewGroup container) {
		if (currentTransaction != null) {
			currentTransaction.commitAllowingStateLoss();
			currentTransaction = null;
			fragmentManager.executePendingTransactions();
		}
	}

	@Override
	public boolean isViewFromObject(View arg0, Object arg1) {
		return ((Fragment) arg1).getView() == arg0;
	}

	@Override
	public void setPrimaryItem(ViewGroup container, int position, Object object) {
		Fragment fragment = (Fragment) object;
		if (fragment != currentPrimaryItem) {
			if (currentPrimaryItem != null) {
				currentPrimaryItem.setMenuVisibility(false);
				currentPrimaryItem.setUserVisibleHint(false);
			}
			if (fragment != null) {
				fragment.setMenuVisibility(true);
				fragment.setUserVisibleHint(true);
			}
			currentPrimaryItem = fragment;
		}
	}

	@Override
	public CharSequence getPageTitle(int position) {
		switch (position) {
		case 0:
			return context.getString(R.string.title_section1).toUpperCase(
					Locale.getDefault());
		case 1:
			return context.getString(R.string.title_section2).toUpperCase(
					Locale.getDefault());
		}
		return null;
	}
}
