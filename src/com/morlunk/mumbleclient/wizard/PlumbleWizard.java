package com.morlunk.mumbleclient.wizard;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;

import com.morlunk.mumbleclient.R;

public class PlumbleWizard extends FragmentActivity {
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		getSupportFragmentManager().beginTransaction().replace(android.R.id.content, new WizardIntroductionFragment()).commit();
	}
	
	public static class WizardIntroductionFragment extends Fragment {
		
		public WizardIntroductionFragment() {
			
		}
		
		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container,
				Bundle savedInstanceState) {
			View view = inflater.inflate(R.layout.wizard_intro, container, false);
			
			Button cancelButton = (Button) view.findViewById(R.id.wizardCancel);
			cancelButton.setOnClickListener(new OnClickListener() {
				
				@Override
				public void onClick(View v) {
					getActivity().finish();	
				}
			});
			
			return view;
		}
		
		
	}
}
