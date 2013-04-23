package com.morlunk.mumbleclient.view;

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.database.DataSetObserver;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListAdapter;

public abstract class PlumbleNestedAdapter extends BaseAdapter implements ListAdapter {
	
	protected enum NestMetadataType {
		META_TYPE_GROUP,
		META_TYPE_ITEM
	}
	
	protected class NestPositionMetadata {
		NestMetadataType type;
		int groupPosition;
		int childPosition;
		int depth;
		/*
		boolean isExpanded;
		NestPositionMetadata parent;
		*/
	}
	
	private Context mContext;
	protected List<NestPositionMetadata> flatMeta = new ArrayList<NestPositionMetadata>();
	
	public abstract View getGroupView(int groupPosition, int depth, View convertView, ViewGroup parent);
	public abstract View getChildView(int groupPosition, int childPosition, int depth, View convertView, ViewGroup parent);
	public abstract int getGroupCount();
	public abstract int getGroupDepth(int groupPosition);
	public abstract int getChildCount(int groupPosition);

	public Object getChild(int groupPosition, int childPosition) { return null; };
	public Object getGroup(int groupPosition) { return null; };
	
	DataSetObserver dataSetObserver = new DataSetObserver() {
		public void onChanged() {
			buildNestMetadata();
		};
	};
	
	public PlumbleNestedAdapter(Context context) {
		mContext = context;
		registerDataSetObserver(dataSetObserver);
	}
	
	private final void buildNestMetadata() {
		flatMeta = new ArrayList<NestPositionMetadata>();
		for(int x=0;x<getGroupCount();x++) {
			NestPositionMetadata groupPositionMetadata = new NestPositionMetadata();
			groupPositionMetadata.type = NestMetadataType.META_TYPE_GROUP;
			groupPositionMetadata.groupPosition = x;
			flatMeta.add(groupPositionMetadata);
			for(int y=0;y<getChildCount(x);y++) {
				NestPositionMetadata childPositionMetadata = new NestPositionMetadata();
				childPositionMetadata.type = NestMetadataType.META_TYPE_ITEM;
				childPositionMetadata.groupPosition = x;
				childPositionMetadata.childPosition = y;
				flatMeta.add(childPositionMetadata);
			}
		}
	}
	
	@Override
	public final int getCount() {
		return flatMeta.size();
	}
	
	@Override
	public int getViewTypeCount() {
		return 2;
	}
	
	@Override
	public int getItemViewType(int position) {
		NestPositionMetadata metadata = flatMeta.get(position);
		return metadata.type.ordinal();
	}

	@Override
	public final Object getItem(int position) {
		NestPositionMetadata metadata = flatMeta.get(position);
		if(metadata.type == NestMetadataType.META_TYPE_GROUP)
			return getGroup(metadata.groupPosition);
		else if(metadata.type == NestMetadataType.META_TYPE_ITEM)
			return getChild(metadata.groupPosition, metadata.childPosition);
		return null;
	}

	@Override
	public final long getItemId(int position) {
		return 0;
	}

	@Override
	public final View getView(int position, View convertView, ViewGroup parent) {
		NestPositionMetadata metadata = flatMeta.get(position);
		NestMetadataType mType = NestMetadataType.values()[getItemViewType(position)];
		if(mType == NestMetadataType.META_TYPE_GROUP)
			return getGroupView(metadata.groupPosition, metadata.depth, convertView, parent);
		else if(mType == NestMetadataType.META_TYPE_ITEM)
			return getChildView(metadata.groupPosition, metadata.childPosition, metadata.depth, convertView, parent);
		return null;
	}
	
	public Context getContext() {
		return mContext;
	}

}
