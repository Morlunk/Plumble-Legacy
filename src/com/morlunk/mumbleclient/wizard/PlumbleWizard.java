package com.morlunk.mumbleclient.wizard;

import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ViewFlipper;

import com.morlunk.mumbleclient.R;

public class PlumbleWizard extends FragmentActivity {
	
	private static final String VIEW_FLIPPER_POSITION = "flipperPosition";
	
	private ViewFlipper mViewFlipper;
	private Button mBackButton;
	private Button mNextButton;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		
		super.onCreate(savedInstanceState);
		setContentView(R.layout.wizard);
		
		mViewFlipper = (ViewFlipper) findViewById(R.id.wizardFlipper);
		mViewFlipper.setOutAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_out_left));
		mViewFlipper.setInAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_in_right));
		
		mBackButton = (Button) findViewById(R.id.wizardBack);
		mBackButton.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				if(mViewFlipper.getDisplayedChild() == 0) {
					finish();
				} else {
					mViewFlipper.showPrevious();
					updateButtonLabels();
				}
			}
		});
		
		mNextButton = (Button) findViewById(R.id.wizardNext);
		mNextButton.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				if(mViewFlipper.getDisplayedChild() == mViewFlipper.getChildCount()-1) {
					finish();
				} else {
					mViewFlipper.showNext();
					updateButtonLabels();
				}
			}
		});
		
		if(savedInstanceState != null && savedInstanceState.containsKey(VIEW_FLIPPER_POSITION)) {
			mViewFlipper.setAnimateFirstView(false);
			mViewFlipper.setDisplayedChild(savedInstanceState.getInt(VIEW_FLIPPER_POSITION));
		}
		
		updateButtonLabels();
	}
	
	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		
		outState.putInt(VIEW_FLIPPER_POSITION, mViewFlipper.getDisplayedChild());
	}
	
	/**
	 * Updates the button labels depending on the user's position in the view stack.
	 */
	private void updateButtonLabels() {
		int position = mViewFlipper.getDisplayedChild();
		int maxPosition = mViewFlipper.getChildCount()-1;

		mBackButton.setText(position == 0 ? R.string.skip : R.string.back);
		mNextButton.setText(position == maxPosition ? R.string.finish : R.string.next);
	}
}
