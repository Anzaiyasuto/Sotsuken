package com.example.sotsuken;

import static androidx.constraintlayout.helper.widget.MotionEffect.TAG;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingClient;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.net.PlacesClient;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;


/***
 * 実装手順
 * テンプレートにGCPのAPIキーを入力
 * カメラ設定
 * 起動時に現在地を表示
 * 長押しすることでマーカー出現or Androidアプリ開発の教科書14章参考
 * その後にdirectionAPI起動
 * 経路描写
 * xfreeサーバにAPI叩く⇔イマココ
 * dbサーバに接続して指定した経路の事故データゲット(仮設置)
 * 経路を含む運転区間の最大最小緯度経度を算出(polyline)
 * DirectionJSONファイルから道案内(html_instructions)を抽出＆文字コード整形＆音声案内登録
 *
 * 2022年11月29日やること
 * ジオフェンスの設定
 * 音声案内登録
 * DirectionJSONファイルの解析とジオフェンスによる音声案内登録
 */
public class MapsActivity extends FragmentActivity
        implements OnMapReadyCallback, GoogleMap.OnMyLocationButtonClickListener,
        GoogleMap.OnMyLocationClickListener, LocationListener,
        ActivityCompat.OnRequestPermissionsResultCallback, GoogleMap.OnMapLongClickListener, GoogleMap.OnMarkerClickListener, TextToSpeech.OnInitListener {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    private static final int PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 1;
    private static final int DEFAULT_ZOOM = 15;
    private final LatLng defaultLocation = new LatLng(35.6809591, 139.7673068);
    Handler mMainHandler = new Handler(Looper.getMainLooper());
    GeofencingClient geofencingClient;
    private final float GEOFENCE_RADIUS = 100;
    private GoogleMap mMap;
    private Marker marker;
    private boolean locationPermissionGranted;
    private LatLng current;
    private Location myLocation;
    private FusedLocationProviderClient fusedLocationProviderClient;
    private Location lastKnownLocation;
    private TextToSpeech tts;
    private GeofenceHelper geofenceHelper;
    private final String GEOFENCE_ID = "SOME_GEOFENCE_ID";
    private LocationManager locationManager;
    private TextView textView;
    private float speed = 0f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //com.example.sotsuken.databinding.ActivityMapsBinding binding = ActivityMapsBinding.inflate(getLayoutInflater());
        setContentView(R.layout.activity_maps);

        Places.initialize(getApplicationContext(), getString(R.string.google_maps_key));
        PlacesClient placesClient = Places.createClient(this);
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);

        //11.4
        tts = new TextToSpeech(this, this);

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        //Geofence setting
        geofencingClient = LocationServices.getGeofencingClient(this);
        geofenceHelper = new GeofenceHelper(this);

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        textView = (TextView) findViewById(R.id.speed_text);
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @SuppressLint("MissingPermission")
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        //仮の中心座標
        //アプリ起動時に現在地があればそれでよし
        // Add a marker in Sydney and move the camera
        /*
        LatLng tokyo = new LatLng(35.6809591, 139.7673068);
        marker = mMap.addMarker(new MarkerOptions().position(tokyo).title("Marker in Tokyo"));
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(tokyo, 14.0f));
        */
        mMap.setMyLocationEnabled(true);
        //mMap.getUiSettings().setMyLocationButtonEnabled(true);
        updateLocationUI();//こいつが現在地ボタンに影響している
        getDeviceLocation();
        //現在地設定
        mMap.setOnMyLocationButtonClickListener(this);
        mMap.setOnMyLocationClickListener(this);


        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        enableMyLocation();


        //長押しでマーカー追加
        //マーカーは増殖しないで最新の物のみ残す
        //現在地のマーカーをタップした後に新しくマーカーを立てないと落ちる
        //何故なら、LatLng型のcurrentが現在地を初期から保持していないから
        /*
        mMap.setOnMapLongClickListener(longpushLocation -> {
            LatLng target = new LatLng(longpushLocation.latitude, longpushLocation.longitude);

            marker.remove();
            marker = mMap.addMarker(new MarkerOptions().position(target).title("" + longpushLocation.latitude + " :" + longpushLocation.longitude));
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(target, 14));

            Toast.makeText(this, "current:("+current.latitude +","+current.longitude+ ")\ntarget:(" + longpushLocation.latitude +","+ longpushLocation.longitude+")", Toast.LENGTH_LONG).show();

        });

         */
        mMap.setOnMapLongClickListener(this);


    }

    private void getLocationPermission() {
        /*
         * Request location permission, so that we can get the location of the
         * device. The result of the permission request is handled by a callback,
         * onRequestPermissionsResult.
         */
        if (ContextCompat.checkSelfPermission(this.getApplicationContext(),
                android.Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            locationPermissionGranted = true;
            Log.d("debug", "locationPermissionGranted = true");
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                    PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
        }
    }


    private void updateLocationUI() {
        if (mMap == null) {
            return;
        }
        try {
            if (locationPermissionGranted) {
                Log.d("debug", "updateLocationUI() try->if->true");
                mMap.setMyLocationEnabled(true);
                mMap.getUiSettings().setMyLocationButtonEnabled(true);
            } else {
                Log.d("debug", "updateLocationUI() try->else->false");
                mMap.setMyLocationEnabled(false);
                mMap.getUiSettings().setMyLocationButtonEnabled(false);
                lastKnownLocation = null;
                getLocationPermission();
                mMap.setMyLocationEnabled(true);
                mMap.getUiSettings().setMyLocationButtonEnabled(true);
            }
        } catch (SecurityException e) {
            Log.e("Exception: %s", e.getMessage());
        }
    }


    private void getDeviceLocation() {
        /*
        if (locationPermissionGranted) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return;
            }
            Task<Location> locationTask = fusedLocationProviderClient.getLastLocation();
            locationTask.addOnCompleteListener(this, new OnCompleteListener<Location>() {
                @Override
                public void onComplete(@NonNull @NotNull Task<Location> task) {
                    if (task.isSuccessful()) {
                        myLocation = task.getResult();
                        if (myLocation != null) {
                            // カメラ位置移動
                            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(myLocation.getLatitude(), myLocation.getLongitude()), 15));
                        }
                    }
                }
            });
        }
         */
        /*
         * Get the best and most recent location of the device, which may be null in rare
         * cases when a location is not available.
         */
        try {
            if (locationPermissionGranted) {
                Task<Location> locationResult = fusedLocationProviderClient.getLastLocation();
                locationResult.addOnCompleteListener(this, new OnCompleteListener<Location>() {
                    @Override
                    public void onComplete(@NonNull Task<Location> task) {
                        if (task.isSuccessful()) {
                            // Set the map's camera position to the current location of the device.
                            lastKnownLocation = task.getResult();
                            if (lastKnownLocation != null) {
                                Log.d("getDeviceLocation()", "lastKnownLocation=(" + lastKnownLocation.getLatitude() + ","+ lastKnownLocation.getLongitude() + ")");
                                //mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(lastKnownLocation.getLatitude(),lastKnownLocation.getLongitude()), DEFAULT_ZOOM));
                                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(lastKnownLocation.getLatitude(),lastKnownLocation.getLongitude()), DEFAULT_ZOOM));
                            }
                        } else {
                            Log.d(TAG, "Current location is null. Using defaults.");
                            Log.e(TAG, "Exception: %s", task.getException());
                            mMap.moveCamera(CameraUpdateFactory
                                    .newLatLngZoom(defaultLocation, DEFAULT_ZOOM));
                            mMap.getUiSettings().setMyLocationButtonEnabled(true);
                        }
                    }
                });
            }
        } catch (SecurityException e) {
            Log.e("Exception: %s", e.getMessage(), e);
        }
    }

    @SuppressLint("MissingPermission")
    private void enableMyLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            Log.d("debug", "setMyLocationEnabled(true)");
            mMap.setMyLocationEnabled(true);
            return;
        }

        // 2. Otherwise, request location permissions from the user.
        PermissionUtils.requestLocationPermissions(this, LOCATION_PERMISSION_REQUEST_CODE, true);
    }

    @Override
    public boolean onMyLocationButtonClick() {
        Toast.makeText(this, "MyLocation button clicked", Toast.LENGTH_SHORT).show();
        // Return false so that we don't consume the event and the default behavior still occurs
        // (the camera animates to the user's current position).
        return false;
    }

    @Override
    public void onMyLocationClick(@NonNull Location location) {
        current = new LatLng(location.getLatitude(), location.getLongitude());
        //mMap.addMarker(new MarkerOptions().position(tokyo).title("Marker in Tokyo"));
        mMap.moveCamera(CameraUpdateFactory.newLatLng(current));
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(current, DEFAULT_ZOOM));

        Toast.makeText(this, "Current location:\n" + location, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode != LOCATION_PERMISSION_REQUEST_CODE) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            return;
        }

        if (PermissionUtils.isPermissionGranted(permissions, grantResults,
                Manifest.permission.ACCESS_FINE_LOCATION) || PermissionUtils
                .isPermissionGranted(permissions, grantResults,
                        Manifest.permission.ACCESS_COARSE_LOCATION)) {
            // Enable the my location layer if the permission has been granted.
            enableMyLocation();
        } else {
            // Permission was denied. Display an error message
            // Display the missing permission error dialog when the fragments resume.
            locationPermissionGranted = true;
        }
        updateLocationUI();
    }



    /*
    @Override
    protected void onResumeFragments() {
        super.onResumeFragments();
        if (locationPermissionGranted) {
            // Permission was not granted, display error dialog.
            showMissingPermissionError();
            locationPermissionGranted = false;
        }
    }
     */

    /**
     * Displays a dialog with error message explaining that the location permission is missing.
     */
    private void showMissingPermissionError() {
        PermissionUtils.PermissionDeniedDialog
                .newInstance(true).show(getSupportFragmentManager(), "dialog");
    }

    @Override
    public void onMapLongClick(@NonNull LatLng latLng) {
        Log.d("debug", String.valueOf(current));
        if (marker != null) {
            marker.remove();
            mMap.clear();
            geofencingClient.removeGeofences(geofenceHelper.getPendingIntent());
        }
        marker = mMap.addMarker(new MarkerOptions().position(latLng).title("(" + latLng.latitude + "," + latLng.longitude + ")"));
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 14));
        mMap.setOnMarkerClickListener(this);
        //"current:("+current.latitude +","+current.longitude+ ")\ntarget:(" +
        Toast.makeText(this, latLng.latitude + "," + latLng.longitude + ")", Toast.LENGTH_LONG).show();

        //20221102
        //最終的に冗長
        //handleMapLongClick(latLng);

    }

    private void handleMapLongClick(LatLng latLng) {
        addCircle(latLng, GEOFENCE_RADIUS);
        addGeofence(latLng, GEOFENCE_RADIUS);

    }

    private void addGeofence(LatLng latLng, float radius) {
        Geofence geofence = geofenceHelper.getGeofence(GEOFENCE_ID, latLng, radius, Geofence.GEOFENCE_TRANSITION_ENTER | Geofence.GEOFENCE_TRANSITION_EXIT);
        GeofencingRequest geofencingRequest = geofenceHelper.getGeofencingRequest(geofence);
        PendingIntent pendingIntent = null;
        pendingIntent = geofenceHelper.getPendingIntent();


        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        geofencingClient.addGeofences(geofencingRequest, pendingIntent).addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void unused) {
                Log.d(TAG, "onSuccess: Added...");
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                String errorMessage = geofenceHelper.getErrorString(e);
                Log.d(TAG, "onFailure: " + e);
            }
        });
    }

    private void addCircle(LatLng latLng, float radius) {
        CircleOptions circleOptions = new CircleOptions();
        circleOptions.center(latLng);
        circleOptions.radius(radius);
        circleOptions.strokeColor(Color.argb(255, 255, 0, 0));
        circleOptions.fillColor(Color.argb(64, 255, 0, 0));
        circleOptions.strokeWidth(4);
        mMap.addCircle(circleOptions);
    }

    @Override
    public boolean onMarkerClick(@NonNull Marker marker) {
        //移動ルート取得

        LatLng latLng = marker.getPosition();
        Log.d("debug", String.valueOf(marker));
        Log.d("debug", String.valueOf(latLng));
        if (latLng == null) return false;
        getDeviceLocation();
        //URL get(direction api)
        String url = getURL(latLng);

        //URL get(handmade web api)
        String apiUrl = getAPIUrl(latLng);

        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                String routeData = getRoute(url);
                Log.i("onMarkerClick", "url = " + url);
                drawRoute(routeData);
                //エリア再定義
                drawSquare(routeData);
                Log.i("onMarkerClick", "apiUrl = " + apiUrl);
                String feedback = getTrafficData(apiUrl);
                //Log.i("debug", feedback);
                //Toast.makeText(this, "aaa", Toast.LENGTH_LONG).show();
            }
        });

        //11.4
        //仮実装
        String text = "600m先,右折してください";
        speechText(text);

        return false;
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

    private void drawSquare(String data) {
        if (data == null) {
            Log.w("SampleMap", "Can not draw route because of no data!!");
            return;
        }

        JSONArray jsonArray = new JSONArray();
        JSONArray legsArray = new JSONArray();
        JSONArray stepArray = new JSONArray();
        //ArrayList<ArrayList<LatLng>> list = new ArrayList<>();
        ArrayList<Double> route_latitude = new ArrayList<>();
        ArrayList<Double> route_longitude = new ArrayList<>();


        try {
            JSONObject jsonObject = new JSONObject(data);
            jsonArray = jsonObject.getJSONArray("routes");
            for (int i = 0; i < jsonArray.length(); i++) {
                legsArray = ((JSONObject) jsonArray.get(i)).getJSONArray("legs");
            }
            for (int i = 0; i < legsArray.length(); i++) {
                stepArray = ((JSONObject) legsArray.get(i)).getJSONArray("steps");
                for (int stepIndex = 0; stepIndex < stepArray.length(); stepIndex++) {
                    JSONObject stepObject = stepArray.getJSONObject(stepIndex);
                    // ルート案内で必要となるpolylineのpointsを取得し、デコード後にリストに格納
                    //list.add(decodePolyline(stepObject.getJSONObject("polyline").get("points").toString()));
                    route_latitude.add(stepObject.getJSONObject("end_location").getDouble("lat"));
                    route_longitude.add(stepObject.getJSONObject("end_location").getDouble("lng"));
                }
            }
        } catch (
                JSONException e) {
            e.printStackTrace();
        }

        mMainHandler.post(new Runnable() {
            @Override
            public void run() {

                PolylineOptions polylineOptions = null;
                polylineOptions = new PolylineOptions();
                Double minX, maxX, minY, maxY;

                //
                Log.i("route_latitude =", String.valueOf(route_latitude));
                Log.i("route_longitude =", String.valueOf(route_longitude));

                //2点間の長方形を作成
                // ラインオプション設定
                polylineOptions.width(10);
                polylineOptions.color(Color.BLACK);
                // ラインを引く
                //mMap.addPolyline(null);

                minX = getListOfMin(route_latitude, lastKnownLocation.getLatitude());
                maxX = getListOfMax(route_latitude, lastKnownLocation.getLatitude());
                minY = getListOfMin(route_longitude, lastKnownLocation.getLongitude());
                maxY = getListOfMax(route_longitude, lastKnownLocation.getLongitude());

                polylineOptions.add(new LatLng(minX, minY))
                        .add(new LatLng(maxX, minY))
                        .add(new LatLng(maxX, maxY))
                        .add(new LatLng(minX, maxY))
                        .add(new LatLng(minX, minY));

                mMap.addPolyline(polylineOptions);
            }

            private Double getListOfMax(ArrayList<Double> point, double start) {
                Double max = start;

                for (int i = 0; i < point.size(); i++) {
                    if (max < point.get(i)) {
                        max = point.get(i);
                    }
                }

                return max;
            }

            private Double getListOfMin(ArrayList<Double> point, double start) {
                Double min = start;

                for (int i = 0; i < point.size(); i++) {
                    if (min > point.get(i)) {
                        min = point.get(i);
                    }
                }

                return min;
            }
        });
    }

    private String getTrafficData(String url) {
        final String[] comment = {null};
        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder().url(url).build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e("Hoge", e.getMessage());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                final String jsonStr = response.body().string();
                //JSONObject jsonObject = new JSONObject(jsonStr);
                //JSONArray alpha = response.body().
                String status = null, message = null;
                //Double latitude[]=null, longitude[]=null;
                ArrayList<Double> latitude = new ArrayList<Double>();
                ArrayList<Double> longitude = new ArrayList<Double>();
                Log.d("getTrafficData()", "jsonStr=" + jsonStr);
                //Log.d("jsonStr type=", String.valueOf(jsonStr instanceof String));
                try {
                    JSONArray jsonArray = new JSONArray(jsonStr);
                    //JSONObject jsonObject = new JSONObject(jsonStr);
                    for (int i = 0; i < jsonArray.length(); i++) {
                        JSONObject jsonObject = jsonArray.getJSONObject(i);
                        //status = jsonObject.getString("id");
                        //message = jsonObject.getString("name");
                        comment[0] = jsonObject.getString("id");
                        latitude.add(jsonObject.getDouble("latitude"));
                        longitude.add(jsonObject.getDouble("longitude"));
                        //latitude = jsonObject.getDouble("latitude");
                        //longitude = jsonObject.getDouble("longitude");
                        //LatLng traffic = new LatLng(latitude, longitude);
                        //marker = mMap.addMarker(new MarkerOptions().position(traffic).title("(" + traffic.latitude + "," + traffic.longitude + ")"));
                        //Log.d("getTrafficData()", jsonObject.getString("latitude") + "," + jsonObject.getString("longitude"));
                    }


                    Handler mainHandler = new Handler(Looper.getMainLooper());
                    //String finalMessage = messag;
                    //String finalStatus = status;
                    String finalComment = comment[0];
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            //txt01.setText(finalComment);
                            //Toast.makeText(getApplicationContext(), finalComment, Toast.LENGTH_LONG);
                            for (int i = 0; i < latitude.size(); i++) {
                                LatLng traffic = new LatLng(latitude.get(i), longitude.get(i));
                                marker = mMap.addMarker(new MarkerOptions().position(traffic).title("(" + traffic.latitude + "," + traffic.longitude + ")"));

                            }
                        }
                    });


                } catch (Exception e) {
                    Log.e("traffic", e.getMessage());
                }
            }
        });
        return comment[0];
    }


    private String getAPIUrl(LatLng latLng) {
        //URLの設定
        //myLocationがヌルぽしがち
        /*
        String str_origin = "origin=" + lastKnownLocation.getLatitude() + "," + lastKnownLocation.getLongitude();
        String str_dest = "destination=" + latLng.latitude + "," + latLng.longitude;
        String mode = "mode=" + "driving";
        String parameters = str_origin + "&" + str_dest + "&" + mode;
        String output = "json";
        String str_url = "https://maps.googleapis.com/maps/api/directions/"
                + output + "?" + parameters + "&key=" + getString(R.string.google_maps_key);
        Log.i("INFORMATION", str_url);
        return str_url;
         */
        float minX = (float) lastKnownLocation.getLatitude();
        float minY = (float) lastKnownLocation.getLongitude();
        float maxX = (float) latLng.latitude;
        float maxY = (float) latLng.longitude;
        float tempX = 0;
        float tempY = 0;

        if (minX > maxX) {
            tempX = minX;
            minX = maxX;
            maxX = tempX;
        }
        if (minY > maxY) {
            tempY = minY;
            minY = maxY;
            maxY = tempY;
        }

        String str_url = "http://al18011.php.xdomain.jp/webapi2.php?";
        String parameter = "minX=" + minX + "&minY=" + minY + "&maxX=" + maxX + "&maxY=" + maxY;
        str_url = str_url + parameter;
        return str_url;
        //return "http://al18011.php.xdomain.jp/webapi2.php?";
    }

    private void drawRoute(String data) {
        if (data == null) {
            Log.w("SampleMap", "Can not draw route because of no data!!");
            return;
        }

        JSONArray jsonArray = new JSONArray();
        JSONArray legsArray = new JSONArray();
        JSONArray stepArray = new JSONArray();
        JSONArray htmlArray = new JSONArray();
        ArrayList<ArrayList<LatLng>> list = new ArrayList<>();
        ArrayList<String> navigationList = new ArrayList<>();
        String alpha = null;
        JSONObject beta = null;
        String distance = null;
        ArrayList<Double> route_latitude = new ArrayList<>();
        ArrayList<Double> route_longitude = new ArrayList<>();
        ArrayList<Double> route_distance = new ArrayList<>();
        ArrayList<String> route_maneuver = new ArrayList<>();
        try {
            JSONObject jsonObject = new JSONObject(data);
            jsonArray = jsonObject.getJSONArray("routes");
            for (int i = 0; i < jsonArray.length(); i++) {
                legsArray = ((JSONObject) jsonArray.get(i)).getJSONArray("legs");
                for (int j = 0; j < legsArray.length(); j++) {
                    stepArray = ((JSONObject) legsArray.get(j)).getJSONArray("steps");

                    for (int stepIndex = 0; stepIndex < stepArray.length(); stepIndex++) {
                        //String polyline= "";
                        //polyline = (String)((JSONObject)((JSONObject)stepArray.get(stepIndex)).get("polyline")).get("points");
                        JSONObject stepObject = stepArray.getJSONObject(stepIndex);
                        // ルート案内で必要となるpolylineのpointsを取得し、デコード後にリストに格納
                        list.add(decodePolyline(stepObject.getJSONObject("polyline").get("points").toString()));
                        //alpha = stepObject.getString("html_instructions");
                        //if(i > 0) beta = stepObject.getString("maneuver");
                        //beta = stepObject.getJSONObject("maneuver");
                        //String instructions = ((JSONObject) stepArray.get(stepIndex)).getString("html_instructions");
                        //JSONObject stepObject = stepArray.getJSONObject(stepIndex);
                        // ルート案内で必要となるpolylineのpointsを取得し、デコード後にリストに格納
                        //list.add(decodePolyline(stepObject.getJSONObject("polyline").get("points").toString()));
                        //Log.d("LIST" , String.valueOf(list));
                        route_latitude.add(stepObject.getJSONObject("end_location").getDouble("lat"));
                        route_longitude.add(stepObject.getJSONObject("end_location").getDouble("lng"));
                        route_distance.add(stepObject.getJSONObject("distance").getDouble("value"));

                        String maneuver = null, distance_txt = null, duration_txt = null;
                        if (stepIndex > 0) {
                            maneuver = ((JSONObject) stepArray.get(stepIndex)).getString("maneuver");
                            route_maneuver.add(stepObject.getString("maneuver"));
                            //distance = String.valueOf(((JSONObject) stepArray.get(stepIndex)).getJSONObject("end_location"));
                            distance_txt = ((JSONObject) ((JSONObject) stepArray.get(stepIndex)).get("distance")).getString("value");
                            duration_txt = ((JSONObject) ((JSONObject) stepArray.get(stepIndex)).get("duration")).getString("value");


                        } else {
                            distance_txt = ((JSONObject) ((JSONObject) stepArray.get(stepIndex)).get("distance")).getString("value");
                        }

                        if (maneuver != null) {
                            /**
                             *  polyline = (String)((JSONObject)((JSONObject)jsonSteps.get(k)).get("polyline")).get("points");
                             *
                             *
                             *  String instructions = (String)((JSONObject)(JSONObject)jsonSteps.get(k)).getString("html_instructions");
                             */
                            Log.i("separete", "--------------------------------------");
                            //Log.i("html_instructions", instructions);
                            Log.i("maneuver", maneuver + ":distance=" + distance_txt + ":duration:" + duration_txt);
                            //addCircle();
                            Log.i("separete", "--------------------------------------");
                        } else {
                            route_maneuver.add("go straight");
                            Log.i("maneuver", "go straight" + distance_txt);
                        }
                        //Log.i("alpha", alpha);
                        //Log.i("step.object", String.valueOf(stepObject.getJSONObject("html_instructions")));
                        //navigationList.add(stepObject.getJSONObject("html_instructions");
                    }
                }
            }

        } catch (JSONException e) {
            e.printStackTrace();
        }
        Log.d("drawRoute()", "lat=" + route_latitude);
        Log.d("drawRoute()", "lon=" + route_longitude);
        Log.d("distance", String.valueOf(route_distance));
        Log.d("maneuver", String.valueOf(route_maneuver));

        JSONArray finalHtmlArray = htmlArray;
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {

                PolylineOptions polylineOptions = null;
                polylineOptions = new PolylineOptions();
                for (int i = 0; i < list.size(); i++) {
                    // 経路を追加
                    polylineOptions.addAll(list.get(i));
                    //Log.i("polylineOptions", String.valueOf(list.get(i)));
                }
                //Log.i("html", String.valueOf(finalHtmlArray));
                //Log.i("navigationList", String.valueOf(navigationList));
                //2点間の長方形を作成
                // ラインオプション設定
                polylineOptions.width(10);
                polylineOptions.color(Color.RED);
                // ラインを引く
                //mMap.addPolyline(null);
                mMap.addPolyline(polylineOptions);

                //2022.11.30
                for (int i = 0; i < route_distance.size(); i++) {
                    LatLng latLng = new LatLng(route_latitude.get(i), route_longitude.get(i));
                    addCircle(latLng, GEOFENCE_RADIUS);
                    addGeofence(latLng, GEOFENCE_RADIUS);
                    if (i >= 0) {
                        marker = mMap.addMarker(new MarkerOptions().position(latLng).title("announce:" + "straight" + " distance:" + route_distance.get(i)));
                    } else {
                        //marker = mMap.addMarker(new MarkerOptions().position(latLng).title("announce:" + route_maneuver.get(i-1) +" distance:" + route_distance.get(i)));

                    }

                }
            }
        });
    }

    private ArrayList<LatLng> decodePolyline(String encoded) {
        ArrayList<LatLng> point = new ArrayList<>();
        int index = 0;
        int len = encoded.length();
        int lat = 0;
        int lng = 0;

        while (index < len) {
            int b;
            int shift = 0;
            int result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lat += dlat;

            shift = 0;
            result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lng += dlng;

            LatLng p = new LatLng(((double) lat / 1E5), ((double) lng / 1E5));
            point.add(p);
        }

        return point;
    }

    private String getRoute(String string) {
        String data = "";
        HttpURLConnection urlConnection = null;
        InputStream inputStream = null;

        // URLからデータ取得(MainThreadからはできないので注意)
        try {
            URL url = new URL(string);
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.connect();
            inputStream = urlConnection.getInputStream();
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuffer stringBuffer = new StringBuffer();
            String line = "";
            while ((line = bufferedReader.readLine()) != null) {
                stringBuffer.append(line);
            }
            data = stringBuffer.toString();
            Log.d("myLog", "Download URL:" + data);
            bufferedReader.close();
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                inputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            urlConnection.disconnect();
        }
        return data;
    }

    private String getURL(LatLng latLng) {
        //URLの設定
        //myLocationがヌルぽしがち
        String str_origin = "origin=" + lastKnownLocation.getLatitude() + "," + lastKnownLocation.getLongitude();
        String str_dest = "destination=" + latLng.latitude + "," + latLng.longitude;
        String mode = "mode=" + "driving";
        String parameters = str_origin + "&" + str_dest + "&" + mode;
        String output = "json";
        String str_url = "https://maps.googleapis.com/maps/api/directions/"
                + output + "?" + parameters + "&region=ja" + "&key=" + getString(R.string.google_maps_key);
        //debug
        //str_url = "https://maps.googleapis.com/maps/api/directions/json?origin=35.94930742542948,%20139.65396618863628&destination=35.93944021019093,%20139.63276601021752&mode=driving&key=AIzaSyCARFC52yInNoO0ff0NMLFTcvU7B4AtMd8";
        Log.i("getURL()", "str_url = " + str_url);
        return str_url;
    }

    @Override
    public void onInit(int status) {
        if (TextToSpeech.SUCCESS == status) {
            Log.d("onInit", "TextToSpeech initialize");
        } else {
            Log.d("onInit", "TextToSpeech failed");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (null != tts) {
            tts.shutdown();
        }
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
        if(location.hasSpeed()) {
            //速度が出ている時（km/hに変換して変数speedへ）
            speed = location.getSpeed() * 3.6f;
        } else {
            //速度が出ていない時
            speed = 0;
        }
        //速度を表示する
        textView.setText(speed + " km/h");

    }

    @Override
    protected void onPause() {
        super.onPause();
        locationManager.removeUpdates(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        //GPS（位置情報）の取得が許可があるのかをチェック
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            //GPSの使用が許可されていなければパーミッションを要求し、その後再度チェックが行われる
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);

            return;
        }
        //ロケーションマネージャーにGPS（位置情報）のリクエストを開始する
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
    }

    @Override
    public void onProviderEnabled(@NonNull String provider) {
        LocationListener.super.onProviderEnabled(provider);
    }

    @Override
    public void onProviderDisabled(@NonNull String provider) {
        LocationListener.super.onProviderDisabled(provider);
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        LocationListener.super.onStatusChanged(provider, status, extras);
    }
}