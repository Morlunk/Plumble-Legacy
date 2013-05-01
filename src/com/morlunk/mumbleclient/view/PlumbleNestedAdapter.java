package com.morlunk.mumbleclient.view;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;
import android.util.SparseArray;
import android.util.SparseBooleanArray;
import android.util.SparseIntArray;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListAdapter;

import com.morlunk.mumbleclient.Globals;

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
	
	/**
	 * Metadata representations
	 */
	protected List<NestPositionMetadata> flatMeta = new ArrayList<NestPositionMetadata>();
	protected List<NestPositionMetadata> visibleMeta = new ArrayList<NestPositionMetadata>();
	
	/**
	 * Group position-meta mappings
	 * TODO cleanup, uses too much memory!
	 */
	protected SparseArray<NestPositionMetadata> groupMap = new SparseArray<NestPositionMetadata>(); // Map of group IDs to meta
	protected SparseIntArray groupToPositionMap = new SparseIntArray(); // Map group IDs to their position
	protected SparseBooleanArray expandedGroups = new SparseBooleanArray();
	@SuppressLint("UseSparseArrays") protected Map<Integer, Boolean> manualExpansions = new HashMap<Integer, Boolean>(); // We use hashmap instead of sparsearray because we need to use contains().
	
	public abstract View getGroupView(int groupPosition, int depth, View convertView, ViewGroup parent);
	public abstract View getChildView(int groupPosition, int childPosition, int depth, View convertView, ViewGroup parent);
	
	public abstract int getGroupDepth(int groupPosition);
	public abstract int getGroupCount();
	public abstract int getChildCount(int groupPosition);
	
	public abstract int getParentId(int groupPosition);
	public abstract int getGroupId(int groupPosition);
	public abstract int getChildId(int groupPosition, int childPosition);

	public Object getChild(int groupPosition, int childPosition) { return null; };
	public Object getGroup(int groupPosition) { return null; };
	public boolean isGroupExpandedByDefault(int groupPosition) { return false; };
	
	public PlumbleNestedAdapter(Context context) {
		mContext = context;
		expandedGroups.put(0, true); // Always expand root
	}
	
	private final void buildFlatMetadata() {
		long startTime = System.currentTimeMillis();
		flatMeta.clear();
		groupToPositionMap.clear();
		groupMap.clear();
		for(int x=0;x<getGroupCount();x++) {
			NestPositionMetadata groupPositionMetadata = new NestPositionMetadata();
			groupPositionMetadata.type = NestMetadataType.META_TYPE_GROUP;
			groupPositionMetadata.groupPosition = x;
			// A bit hackish. Really boosts performance though, compared to getting index of parent channel ID. FIXME
			groupPositionMetadata.groupParent = groupToPositionMap.get(getParentId(x), -1);
			// FIXME switch to using unique IDs for channels so when they shift it retains expansion
			if(manualExpansions.containsKey(x) && manualExpansions.get(x) == isGroupExpandedByDefault(x))
				manualExpansions.remove((Integer)x); // If a view was manually collapsed/expanded and now has the same value as default, remove memory of it.
			if(!manualExpansions.containsKey(x))
				expandedGroups.put(x, isGroupExpandedByDefault(x)); // Expand automagically, except if the channel was manually collapsed/expanded.
			
			flatMeta.add(groupPositionMetadata);
			groupMap.put(x, groupPositionMetadata);
			groupToPositionMap.put(getGroupId(x), x);
			for(int y=0;y<getChildCount(x);y++) {
				NestPositionMetadata childPositionMetadata = new NestPositionMetadata();
				childPositionMetadata.type = NestMetadataType.META_TYPE_ITEM;
				childPositionMetadata.groupPosition = x;
				childPositionMetadata.childPosition = y;
				flatMeta.add(childPositionMetadata);
			}
		}
		Log.d(Globals.LOG_TAG, "OPT: built flat metadata, took "+(System.currentTimeMillis()-startTime)+"ms");
	}
	
	/**
	 * TODO move this over to PlumbleNestedListView
	 */
	protected final void buildVisibleMetadata() {
		long startTime = System.currentTimeMillis();
		visibleMeta.clear();
		for(NestPositionMetadata metadata : flatMeta) {
			if(metadata.type == NestMetadataType.META_TYPE_GROUP) {
				if(isParentExpanded(metadata.groupPosition))
						visibleMeta.add(metadata);
			} else if(metadata.type == NestMetadataType.META_TYPE_ITEM) {
				int parent = getFlatGroupPosition(metadata.groupPosition);
				NestPositionMetadata parentMeta = flatMeta.get(parent);
				if(visibleMeta.contains(parentMeta) && expandedGroups.get(parentMeta.groupPosition, true)) // Don't insert a child group with no parent.
					visibleMeta.add(metadata);
			}
		}
		Log.d(Globals.LOG_TAG, "OPT: built visible metadata, took "+(System.currentTimeMillis()-startTime)+"ms");
	}
	
	/**
	 * Iterates up the group hierarchy and returns whether or not any of the group's parents are not expanded.
	 * @param groupPosition
	 */
	private boolean isParentExpanded(int groupPosition) {
		NestPositionMetadata metadata = groupMap.get(groupPosition);
		if(metadata.groupParent == -1)
			return true; // Return true for top of tree.
		if(!expandedGroups.get(metadata.groupParent))
			return false;
		else
			return isParentExpanded(metadata.groupParent);
	}
	
	protected void collapseGroup(int groupPosition) {
		if(isGroupExpandedByDefault(groupPosition))
			manualExpansions.put(groupPosition, false);
		expandedGroups.put(groupPosition, false);
	}
	
	protected void expandGroup(int groupPosition) {
		if(!isGroupExpandedByDefault(groupPosition))
			manualExpansions.put((Integer)groupPosition, true);
		expandedGroups.put(groupPosition, true);
	}
	
	public boolean isGroupExpanded(int groupPosition) {
		return expandedGroups.get(groupPosition);
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
	
	public int getVisibleFlatChildPosition(int groupPosition, int childPosition) {
		for(int x=0;x<visibleMeta.size();x++) {
			NestPositionMetadata metadata = visibleMeta.get(x);
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
	
	public int getVisibleFlatGroupPosition(int groupPosition) {
		for(int x=0;x<visibleMeta.size();x++) {
			NestPositionMetadata metadata = visibleMeta.get(x);
			if(metadata.type == NestMetadataType.META_TYPE_ITEM &&
					metadata.groupPosition == groupPosition)
				return x;
		}
		return -1;
	}
	
	@Override
	public void notifyDataSetChanged() {
		long startTime = System.currentTimeMillis();
		buildFlatMetadata();
		buildVisibleMetadata();
		super.notifyDataSetChanged();
		Log.d(Globals.LOG_TAG, "OPT: reloaded data set, took "+(System.currentTimeMillis()-startTime)+"ms");
	}
	
	/**
	 * Does not rebuild flat hierarchy metadata.
	 */
	protected void notifyVisibleSetChanged() {
		buildVisibleMetadata();
		super.notifyDataSetChanged();
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
	public long getItemId(int position) {
		return 0;
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
	public final View getView(int position, View convertView, ViewGroup parent) {
		NestPositionMetadata metadata = visibleMeta.get(position);
		NestMetadataType mType = NestMetadataType.values()[getItemViewType(position)];
		View view = null;
		if(mType == NestMetadataType.META_TYPE_GROUP)
			view = getGroupView(metadata.groupPosition, metadata.depth, convertView, parent);
		else if(mType == NestMetadataType.META_TYPE_ITEM)
			view = getChildView(metadata.groupPosition, metadata.childPosition, metadata.depth, convertView, parent);
		return view;
	}
	
	public Context getContext() {
		return mContext;
	}

}
