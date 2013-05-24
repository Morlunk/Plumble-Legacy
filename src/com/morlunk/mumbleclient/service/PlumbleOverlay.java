package com.morlunk.mumbleclient.service;

import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.RemoteException;
import android.util.Log;
import android.view.*;
import android.widget.*;
import com.morlunk.mumbleclient.Globals;
import com.morlunk.mumbleclient.R;
import com.morlunk.mumbleclient.Settings;
import com.morlunk.mumbleclient.service.model.User;

import java.util.List;

/**
 * Created by andrew on 23/05/13.
 */
public class PlumbleOverlay {

    private MumbleService mService;

    private View mOverlayView;
    private ListView mListView;
    private PlumbleOverlayAdapter mAdapter;

    private List<User> mChannelUsers;

    private BaseServiceObserver mServiceObserver = new BaseServiceObserver() {
        @Override
        public void onCurrentChannelChanged() throws RemoteException {
            super.onCurrentChannelChanged();
            mChannelUsers = mService.getChannelMap().get(mService.getCurrentChannel().id);
            mAdapter.notifyDataSetChanged();;
        }

        @Override
        public void onUserTalkingUpdated(User user) {
            super.onUserTalkingUpdated(user);
            mAdapter.notifyDataSetChanged();
        }

        @Override
        public void onUserUpdated(User user) throws RemoteException {
            super.onUserUpdated(user);
            mAdapter.notifyDataSetChanged();
        }
    };

    public PlumbleOverlay(MumbleService service) {
        mService = service;
        mChannelUsers = mService.getChannelMap().get(mService.getCurrentChannel().id);
        mAdapter = new PlumbleOverlayAdapter();
    }

    public void show() {
        final WindowManager windowManager = (WindowManager)mService.getSystemService(Context.WINDOW_SERVICE);
        final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.LEFT | Gravity.TOP;
        params.x = 0;
        params.y = 0;
        LayoutInflater inflater = (LayoutInflater) mService.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mOverlayView = inflater.inflate(R.layout.overlay, null);
        mListView = (ListView)mOverlayView.findViewById(R.id.overlay_list);
        mListView.setAdapter(mAdapter);

        TextView titleBar = (TextView)mOverlayView.findViewById(R.id.overlay_title);

        titleBar.setOnTouchListener(new View.OnTouchListener() {

            private int offsetX = 0;
            private int offsetY = 0;

            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                    offsetX = (int) motionEvent.getRawX() - params.x;
                    offsetY = (int) motionEvent.getRawY() - params.y;
                } else if (motionEvent.getAction() == MotionEvent.ACTION_MOVE) {
                    params.x = (int) motionEvent.getRawX() - offsetX;
                    params.y = (int) motionEvent.getRawY() - offsetY;
                    windowManager.updateViewLayout(mOverlayView, params);
                }
                return true;
            }
        });
        ImageView talkButton = (ImageView)mOverlayView.findViewById(R.id.overlay_talk);
        talkButton.setVisibility(Settings.getInstance(mService).isPushToTalk() ? View.VISIBLE : View.GONE);
        talkButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if(motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                    mService.setPushToTalk(true);
                } else if(motionEvent.getAction() == MotionEvent.ACTION_UP) {
                    mService.setPushToTalk(false);
                }
                return true;
            }
        });

        ImageView shrinkButton = (ImageView)mOverlayView.findViewById(R.id.overlay_shrink);
        shrinkButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mListView.setVisibility(mListView.getVisibility() == View.GONE ? View.VISIBLE : View.GONE);
            }
        });

        // Add layout to window manager
        windowManager.addView(mOverlayView, params);
        mService.registerObserver(mServiceObserver);
    }

    public void hide() {
        mService.unregisterObserver(mServiceObserver);
        WindowManager windowManager = (WindowManager)mService.getSystemService(Context.WINDOW_SERVICE);
        if(mOverlayView != null) {
            windowManager.removeView(mOverlayView);
            mOverlayView = null;
        }
    }

    private class PlumbleOverlayAdapter extends BaseAdapter implements ListAdapter {

        @Override
        public int getCount() {
            return mChannelUsers.size();
        }

        @Override
        public User getItem(int i) {
            return mChannelUsers.get(i);
        }

        @Override
        public long getItemId(int i) {
            return mChannelUsers.get(i).session;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            if(view == null) {
                LayoutInflater inflater = (LayoutInflater)mService.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                view = inflater.inflate(R.layout.channel_user_row_micro, viewGroup, false);
            }

            TextView nameView = (TextView)view.findViewById(R.id.userRowName);
            ImageView stateView = (ImageView)view.findViewById(R.id.userRowState);

            User user = getItem(i);
            nameView.setText(user.name);

            if (user.selfDeafened) {
                stateView.setImageResource(R.drawable.ic_deafened);
            } else if (user.selfMuted) {
                stateView.setImageResource(R.drawable.ic_muted);
            } else if (user.serverDeafened) {
                stateView.setImageResource(R.drawable.ic_server_deafened);
            } else if (user.serverMuted) {
                stateView.setImageResource(R.drawable.ic_server_muted);
            } else if (user.suppressed) {
                stateView.setImageResource(R.drawable.ic_suppressed);
            } else {
                if (user.talkingState == User.TALKINGSTATE_TALKING) {
                    stateView.setImageResource(R.drawable.ic_talking_on);
                } else {
                    stateView.setImageResource(R.drawable.ic_talking_off);
                }
            }

            return view;
        }
    }
}
