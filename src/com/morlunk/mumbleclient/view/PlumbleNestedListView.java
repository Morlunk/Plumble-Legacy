package com.morlunk.mumbleclient.view;


import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;

import com.morlunk.mumbleclient.view.PlumbleNestedAdapter.NestMetadataType;
import com.morlunk.mumbleclient.view.PlumbleNestedAdapter.NestPositionMetadata;

public class PlumbleNestedListView extends ListView implements OnItemClickListener {

	public interface OnNestedChildClickListener {
		public void onNestedChildClick(AdapterView<?> parent, View view, int groupPosition, int childPosition, long id);
	}
	public interface OnNestedGroupClickListener {
		public void onNestedGroupClick(AdapterView<?> parent, View view, int groupPosition, long id);
	}
	
	private PlumbleNestedAdapter mNestedAdapter;
	private OnNestedChildClickListener mChildClickListener;
	private OnNestedGroupClickListener mGroupClickListener;
	
	public PlumbleNestedListView(Context context) {
		this(context, null);
	}
	
	public PlumbleNestedListView(Context context, AttributeSet attrs) {
		super(context, attrs);
		
		setOnItemClickListener(this);
		
		/*
		TypedArray array = context.getTheme().obtainStyledAttributes(attrs, R.styleable.PlumbleNestedListView, 0, 0);
		try {
			setDefaultExpand(array.getBoolean(R.styleable.PlumbleNestedListView_defaultExpand, false));
		} finally {
			array.recycle();
		}
		*/
	}
	
	public void setAdapter(PlumbleNestedAdapter adapter) {
		super.setAdapter(adapter);
		mNestedAdapter = adapter;
	}

	public int getFlatGroupPosition(int groupPosition) {
		for(int x=0;x<mNestedAdapter.flatMeta.size();x++) {
			NestPositionMetadata metadata = mNestedAdapter.flatMeta.get(x);
			if(metadata.type == NestMetadataType.META_TYPE_GROUP &&
					metadata.groupPosition == groupPosition)
				return x;
		}
		return -1;
	}
	
	public int getFlatChildPosition(int groupPosition, int childPosition) {
		for(int x=0;x<mNestedAdapter.flatMeta.size();x++) {
			NestPositionMetadata metadata = mNestedAdapter.flatMeta.get(x);
			if(metadata.type == NestMetadataType.META_TYPE_ITEM &&
					metadata.groupPosition == groupPosition &&
					metadata.childPosition == childPosition)
				return x;
		}
		return -1;
	}	

	public OnNestedChildClickListener getOnChildClickListener() {
		return mChildClickListener;
	}

	public void setOnChildClickListener(
			OnNestedChildClickListener mChildClickListener) {
		this.mChildClickListener = mChildClickListener;
	}

	public OnNestedGroupClickListener getOnGroupClickListener() {
		return mGroupClickListener;
	}

	public void setOnGroupClickListener(
			OnNestedGroupClickListener mGroupClickListener) {
		this.mGroupClickListener = mGroupClickListener;
	}
	
	@Override
	public void setOnItemClickListener(OnItemClickListener listener) {
		if(listener != this)
	        throw new RuntimeException(
	                "For PlumbleNestedListView, please use the child and group equivalents of setOnItemClickListener.");
		super.setOnItemClickListener(listener);
	}

	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position,
			long id) {
		NestPositionMetadata metadata = mNestedAdapter.flatMeta.get(position);
		if(metadata.type == NestMetadataType.META_TYPE_GROUP && mGroupClickListener != null)
			mGroupClickListener.onNestedGroupClick(parent, view, metadata.groupPosition, id);
		else if(metadata.type == NestMetadataType.META_TYPE_ITEM && mChildClickListener != null)
			mChildClickListener.onNestedChildClick(parent, view, metadata.groupPosition, metadata.childPosition, id);
	}
}
