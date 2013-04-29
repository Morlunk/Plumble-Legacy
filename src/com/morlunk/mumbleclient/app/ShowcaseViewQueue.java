package com.morlunk.mumbleclient.app;

import java.util.List;

import android.view.View;

import com.github.espiandev.showcaseview.ShowcaseView;
import com.github.espiandev.showcaseview.ShowcaseView.OnShowcaseEventListener;

/**
 * Simple class to manage displaying ShowcaseViews in a linear sequence.
 * @author morlunk
 *
 */
public class ShowcaseViewQueue {

	List<ShowcaseView> showcases;
	int index = 0;
	
	/**
	 * Creates a queue with the passed showcases. Will make them hidden.
	 * @param showcases
	 */
	public ShowcaseViewQueue(List<ShowcaseView> showcases) {
		this.showcases = showcases;
		for(ShowcaseView showcaseView : showcases) {
			showcaseView.setVisibility(View.GONE); // Start hidden by default.
		}
	}
	
	public void queueNext() {
		if(index < showcases.size()) {
			ShowcaseView showcase = showcases.get(index);
			showcase.setOnShowcaseEventListener(new OnShowcaseEventListener() {
				@Override
				public void onShowcaseViewShow(ShowcaseView showcaseView) { }
				
				@Override
				public void onShowcaseViewHide(ShowcaseView showcaseView) {
					queueNext();
				}
			});
			showcase.show();
			index++;
		}
	}
}
