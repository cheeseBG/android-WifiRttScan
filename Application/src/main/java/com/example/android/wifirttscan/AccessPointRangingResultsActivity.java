/*
 * Copyright (C) 2018 Google Inc. All Rights Reserved.
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
package com.example.android.wifirttscan;

import android.Manifest.permission;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.wifi.ScanResult;
import android.net.wifi.rtt.RangingRequest;
import android.net.wifi.rtt.RangingResult;
import android.net.wifi.rtt.RangingResultCallback;
import android.net.wifi.rtt.WifiRttManager;
import android.os.Bundle;
import android.os.Handler;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Displays ranging information about a particular access point chosen by the user. Uses {@link
 * Handler} to trigger new requests based on
 */
public class AccessPointRangingResultsActivity extends AppCompatActivity {
    private static final String TAG = "APRRActivity";

    public static final String SCAN_RESULT_EXTRA =
            "com.example.android.wifirttscan.extra.SCAN_RESULT";

    private static final int SAMPLE_SIZE_DEFAULT = 50;
    private static final int MILLISECONDS_DELAY_BEFORE_NEW_RANGING_REQUEST_DEFAULT = 1000;
    private final int NUMBER_OF_LOCATION = 3;
    private final int COORDINATE = 2;

    // UI Elements.
    private TextView mSsidTextView;
    private TextView mBssidTextView;

    private TextView mRangeTextView;
    private TextView mRangeMeanTextView;
//    private TextView mRangeSDTextView;
//    private TextView mRangeSDMeanTextView;
    private TextView mRssiTextView;
    private TextView mSuccessesInBurstTextView;
    private TextView mSuccessRatioTextView;
    private TextView mNumberOfRequestsTextView;

    private EditText mSampleSizeEditText;
    private EditText mMillisecondsDelayBeforeNewRangingRequestEditText;

    // Added Elements by cheeseBG
    private TextView mCoordinate1TextView, mCoordinate2TextView, mCoordinate3TextView;
    private TextView mRangeMean1TextView, mRangeMean2TextView, mRangeMean3TextView;
    private TextView mAPCoordinateTextView;
    private Button mLocationButton1, mLocationButton2, mLocationButton3;

    // For Find AP
    private float[][] mCoordinate = new float[NUMBER_OF_LOCATION][COORDINATE];
    private float[] mRangeMean = new float[NUMBER_OF_LOCATION];
    private float[] mAPCoordinate = new float[COORDINATE];
    private int mLocBtnFlag = 0;
    private int mResetBtnFlag = -1;

    // Non UI variables.
    private ScanResult mScanResult;
    private String mMAC;

    private int mNumberOfRangeRequests;
    private int mNumberOfSuccessfulRangeRequests;

    private int mMillisecondsDelayBeforeNewRangingRequest;

    // Max sample size to calculate average for
    // 1. Distance to device (getDistanceMm) over time
    // 2. Standard deviation of the measured distance to the device (getDistanceStdDevMm) over time
    // Note: A RangeRequest result already consists of the average of 7 readings from a burst,
    // so the average in (1) is the average of these averages.
    private int mSampleSize;

    // Used to loop over a list of distances to calculate averages (ensures data structure never
    // get larger than sample size).
    private int mStatisticRangeHistoryEndIndex;
    private ArrayList<Integer> mStatisticRangeHistory;

    // Used to loop over a list of the standard deviation of the measured distance to calculate
    // averages  (ensures data structure never get larger than sample size).
    private int mStatisticRangeSDHistoryEndIndex;
    private ArrayList<Integer> mStatisticRangeSDHistory;

    private WifiRttManager mWifiRttManager;
    private RttRangingResultCallback mRttRangingResultCallback;

    // Triggers additional RangingRequests with delay (mMillisecondsDelayBeforeNewRangingRequest).
    final Handler mRangeRequestDelayHandler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_access_point_ranging_results);

        // Initializes UI elements.
        mSsidTextView = findViewById(R.id.ssid);
        mBssidTextView = findViewById(R.id.bssid);

        mRangeTextView = findViewById(R.id.range_value);
        mRangeMeanTextView = findViewById(R.id.range_mean_value);
//        mRangeSDTextView = findViewById(R.id.range_sd_value);
//        mRangeSDMeanTextView = findViewById(R.id.range_sd_mean_value);
        mRssiTextView = findViewById(R.id.rssi_value);
        mSuccessesInBurstTextView = findViewById(R.id.successes_in_burst_value);
        mSuccessRatioTextView = findViewById(R.id.success_ratio_value);
        mNumberOfRequestsTextView = findViewById(R.id.number_of_requests_value);

        mSampleSizeEditText = findViewById(R.id.stats_window_size_edit_value);
        mSampleSizeEditText.setText(SAMPLE_SIZE_DEFAULT + "");

        mMillisecondsDelayBeforeNewRangingRequestEditText =
                findViewById(R.id.ranging_period_edit_value);
        mMillisecondsDelayBeforeNewRangingRequestEditText.setText(
                MILLISECONDS_DELAY_BEFORE_NEW_RANGING_REQUEST_DEFAULT + "");

        // Initializes added UI elements by cheeseBG
        mCoordinate1TextView = findViewById(R.id.coordinate1);
        mCoordinate2TextView = findViewById(R.id.coordinate2);
        mCoordinate3TextView = findViewById(R.id.coordinate3);

        mRangeMean1TextView = findViewById(R.id.range_mean1);
        mRangeMean2TextView = findViewById(R.id.range_mean2);
        mRangeMean3TextView = findViewById(R.id.range_mean3);

        mAPCoordinateTextView = findViewById(R.id.ap_coordinate);

        mLocationButton1 = findViewById(R.id.location1_btn);
        mLocationButton2 = findViewById(R.id.location2_btn);
        mLocationButton3 = findViewById(R.id.location3_btn);


        // Retrieve ScanResult from Intent.
        Intent intent = getIntent();
        mScanResult = intent.getParcelableExtra(SCAN_RESULT_EXTRA);



        if (mScanResult == null) {
            finish();
        }

        mMAC = mScanResult.BSSID;

        mSsidTextView.setText(mScanResult.SSID);
        mBssidTextView.setText(mScanResult.BSSID);

        mWifiRttManager = (WifiRttManager) getSystemService(Context.WIFI_RTT_RANGING_SERVICE);
        mRttRangingResultCallback = new RttRangingResultCallback();

        // Used to store range (distance) and rangeSd (standard deviation of the measured distance)
        // history to calculate averages.
        mStatisticRangeHistory = new ArrayList<>();
        mStatisticRangeSDHistory = new ArrayList<>();


        resetData();

        startRangingRequest();
    }

    private void resetData() {
        mSampleSize = Integer.parseInt(mSampleSizeEditText.getText().toString());

        mMillisecondsDelayBeforeNewRangingRequest =
                Integer.parseInt(
                        mMillisecondsDelayBeforeNewRangingRequestEditText.getText().toString());

        mNumberOfSuccessfulRangeRequests = 0;
        mNumberOfRangeRequests = 0;

        mStatisticRangeHistoryEndIndex = 0;
        mStatisticRangeHistory.clear();
        mStatisticRangeSDHistoryEndIndex = 0;
        mStatisticRangeSDHistory.clear();
    }

    private void startRangingRequest() {
        // Permission for fine location should already be granted via MainActivity (you can't get
        // to this class unless you already have permission. If they get to this class, then disable
        // fine location permission, we kick them back to main activity.
        if (ActivityCompat.checkSelfPermission(this, permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            finish();
        }

        mNumberOfRangeRequests++;

        RangingRequest rangingRequest =
                new RangingRequest.Builder().addAccessPoint(mScanResult).build();

        mWifiRttManager.startRanging(
                rangingRequest, getApplication().getMainExecutor(), mRttRangingResultCallback);
    }

    // Calculates average distance based on stored history.
    private float getDistanceMean() {
        float distanceSum = 0;

        for (int distance : mStatisticRangeHistory) {
            distanceSum += distance;
        }

        return distanceSum / mStatisticRangeHistory.size();
    }

    // Adds distance to history. If larger than sample size value, loops back over and replaces the
    // oldest distance record in the list.
    private void addDistanceToHistory(int distance) {

        if (mStatisticRangeHistory.size() >= mSampleSize) {

            if (mStatisticRangeHistoryEndIndex >= mSampleSize) {
                mStatisticRangeHistoryEndIndex = 0;
            }

            mStatisticRangeHistory.set(mStatisticRangeHistoryEndIndex, distance);
            mStatisticRangeHistoryEndIndex++;

        } else {
            mStatisticRangeHistory.add(distance);
        }
    }

    // Calculates standard deviation of the measured distance based on stored history.
    private float getStandardDeviationOfDistanceMean() {
        float distanceSdSum = 0;

        for (int distanceSd : mStatisticRangeSDHistory) {
            distanceSdSum += distanceSd;
        }

        return distanceSdSum / mStatisticRangeHistory.size();
    }

    // Adds standard deviation of the measured distance to history. If larger than sample size
    // value, loops back over and replaces the oldest distance record in the list.
    private void addStandardDeviationOfDistanceToHistory(int distanceSd) {

        if (mStatisticRangeSDHistory.size() >= mSampleSize) {

            if (mStatisticRangeSDHistoryEndIndex >= mSampleSize) {
                mStatisticRangeSDHistoryEndIndex = 0;
            }

            mStatisticRangeSDHistory.set(mStatisticRangeSDHistoryEndIndex, distanceSd);
            mStatisticRangeSDHistoryEndIndex++;

        } else {
            mStatisticRangeSDHistory.add(distanceSd);
        }
    }

    // Added by cheeseBG
    // Return AP's coordinate
    private float[] trilateration(float[][] coordinates, float[] rangeMean)
    {
        float[] apCoordinate = new float[COORDINATE];

        float x1 = coordinates[0][0];
        float x2 = coordinates[1][0];
        float x3 = coordinates[2][0];
        float y1 = coordinates[0][1];
        float y2 = coordinates[1][1];
        float y3 = coordinates[2][1];

        float d1 = rangeMean[0];
        float d2 = rangeMean[1];
        float d3 = rangeMean[2];

        float A = (-2 * x1) + (2 * x2);
        float B = (-2 * y1) + (2 * y2);
        float C = (float)(Math.pow(d1, 2) - Math.pow(d2, 2)
                - Math.pow(x1, 2) - Math.pow(x2, 2)
                - Math.pow(y1, 2) - Math.pow(y2, 2));
        float D = (-2 * x2) + (2 * x3);
        float E = (-2 * y2) + (2 * y3);
        float F = (float)(Math.pow(d2, 2) - Math.pow(d3, 2)
                - Math.pow(x2, 2) - Math.pow(x3, 2)
                - Math.pow(y2, 2) - Math.pow(y3, 2));

        float x = ((C * E) - (F * B)) / ((E * A) - (B * D));
        float y = ((C * D) - (A * F)) / ((B * D) - (A * E));

        apCoordinate[0] = x;
        apCoordinate[1] = y;

        return apCoordinate;

    }

    // Added by cheeseBG
    // Find Coordinate method.
    private float[] findCoordinate(float[] coordinate, float distance, float angle)
    {
        float x = coordinate[0];
        float y = coordinate[1];

        // return value
        float[] resultCoordinate = new float[2];




        resultCoordinate[0] = (float)(distance * Math.cos(Math.toRadians(angle))) + x; // x result
        resultCoordinate[1] = (float)(distance * Math.sin(Math.toRadians(angle))) + y; // y result

        return resultCoordinate;
    }


    // Check whether three points are in line
    private boolean checkInLine(float[][] coordinates)
    {
        float[] resultCoordinate = new float[2];
        boolean check = false;

        float x1 = coordinates[0][0];
        float x2 = coordinates[1][0];
        float x3 = coordinates[2][0];
        float y1 = coordinates[0][1];
        float y2 = coordinates[1][1];
        float y3 = coordinates[2][1];

        float triangleWidth = ((x1 * y2) + (x2 * y3) + (x2 * y1)) - ((x2 * y1) + (x3 * y2) + (x1 * y3));

        if (triangleWidth == 0)
            check = true;
        else
            check = false;

        return check;
    }

    // If checkInLine is true, find AP's coordinate with this method
    private float findAPWithTwoPoints


    public void onResetButtonClick(View view) {
        mResetBtnFlag = mLocBtnFlag;
        resetData();
    }


    // Class that handles callbacks for all RangingRequests and issues new RangingRequests.
    private class RttRangingResultCallback extends RangingResultCallback {

        private void queueNextRangingRequest() {
            mRangeRequestDelayHandler.postDelayed(
                    new Runnable() {
                        @Override
                        public void run() {
                            startRangingRequest();
                        }
                    },
                    mMillisecondsDelayBeforeNewRangingRequest);
        }

        @Override
        public void onRangingFailure(int code) {
            Log.d(TAG, "onRangingFailure() code: " + code);
            queueNextRangingRequest();
        }

        @Override
        public void onRangingResults(@NonNull List<RangingResult> list) {
            Log.d(TAG, "onRangingResults(): " + list);

            // Because we are only requesting RangingResult for one access point (not multiple
            // access points), this will only ever be one. (Use loops when requesting RangingResults
            // for multiple access points.)
            if (list.size() == 1) {

                RangingResult rangingResult = list.get(0);

                if (mMAC.equals(rangingResult.getMacAddress().toString())) {

                    if (rangingResult.getStatus() == RangingResult.STATUS_SUCCESS) {

                        mNumberOfSuccessfulRangeRequests++;

                        mRangeTextView.setText((rangingResult.getDistanceMm() / 1000f) + "");
                        addDistanceToHistory(rangingResult.getDistanceMm());
                        mRangeMeanTextView.setText((getDistanceMean() / 1000f) + "");

//                        mRangeSDTextView.setText(
//                                (rangingResult.getDistanceStdDevMm() / 1000f) + "");
//                        addStandardDeviationOfDistanceToHistory(
//                                rangingResult.getDistanceStdDevMm());
//                        mRangeSDMeanTextView.setText(
//                                (getStandardDeviationOfDistanceMean() / 1000f) + "");

                        mRssiTextView.setText(rangingResult.getRssi() + "");
                        mSuccessesInBurstTextView.setText(
                                rangingResult.getNumSuccessfulMeasurements()
                                        + "/"
                                        + rangingResult.getNumAttemptedMeasurements());


                        //TODO: Button Listener
                        mLocationButton1.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {

                                if(mResetBtnFlag == -1){
                                    Toast.makeText(
                                            getApplicationContext(),
                                            "You should click reset button first",
                                            Toast.LENGTH_LONG)
                                            .show();
                                }
                                else if(mLocBtnFlag == 0){
                                    mCoordinate[0][0] = 0; // Initialize x1
                                    mCoordinate[0][1] = 0; // Initialize y1

                                    mRangeMean[0] = getDistanceMean() / 1000f;

                                    mCoordinate1TextView.setText("(" + String.format("%.2f", mCoordinate[0][0]) + ", "
                                            + String.format("%.2f", mCoordinate[0][1]) + ")");
                                    mRangeMean1TextView.setText(String.format("%.2f", mRangeMean[0]));

                                    mLocBtnFlag = 1;
                                }
                                else{
                                    Toast.makeText(
                                            getApplicationContext(),
                                           "Location1: already decided",
                                            Toast.LENGTH_LONG)
                                            .show();
                                }

                            }
                        });

                        mLocationButton2.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {

                                if(mResetBtnFlag == 0){
                                    Toast.makeText(
                                            getApplicationContext(),
                                            "You should click reset button first",
                                            Toast.LENGTH_LONG)
                                            .show();
                                }
                                else if(mLocBtnFlag == 1){
                                    mCoordinate[1] = findCoordinate(mCoordinate[0], 1, 90);

                                    mRangeMean[1] = getDistanceMean() / 1000f;

                                    mCoordinate2TextView.setText("(" + String.format("%.2f", mCoordinate[1][0]) + ", "
                                            + String.format("%.2f", mCoordinate[1][1]) + ")");
                                    mRangeMean2TextView.setText(String.format("%.2f", mRangeMean[1]));

                                    mLocBtnFlag = 2;
                                }
                                else if(mLocBtnFlag == 0){
                                    Toast.makeText(
                                            getApplicationContext(),
                                            "Find location1",
                                            Toast.LENGTH_LONG)
                                            .show();
                                }
                                else{
                                    Toast.makeText(
                                            getApplicationContext(),
                                            "Location2: already decided",
                                            Toast.LENGTH_LONG)
                                            .show();
                                }
                            }
                        });

                        mLocationButton3.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {

                                if(mResetBtnFlag == 1){
                                    Toast.makeText(
                                            getApplicationContext(),
                                            "You should click reset button first",
                                            Toast.LENGTH_LONG)
                                            .show();
                                }
                                else if(mLocBtnFlag == 2){
                                    mCoordinate[2] = findCoordinate(mCoordinate[1], 1, 0);

                                    mRangeMean[2] = getDistanceMean() / 1000f;

                                    mCoordinate3TextView.setText("(" + String.format("%.2f", mCoordinate[2][0]) + ", "
                                            + String.format("%.2f", mCoordinate[2][1]) + ")");
                                    mRangeMean3TextView.setText(String.format("%.2f", mRangeMean[2]));

                                    // Find AP's coordinate
                                    if (checkInLine(mCoordinate))
                                        mAPCoordinate = ;
                                    else
                                        mAPCoordinate = trilateration(mCoordinate, mRangeMean);

                                    mAPCoordinateTextView.setText("(" + mAPCoordinate[0] + ", "
                                            + mAPCoordinate[1] + ")");

                                    mLocBtnFlag = 3;
                                }
                                else{
                                    Toast.makeText(
                                            getApplicationContext(),
                                            "Find location1,2",
                                            Toast.LENGTH_LONG)
                                            .show();
                                }

                            }
                        });
                        

                        float successRatio =
                                ((float) mNumberOfSuccessfulRangeRequests
                                                / (float) mNumberOfRangeRequests)
                                        * 100;
                        mSuccessRatioTextView.setText(successRatio + "%");

                        mNumberOfRequestsTextView.setText(mNumberOfRangeRequests + "");

                    } else if (rangingResult.getStatus()
                            == RangingResult.STATUS_RESPONDER_DOES_NOT_SUPPORT_IEEE80211MC) {
                        Log.d(TAG, "RangingResult failed (AP doesn't support IEEE80211 MC.");

                    } else {
                        Log.d(TAG, "RangingResult failed.");
                    }

                } else {
                    Toast.makeText(
                                    getApplicationContext(),
                                    R.string
                                            .mac_mismatch_message_activity_access_point_ranging_results,
                                    Toast.LENGTH_LONG)
                            .show();
                }
            }

            queueNextRangingRequest();
        }
    }
}
