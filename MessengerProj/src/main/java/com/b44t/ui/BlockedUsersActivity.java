/*
 * This part of the Delta Chat fronted is based on Telegram which is covered by the following note:
 *
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package com.b44t.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.b44t.messenger.LocaleController;
import com.b44t.messenger.MrMailbox;
import com.b44t.messenger.TLRPC;
import com.b44t.messenger.MessagesController;
import com.b44t.messenger.NotificationCenter;
import com.b44t.messenger.R;
import com.b44t.ui.Adapters.BaseFragmentAdapter;
import com.b44t.ui.Cells.UserCell;
import com.b44t.ui.ActionBar.ActionBar;
import com.b44t.ui.ActionBar.ActionBarMenu;
import com.b44t.ui.ActionBar.BaseFragment;
import com.b44t.ui.Components.LayoutHelper;

public class BlockedUsersActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate, ContactsActivity.ContactsActivityDelegate {

    private ListView listView;
    private ListAdapter listViewAdapter;
    private TextView emptyTextView;
    private int selectedUserId;

    private final static int block_user = 1;

    private int[] blockedUserIds;

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        blockedUserIds = MrMailbox.MrMailboxGetBlockedContacts(MrMailbox.hMailbox);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.updateInterfaces);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.blockedUsersDidLoaded);
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.updateInterfaces);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.blockedUsersDidLoaded);
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString("BlockedUsers", R.string.BlockedUsers));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == block_user) {
                    Bundle args = new Bundle();
                    args.putInt("do_what", ContactsActivity.SELECT_CONTACT_TO_BLOCK);
                    ContactsActivity fragment = new ContactsActivity(args);
                    fragment.setDelegate(BlockedUsersActivity.this);
                    presentFragment(fragment);
                }
            }
        });

        ActionBarMenu menu = actionBar.createMenu();
        menu.addItem(block_user, R.drawable.plus);

        fragmentView = new FrameLayout(context);
        FrameLayout frameLayout = (FrameLayout) fragmentView;

        emptyTextView = new TextView(context);
        emptyTextView.setTextColor(0xff808080);
        emptyTextView.setTextSize(20);
        emptyTextView.setGravity(Gravity.CENTER);
        emptyTextView.setVisibility(View.INVISIBLE);
        emptyTextView.setText(LocaleController.getString("NoBlocked", R.string.NoBlocked));
        frameLayout.addView(emptyTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));
        emptyTextView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return true;
            }
        });

        listView = new ListView(context);
        listView.setEmptyView(emptyTextView);
        listView.setVerticalScrollBarEnabled(false);
        listView.setDivider(null);
        listView.setDividerHeight(0);
        listView.setAdapter(listViewAdapter = new ListAdapter(context));
        listView.setVerticalScrollbarPosition(LocaleController.isRTL ? ListView.SCROLLBAR_POSITION_LEFT : ListView.SCROLLBAR_POSITION_RIGHT);
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                if (i>=0 && i < blockedUserIds.length) {
                    Bundle args = new Bundle();
                    args.putInt("user_id", blockedUserIds[i]);
                    presentFragment(new ProfileActivity(args));
                }
            }
        });

        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> adapterView, View view, int i, long l) {
                if (i < 0 || i >= blockedUserIds.length || getParentActivity() == null) {
                    return true;
                }
                selectedUserId = blockedUserIds[i];

                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                CharSequence[] items = new CharSequence[]{LocaleController.getString("UnblockContact", R.string.UnblockContact)};
                builder.setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        MrMailbox.MrMailboxBlockContact(MrMailbox.hMailbox, selectedUserId, 0);
                    }
                });
                showDialog(builder.create());
                return true;
            }
        });

        listView.setEmptyView(emptyTextView);
        return fragmentView;
    }

    @Override
    public void didReceivedNotification(int id, Object... args) {
        if (id == NotificationCenter.updateInterfaces) {
            int mask = (Integer)args[0];
            if ((mask & MessagesController.UPDATE_MASK_AVATAR) != 0 || (mask & MessagesController.UPDATE_MASK_NAME) != 0) {
                updateVisibleRows(mask);
            }
        } else if (id == NotificationCenter.blockedUsersDidLoaded) {
            blockedUserIds = MrMailbox.MrMailboxGetBlockedContacts(MrMailbox.hMailbox);
            if (listView != null && listView.getEmptyView() == null) {
                listView.setEmptyView(emptyTextView);
            }
            if (listViewAdapter != null) {
                listViewAdapter.notifyDataSetChanged();
            }
        }
    }

    private void updateVisibleRows(int mask) {
        if (listView == null) {
            return;
        }
        int count = listView.getChildCount();
        for (int a = 0; a < count; a++) {
            View child = listView.getChildAt(a);
            if (child instanceof UserCell) {
                ((UserCell) child).update(mask);
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listViewAdapter != null) {
            listViewAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void didSelectContact(final TLRPC.User user, String param) {
        if (user == null) {
            return;
        }
        MrMailbox.MrMailboxBlockContact(MrMailbox.hMailbox, user.id, 1);
    }

    private class ListAdapter extends BaseFragmentAdapter {
        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean areAllItemsEnabled() {
            return false;
        }

        @Override
        public boolean isEnabled(int i) {
            return true;
        }

        @Override
        public int getCount() {
            return blockedUserIds.length;
        }

        @Override
        public Object getItem(int i) {
            return null;
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public boolean hasStableIds() {
            return false;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            if (view == null) {
                view = new UserCell(mContext, 1, 0);
            }
            if (i>=0 && i<blockedUserIds.length) {
                long hContact = MrMailbox.MrMailboxGetContact(MrMailbox.hMailbox, blockedUserIds[i]);
                    ((UserCell) view).setData(blockedUserIds[i], 0, MrMailbox.MrContactGetDisplayName(hContact), MrMailbox.MrContactGetAddr(hContact), 0);
                MrMailbox.MrContactUnref(hContact);
            }
            return view;
        }

        @Override
        public int getItemViewType(int i) {
            return 0;
        }

        @Override
        public int getViewTypeCount() {
            return 1;
        }

        @Override
        public boolean isEmpty() {
            return blockedUserIds.length==0;
        }
    }
}