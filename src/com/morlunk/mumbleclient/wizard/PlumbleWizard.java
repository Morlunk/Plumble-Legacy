package com.morlunk.mumbleclient.wizard;

import java.io.File;
import java.security.cert.X509Certificate;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.FragmentActivity;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.Toast;
import android.widget.ViewFlipper;

import com.morlunk.mumbleclient.R;
import com.morlunk.mumbleclient.Settings;
import com.morlunk.mumbleclient.service.PlumbleCertificateManager;

public class PlumbleWizard extends FragmentActivity {
	
	private static final String VIEW_FLIPPER_POSITION = "flipperPosition";
	private static final String CERTIFICATE_FOLDER = "Plumble";
	
	private ViewFlipper mViewFlipper;
	private Button mBackButton;
	private Button mNextButton;
	
	// Certificate
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		
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
		
		// Setup certificate wizard
		Button generateCertificate = (Button) findViewById(R.id.wizardGenerateCert);
		generateCertificate.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				generateCertificate();
			}
		});
		
		// Setup audio wizard
		
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
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if(keyCode == KeyEvent.KEYCODE_BACK) {
			if(mViewFlipper.getDisplayedChild() > 0) {
				mViewFlipper.showPrevious();
				updateButtonLabels();
			}
			return true;
		}
		return super.onKeyDown(keyCode, event);
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
	
	/**
	 * Generates a new certificate and sets it as active.
	 * @param certificateList If passed, will update the list of certificates available. Messy.
	 */
	private void generateCertificate() {
		AsyncTask<File, Void, X509Certificate> task = new AsyncTask<File, Void, X509Certificate>() {
			
			private ProgressDialog loadingDialog;
			private File certificatePath;
			
			@Override
			protected void onPreExecute() {
				super.onPreExecute();
				
				loadingDialog = new ProgressDialog(PlumbleWizard.this);
				loadingDialog.setIndeterminate(true);
				loadingDialog.setMessage(getResources().getString(R.string.generateCertProgress));
				loadingDialog.setOnCancelListener(new OnCancelListener() {
					
					@Override
					public void onCancel(DialogInterface arg0) {
						cancel(true);
						
					}
				});
				loadingDialog.show();
			}
			@Override
			protected X509Certificate doInBackground(File... params) {
				certificatePath = params[0];
				try {
					X509Certificate certificate = PlumbleCertificateManager.createCertificate(certificatePath);
					
					Settings settings = Settings.getInstance(PlumbleWizard.this);
					settings.setCertificatePath(certificatePath.getAbsolutePath());
					return certificate;
				} catch (Exception e) {
					e.printStackTrace();
					return null;
				}
			}
			
			@Override
			protected void onPostExecute(X509Certificate result) {
				super.onPostExecute(result);
				if(result != null) {
					Toast.makeText(PlumbleWizard.this, getString(R.string.generateCertSuccess, certificatePath.getName()), Toast.LENGTH_SHORT).show();
				} else {
					Toast.makeText(PlumbleWizard.this, R.string.generateCertFailure, Toast.LENGTH_SHORT).show();
				}
				
				loadingDialog.dismiss();
			}
			
		};
		File externalStorageDirectory = Environment.getExternalStorageDirectory();
		File plumbleFolder = new File(externalStorageDirectory, CERTIFICATE_FOLDER);
		if(!plumbleFolder.exists()) {
			plumbleFolder.mkdir();
		}
		File certificatePath = new File(plumbleFolder, String.format("plumble-%d.p12", (int) (System.currentTimeMillis() / 1000L)));
		task.execute(certificatePath);
	}
}
