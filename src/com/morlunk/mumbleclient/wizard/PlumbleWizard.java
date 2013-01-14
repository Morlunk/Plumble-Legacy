package com.morlunk.mumbleclient.wizard;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.Button;

import com.morlunk.mumbleclient.R;

public class PlumbleWizard extends FragmentActivity {
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		
		super.onCreate(savedInstanceState);
		setContentView(R.layout.wizard_intro);
		
		Button cancelButton = (Button) findViewById(R.id.wizardCancel);
		cancelButton.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				finish();	
			}
		});
		
		Button nextButton = (Button) findViewById(R.id.wizardContinue);
		nextButton.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				startActivity(new Intent(PlumbleWizard.this, WizardCertificateActivity.class));
			}
		});
	}
	
	public static class WizardCertificateActivity extends FragmentActivity {
		
		@Override
		public void onCreate(Bundle arg0) {
			super.onCreate(arg0);
			setContentView(R.layout.wizard_certificate);

			Button backButton = (Button) findViewById(R.id.wizardBack);
			backButton.setOnClickListener(new OnClickListener() {
				
				@Override
				public void onClick(View v) {
					finish();
				}
			});
		}
	}
}
