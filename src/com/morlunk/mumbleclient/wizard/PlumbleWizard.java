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
	
	private ViewFlipper mViewFlipper;
	private Button mBackButton;
	private Button mNextButton;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		
		super.onCreate(savedInstanceState);
		setContentView(R.layout.wizard);
		
		mViewFlipper = (ViewFlipper) findViewById(R.id.wizardFlipper);
		mViewFlipper.setInAnimation(AnimationUtils.loadAnimation(this, android.R.anim.slide_in_left));
		mViewFlipper.setOutAnimation(AnimationUtils.loadAnimation(this, android.R.anim.slide_out_right));
		
		mBackButton = (Button) findViewById(R.id.wizardBack);
		mBackButton.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				if(mViewFlipper.getDisplayedChild() == 0) {
					finish();
				} else {
					mViewFlipper.setDisplayedChild(mViewFlipper.getDisplayedChild()-1);
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
					mViewFlipper.setDisplayedChild(mViewFlipper.getDisplayedChild()+1);
					updateButtonLabels();
				}
			}
		});
		
		updateButtonLabels();
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
