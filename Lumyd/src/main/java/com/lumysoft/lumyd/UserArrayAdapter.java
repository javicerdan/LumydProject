package com.lumysoft.lumyd;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.List;

/**
 * Created by Javier Cerdán on 10/08/13.
 */
public class UserArrayAdapter<User> extends ArrayAdapter {
    List<User> mUsers;
    public UserArrayAdapter(Context mContext, int simple_list_item_1, List<User> mUserList) {
        super(mContext, simple_list_item_1, mUserList);
        mUsers = mUserList;
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {

        com.lumysoft.lumydapi.userendpoint.model.User user =
                (com.lumysoft.lumydapi.userendpoint.model.User) mUsers.get(i);
        TextView tView;
        if (view == null) {
            tView = new TextView(viewGroup.getContext());
        } else {
            tView = (TextView) view;
        }
        tView.setText(user.getName());
        return tView;
    }
}
