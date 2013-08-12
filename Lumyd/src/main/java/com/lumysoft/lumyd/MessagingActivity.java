package com.lumysoft.lumyd;

import android.annotation.TargetApi;
import android.app.ActionBar;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;

import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.extensions.android.json.AndroidJsonFactory;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.lumysoft.lumydapi.messageEndpoint.MessageEndpoint;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Javier Cerdán on 1/08/13.
 */
public class MessagingActivity extends FragmentActivity {

    private Button mSendButton;
    private EditText mEditText;
    private GoogleAccountCredential mCredential;
    private SharedPreferences mSettings;
    private MessageEndpoint messageEndpoint;
    private ListView mUserListView;
    private List<String> messagesList;
    private ArrayAdapter<String> mListAdapter;

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_messaging);

        mEditText = (EditText) findViewById(R.id.editText);
        mSendButton = (Button) findViewById(R.id.send_button);
        mCredential = GoogleAccountCredential.usingAudience(this, "server:client_id:" + LumydUtils.APPENGINE_APPID);
        messagesList = new ArrayList<String>();
        mUserListView = (ListView) findViewById(R.id.listView);
        mListAdapter = new ArrayAdapter<String>(
                getApplicationContext(), android.R.layout.simple_list_item_1, messagesList);
        mUserListView.setAdapter(mListAdapter);

        //Retrieve the account name for credentials
        mSettings = PreferenceManager.getDefaultSharedPreferences(this);
        String accountName = mSettings.getString(LumydUtils.PREF_ACCOUNT_NAME, null);
        if(accountName == null) {
            startActivityForResult(mCredential.newChooseAccountIntent(), LumydUtils.REQUEST_ACCOUNT_PICKER);
        } else {
            mCredential.setSelectedAccountName(accountName);
        }
        registerReceiver(mHandleMessageReceiver, new IntentFilter("com.google.android.c2dm.intent.RECEIVE"));
        MessageEndpoint.Builder builder = new MessageEndpoint.Builder(
                AndroidHttp.newCompatibleTransport(), new AndroidJsonFactory(),
                mCredential);
        messageEndpoint = builder.build();

        mSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                send(mEditText.getText().toString());
                messagesList.add(mEditText.getText().toString());
                mListAdapter.notifyDataSetChanged();
                mEditText.setText(null);
            }
        });

        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setHomeButtonEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    private void send(final String txt) {
        new AsyncTask<Void, Void, String>() {
            @Override
            protected String doInBackground(Void... params) {
                String msg = "";
                try {
                    messageEndpoint.sendMessage(txt, getIntent().getStringExtra(LumydUtils.USER_ID),
                            getIntent().getStringExtra(LumydUtils.SELECTED_USER_ID)).execute();
                } catch (IOException ex) {
                    Log.e(LumydUtils.APPTAG, "Message could not be sent " + ex.getMessage());
                }
                return msg;
            }
        }.execute(null, null, null);
    }

    private final BroadcastReceiver mHandleMessageReceiver =
            new GcmBroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    messagesList.add(intent.getExtras().getString(LumydUtils.MESSAGE));
                    mListAdapter.notifyDataSetChanged();
                    setResultCode(Activity.RESULT_OK);
                }
            };
}
