package com.morlunk.mumbleclient.app;

import android.app.Activity;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

import com.morlunk.mumbleclient.R;
import com.morlunk.mumbleclient.Settings;
import com.morlunk.mumbleclient.service.MumbleService;

public class AmplifierDialogFragment extends DialogFragment implements OnSeekBarChangeListener {
	
	private Settings settings;
	private SeekBar recordingMultiplierBar;
	private TextView recordingMultiplierText;
	
	public static AmplifierDialogFragment newInstance() {
		return new AmplifierDialogFragment();
	}
	
	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		
		settings = new Settings(activity);
	}
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		getDialog().setTitle(R.string.amplifier);
		View view = inflater.inflate(R.layout.dialog_amplifier, null, false);
		recordingMultiplierBar = (SeekBar) view.findViewById(R.id.recordingMultiplier);
		recordingMultiplierBar.setOnSeekBarChangeListener(this);
		recordingMultiplierText = (TextView) view.findViewById(R.id.recordingMultiplierText);
		
		// Set values from settings
		float multiplierValue = settings.getAmplitudeBoostMultiplier();
		int multiplierProgress = (int) (((float)recordingMultiplierBar.getMax()/2f)+(((float)recordingMultiplierBar.getMax()/2f)*multiplierValue));
		recordingMultiplierBar.setProgress(multiplierProgress);
		recordingMultiplierText.setText((int)(multiplierValue*100)+"%");
		return view;
	}

	@Override
	public void onProgressChanged(SeekBar seekBar, int progress,
			boolean fromUser) {
		if(fromUser) {
			// Have half of seek bar produce negative values
			int defaultValue = seekBar.getMax()/2;
			float multiplier = (float)(progress-defaultValue)/defaultValue;
			
			settings.setAmplitudeBoostMultiplier(multiplier);
			recordingMultiplierText.setText((int)(multiplier*100)+"%");
			
			// TODO maybe move this to onDismiss?
			if(MumbleService.getCurrentService() != null)
				MumbleService.getCurrentService().updateSettings();
		}
	}
	
	@Override
	public void onDismiss(DialogInterface dialog) {
		super.onDismiss(dialog);
	}

	@Override
	public void onStartTrackingTouch(SeekBar seekBar) {
	}

	@Override
	public void onStopTrackingTouch(SeekBar seekBar) {
	}

}
