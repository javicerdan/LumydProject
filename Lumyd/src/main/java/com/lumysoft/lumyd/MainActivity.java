/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.lumysoft.lumyd;

import android.accounts.AccountManager;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.gcm.GoogleCloudMessaging;
import com.google.android.gms.location.LocationClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.extensions.android.json.AndroidJsonFactory;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.lumysoft.lumydapi.userendpoint.Userendpoint;
import com.lumysoft.lumydapi.userendpoint.model.CollectionResponseUser;
import com.lumysoft.lumydapi.userendpoint.model.Point;
import com.lumysoft.lumydapi.userendpoint.model.User;

import java.io.IOException;
import java.util.List;

/**
 * This the app's main Activity. It provides buttons for requesting the various features of the
 * app, displays the current location, the current address, and the status of the location client
 * and updating services.
 *
 *
 */
public class MainActivity extends FragmentActivity implements
        GooglePlayServicesClient.ConnectionCallbacks,
        GooglePlayServicesClient.OnConnectionFailedListener,
        LocationListener {

    // Stores the current instantiation of the location client in this object
    private LocationClient mLocationClient;

    //Parameters to request current location
    private LocationRequest mLRequest;

    // Stores the current location
    private Location mCurrentLocation;

    //User endpoint with backend
    private Userendpoint mUserEndpoint;

    //User backend representation
    private User mUser;

    //Maximum distance we search for other users (in meters).
    private int mDistance;

    //Collection of users nearby
    private CollectionResponseUser mlocalUsers;

    //Manage settings
    private SharedPreferences mSettings;

    private Context mContext;

    //Registration ID for GCM
    private String mRegid;

    private GoogleCloudMessaging mGcm;

    private List<User> mUserList;

    GoogleAccountCredential mCredential;
    /*
     * Initialize the Activity
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mUser = new User();
        mSettings = PreferenceManager.getDefaultSharedPreferences(this);

        String userName = mSettings.getString(SettingsActivity.KEY_PREF_NAME, "");
        if(userName == ""){
            startActivity(new Intent(this, SettingsActivity.class));
        }

        //Set up location request params
        mLRequest = LocationRequest.create();
        mLRequest.setInterval(2000);
        mLRequest.setFastestInterval(1000);
        mLRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        mCredential = GoogleAccountCredential.usingAudience(this, "server:client_id:" + LumydUtils.APPENGINE_APPID);

        //Retrieve the account name for credentials
        String accountName = mSettings.getString(LumydUtils.PREF_ACCOUNT_NAME, null);
        if(accountName == null) {
            startActivityForResult(mCredential.newChooseAccountIntent(), LumydUtils.REQUEST_ACCOUNT_PICKER);
        } else {
            mCredential.setSelectedAccountName(accountName);
        }

        Userendpoint.Builder builder = new Userendpoint.Builder(
                AndroidHttp.newCompatibleTransport(), new AndroidJsonFactory(),
                mCredential);
        mUserEndpoint = builder.build();

        mDistance = 100000; //TODO: Incremental depending on results

        //Register with Google Cloud Messaging
        mContext = getApplicationContext();
        mRegid = LumydUtils.getRegistrationId(mContext, mSettings);

        registerBackground();
        mGcm = GoogleCloudMessaging.getInstance(this);


        /*
         * Create a new location client, using the enclosing class to
         * handle callbacks.
         */
        mLocationClient = new LocationClient(this, this, this);
    }

    /*
     * Called when the Activity is no longer visible at all.
     * Stop updates and disconnect.
     */
    @Override
    public void onStop() {
        Log.v(LumydUtils.APPTAG, "onStop");
        // After disconnect() is called, the client is considered "dead".
        if(mLocationClient.isConnected()){
            mLocationClient.disconnect();
        }
        super.onStop();
    }
    /*
     * Called when the Activity is going into the background.
     * Parts of the UI may be visible, but the Activity is inactive.
     */
    @Override
    public void onPause() {
        Log.v(LumydUtils.APPTAG, "onPause");
        super.onPause();
    }

    /*
     * Called when the Activity is restarted, even before it becomes visible.
     */
    @Override
    public void onStart() {
        Log.v(LumydUtils.APPTAG, "onStart");
        super.onStart();
        mLocationClient.connect();

    }
    /*
     * Called when the system detects that this Activity is now visible.
     */
    @Override
    public void onResume() {
        Log.v(LumydUtils.APPTAG, "onResume");
        super.onResume();
    }

    /*
     * Handle results returned to this Activity by other Activities started with
     * startActivityForResult(). In particular, the method onConnectionFailed() in
     * LocationUpdateRemover and LocationUpdateRequester may call startResolutionForResult() to
     * start an Activity that handles Google Play services problems. The result of this
     * call returns here, to onActivityResult.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {

        // Choose what to do based on the request code
        switch (requestCode) {
            // If the request code matches the code sent in onConnectionFailed
            case LumydUtils.CONNECTION_FAILURE_RESOLUTION_REQUEST :
                switch (resultCode) {
                    // If Google Play services resolved the problem
                    case Activity.RESULT_OK:
                        Log.d(LumydUtils.APPTAG, getString(R.string.resolved));
                    break;
                    default:
                        Log.d(LumydUtils.APPTAG, getString(R.string.no_resolution));
                        break;
                }

            case LumydUtils.REQUEST_ACCOUNT_PICKER:
                if (intent != null && intent.getExtras() != null) {
                    String accountName = intent.getExtras().getString(
                            AccountManager.KEY_ACCOUNT_NAME);
                    if (accountName != null) {
                        //Save it in prefs and credential
                        SharedPreferences.Editor editor = mSettings.edit();
                        editor.putString(LumydUtils.PREF_ACCOUNT_NAME, accountName);
                        editor.commit();
                        mCredential.setSelectedAccountName(accountName);
                        // User is authorized.
                    }
                }
                break;
            default:
               // Report that this Activity received an unknown requestCode
               Log.d(LumydUtils.APPTAG,
                       getString(R.string.unknown_activity_request_code, requestCode));
               break;
        }
    }

    /**
     * Verify that Google Play services is available before making a request.
     *
     * @return true if Google Play services is available, otherwise false
     */
    private boolean servicesConnected() {

        // Check that Google Play services is available
        int resultCode =
                GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
        return ConnectionResult.SUCCESS == resultCode;
    }

    /**
     * First location received is all we need.
     * @param location user location
     */
    @Override
    public void onLocationChanged(Location location) {
        mCurrentLocation = location;
        mLocationClient.removeLocationUpdates(this);
    }
    /*
     * Called by Location Services when the request to connect the
     * client finishes successfully. At this point, you can
     * request the current location or start periodic updates
     */
    @Override
    public void onConnected(Bundle bundle) {
        Log.v(LumydUtils.APPTAG, "Connected!");
        //Fire a fast request. Then we can ask for the last location.
        mLocationClient.requestLocationUpdates(mLRequest, this);

        if(servicesConnected() && (mCurrentLocation = mLocationClient.getLastLocation()) != null) {
            new InsertUserTask().execute();
            new ListUsersTask().execute();
        }
    }

    /*
     * Called by Location Services if the connection to the
     * location client drops because of an error.
     */
    @Override
    public void onDisconnected() {
    }


    /*
     * Called by Location Services if the attempt to
     * Location Services fails.
     */
    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {

        /*
         * Google Play services can resolve some errors it detects.
         * If the error has a resolution, try sending an Intent to
         * start a Google Play services activity that can resolve
         * error.
         */
        if (connectionResult.hasResolution()) {
            try {

                // Start an Activity that tries to resolve the error
                connectionResult.startResolutionForResult(
                        this,
                        LumydUtils.CONNECTION_FAILURE_RESOLUTION_REQUEST);

                /*
                * Thrown if Google Play services canceled the original
                * PendingIntent
                */

            } catch (IntentSender.SendIntentException e) {

                // Log the error
                e.printStackTrace();
            }
        } else {

            // If no resolution is available, display a dialog to the user with the error.
            showErrorDialog(connectionResult.getErrorCode());
            Log.e(LumydUtils.APPTAG, "result " + connectionResult.getErrorCode());
        }
    }

    /**
     * Show a dialog returned by Google Play services for the
     * connection error code
     *
     * @param errorCode An error code returned from onConnectionFailed
     */
    private void showErrorDialog(int errorCode) {

        // Get the error dialog from Google Play services
        Dialog errorDialog = GooglePlayServicesUtil.getErrorDialog(
            errorCode,
            this,
            LumydUtils.CONNECTION_FAILURE_RESOLUTION_REQUEST);

        // If Google Play services can provide an error dialog
        if (errorDialog != null) {

            // Create a new DialogFragment in which to show the error dialog
            ErrorDialogFragment errorFragment = new ErrorDialogFragment();

            // Set the dialog in the DialogFragment
            errorFragment.setDialog(errorDialog);

            // Show the error dialog in the DialogFragment
            errorFragment.show(getSupportFragmentManager(), LumydUtils.APPTAG);
        }
    }

    /**
     * Define a DialogFragment to display the error dialog generated in
     * showErrorDialog.
     */
    public static class ErrorDialogFragment extends DialogFragment {

        // Global field to contain the error dialog
        private Dialog mDialog;

        /**
         * Default constructor. Sets the dialog field to null
         */
        public ErrorDialogFragment() {
            super();
            mDialog = null;
        }

        /**
         * Set the dialog to display
         *
         * @param dialog An error dialog
         */
        public void setDialog(Dialog dialog) {
            mDialog = dialog;
        }

        /*
         * This method must return a Dialog to the DialogFragment.
         */
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return mDialog;
        }
    }

    /**
     * Async Task to ask the server for nearby users.
     */
    private class ListUsersTask extends AsyncTask<Void, Void, CollectionResponseUser> {

        @Override
        protected CollectionResponseUser doInBackground(Void... unused) {
            Point point = new Point();
            point.setLat(mCurrentLocation.getLatitude());
            point.setLon(mCurrentLocation.getLongitude());
            try {
                mlocalUsers = mUserEndpoint.listUser(mDistance, point).execute();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return mlocalUsers;
        }

        protected void onPostExecute(CollectionResponseUser userCollection) {
            if( userCollection != null && ! userCollection.isEmpty()){
                //Populate Users nearby view.
                mUserList = userCollection.getItems();
                ListView userListView = (ListView) findViewById(R.id.listView);
                userListView.setAdapter(new UserArrayAdapter<User>(
                        mContext, android.R.layout.simple_list_item_1, mUserList));
                userListView.setOnItemClickListener(mUserClickedHandler);
            }
        }

    }

    /**
     * Async Task to add / update the user.
     */
    private class InsertUserTask extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... unused) {
            mUser.setAge(12);
            mUser.setName(mSettings.getString(SettingsActivity.KEY_PREF_NAME, "Anonymous"));
            mUser.setLatitude(mCurrentLocation.getLatitude());
            mUser.setLongitude(mCurrentLocation.getLongitude());
            String regId = LumydUtils.getRegistrationId(mContext, mSettings);
            if(regId == null) {
                //TODO: Flag to update user before he can chat when RegId available
                Log.e(LumydUtils.APPTAG, "GCM RegId not set when inserting user");
            }
            mUser.setSourceDevice(regId);
            try {
                mUserEndpoint.insertUser(mUser).execute();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }
    }

    /**
     * Registers the application with GCM servers asynchronously.
     * <p>
     * Stores the registration id, app versionCode, and expiration time in the application's
     * shared preferences.
     */
    protected void registerBackground() {
        new AsyncTask<Void, Void, String>() {
            @Override
            protected String doInBackground(Void... params) {
                String msg = "";
                try {
                    if (mGcm == null) {
                        mGcm = GoogleCloudMessaging.getInstance(mContext);
                    }
                    mRegid = mGcm.register(LumydUtils.SENDER_ID);
                    msg = "Device registered, registration id=" + mRegid;
                    Log.v(LumydUtils.APPTAG, msg);

                    // Save the regid - no need to register again.
                    LumydUtils.setRegistrationId(mContext, mRegid, mSettings);
                } catch (IOException ex) {
                    msg = "Error :" + ex.getMessage();
                }
                return msg;
            }
        }.execute(null, null, null);
    }


    /**
     * Launch MessagingIntent when clicked on an user from the list.
     */
    private AdapterView.OnItemClickListener mUserClickedHandler = new AdapterView.OnItemClickListener() {
        public void onItemClick(AdapterView parent, View v, int position, long id) {
            User selectedUser = mUserList.get((int)id);
            Intent intent = new Intent(mContext, MessagingActivity.class);
            intent.putExtra(LumydUtils.USER_ID, mRegid);
            intent.putExtra(LumydUtils.SELECTED_USER_ID, selectedUser.getSourceDevice());
            startActivity(intent);
        }
    };
}
