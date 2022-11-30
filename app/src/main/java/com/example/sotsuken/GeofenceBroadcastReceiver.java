package com.example.sotsuken;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingEvent;

import java.util.List;

public class GeofenceBroadcastReceiver extends BroadcastReceiver {

    private static final String TAG = "GeofenceBroadcast";
    private TextToSpeech tts;

    @Override
    public void onReceive(Context context, Intent intent) {
        // TODO: This method is called when the BroadcastReceiver is receiving
        // an Intent broadcast.
        // Toast.makeText(context, "Geofence triggered", Toast.LENGTH_SHORT).show();

        NotificationHelper notificationHelper = new NotificationHelper(context);

        GeofencingEvent geofencingEvent = GeofencingEvent.fromIntent(intent);

        if (geofencingEvent.hasError()){
            Log.d(TAG, "onReceive: Error receiving geofence event...");
            return;
        }


        List<Geofence> geofenceList = geofencingEvent.getTriggeringGeofences();
        for (Geofence geofence: geofenceList) {
            Log.d(TAG, "onReceive: " + geofence.getRequestId());
        }
//        Location location = geofencingEvent.getTriggeringLocation();
        int transitionType = geofencingEvent.getGeofenceTransition();

        switch (transitionType) {
            case Geofence.GEOFENCE_TRANSITION_ENTER:
                Toast.makeText(context, "GEOFENCE_TRANSITION_ENTER", Toast.LENGTH_SHORT).show();
                //speechText("直進");
                notificationHelper.sendHighPriorityNotification("GEOFENCE_TRANSITION_ENTER", "", MapsActivity.class);
                break;
            case Geofence.GEOFENCE_TRANSITION_EXIT:
                Toast.makeText(context, "GEOFENCE_TRANSITION_EXIT", Toast.LENGTH_SHORT).show();
                notificationHelper.sendHighPriorityNotification("GEOFENCE_TRANSITION_EXIT", "", MapsActivity.class);
                break;
        }


    }

    private void speechText(String text) {
        if (0 < text.length()) {
            if (tts.isSpeaking()) {
                tts.stop();
                return;
            }
            setSpeechRate();
            setSpeechPitch();

            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "messageID");
            setTtsListener();
        }
    }

    private void setTtsListener() {
        int listenerResult =
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override
                    public void onDone(String utteranceId) {
                        Log.d("setTtsListener", "progress on Done " + utteranceId);
                    }

                    @Override
                    public void onError(String utteranceId) {
                        Log.d("setTtsListener", "progress on Error " + utteranceId);
                    }

                    @Override
                    public void onStart(String utteranceId) {
                        Log.d("setTtsListener", "progress on Start " + utteranceId);
                    }
                });

        if (listenerResult != TextToSpeech.SUCCESS) {
            Log.e("setTtsListener", "failed to add utterance progress listener");
        }
    }

    private void setSpeechPitch() {
        if (null != tts) {
            tts.setPitch((float) 1.0);
        }
    }

    private void setSpeechRate() {
        if (null != tts) {
            tts.setSpeechRate((float) 1.0);
        }
    }
}