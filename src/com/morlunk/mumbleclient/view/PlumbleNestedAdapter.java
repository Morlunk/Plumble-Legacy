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
		int groupParent;
		int childPosition;
		int depth;
	}
	
	private Context mContext;
	protected List<NestPositionMetadata> flatMeta = new ArrayList<NestPositionMetadata>();
	protected List<NestPositionMetadata> visibleMeta = new ArrayList<NestPositionMetadata>();
	protected List<Integer> expandedGroups = new ArrayList<Integer>();
	
	public abstract View getGroupView(int groupPosition, int depth, View convertView, ViewGroup parent);
	public abstract View getChildView(int groupPosition, int childPosition, int depth, View convertView, ViewGroup parent);
	public abstract int getGroupParentPosition(int groupPosition);
	public abstract int getGroupCount();
	public abstract int getGroupDepth(int groupPosition);
	public abstract int getChildCount(int groupPosition);

	public Object getChild(int groupPosition, int childPosition) { return null; };
	public Object getGroup(int groupPosition) { return null; };
	
	DataSetObserver dataSetObserver = new DataSetObserver() {
		public void onChanged() {
			buildFlatMetadata();
			buildVisibleMetadata();
		};
	};
	
	public PlumbleNestedAdapter(Context context) {
		mContext = context;
		registerDataSetObserver(dataSetObserver);
		expandedGroups.add(0); // Always expand root
	}
	
	private final void buildFlatMetadata() {
		flatMeta = new ArrayList<NestPositionMetadata>();
		for(int x=0;x<getGroupCount();x++) {
			NestPositionMetadata groupPositionMetadata = new NestPositionMetadata();
			groupPositionMetadata.type = NestMetadataType.META_TYPE_GROUP;
			groupPositionMetadata.groupPosition = x;
			groupPositionMetadata.groupParent = getGroupParentPosition(x);
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
	
	/**
	 * TODO move this over to PlumbleNestedListView
	 */
	protected final void buildVisibleMetadata() {
		visibleMeta = new ArrayList<NestPositionMetadata>();
		for(NestPositionMetadata metadata : flatMeta) {
			if(metadata.type == NestMetadataType.META_TYPE_GROUP) {
				if(isParentExpanded(metadata.groupPosition))
						visibleMeta.add(metadata);
			} else if(metadata.type == NestMetadataType.META_TYPE_ITEM) {
				if(expandedGroups.contains(metadata.groupPosition) && isParentExpanded(metadata.groupPosition)) // Don't insert a child group with no parent.
					visibleMeta.add(metadata);
			}
		}
	}
	
	/**
	 * Iterates up the group hierarchy and returns whether or not any of the group's parents are not expanded.
	 * @param groupPosition
	 */
	private boolean isParentExpanded(int groupPosition) {
		
		for(NestPositionMetadata metadata : flatMeta) {
			// Collapse the specified position and its children
			if(metadata.groupPosition == groupPosition) {
				if(metadata.groupParent == -1)
					return true; // Return true for top of tree.
				if(!expandedGroups.contains(metadata.groupParent))
					return false;
				else
					return isParentExpanded(metadata.groupParent);
			}
		}
		return true;
	}
	
	protected void collapseGroup(int groupPosition) {
		for(NestPositionMetadata metadata : flatMeta) {
			// Collapse the specified position and its children
			if(metadata.groupPosition == groupPosition) {
				expandedGroups.remove((Integer)groupPosition);
			}
		}
	}
	
	protected void expandGroup(int groupPosition) {
		expandedGroups.add((Integer)groupPosition);
	}

	public int getFlatChildPosition(int groupPosition, int childPosition) {
		for(int x=0;x<flatMeta.size();x++) {
			NestPositionMetadata metadata = flatMeta.get(x);
			if(metadata.type == NestMetadataType.META_TYPE_ITEM &&
					metadata.groupPosition == groupPosition &&
					metadata.childPosition == childPosition)
				return x;
		}
		return -1;
	}

	public int getFlatGroupPosition(int groupPosition) {
		for(int x=0;x<flatMeta.size();x++) {
			NestPositionMetadata metadata = flatMeta.get(x);
			if(metadata.type == NestMetadataType.META_TYPE_GROUP &&
					metadata.groupPosition == groupPosition)
				return x;
		}
		return -1;
	}
	
	@Override
	public final int getCount() {
		return visibleMeta.size();
	}
	
	@Override
	public int getViewTypeCount() {
		return 2;
	}
	
	@Override
	public int getItemViewType(int position) {
		NestPositionMetadata metadata = visibleMeta.get(position);
		return metadata.type.ordinal();
	}

	@Override
	public final Object getItem(int position) {
		NestPositionMetadata metadata = visibleMeta.get(position);
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
		NestPositionMetadata metadata = visibleMeta.get(position);
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
