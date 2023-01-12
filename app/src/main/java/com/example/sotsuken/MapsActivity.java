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
import android.view.View;
import android.widget.CheckBox;
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
import com.google.android.gms.maps.GoogleMapOptions;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.UiSettings;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Queue;

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
 *
 * 2023/1/8
 * 現在地と次の経路までの距離を表す
 */
public class MapsActivity extends FragmentActivity
        implements OnMapReadyCallback, GoogleMap.OnMyLocationButtonClickListener,
        GoogleMap.OnMyLocationClickListener, LocationListener,
        ActivityCompat.OnRequestPermissionsResultCallback, GoogleMap.OnMapLongClickListener, GoogleMap.OnMarkerClickListener, TextToSpeech.OnInitListener {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    private static final int PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 1;
    private static final int BACKGROUND_LOCATION_PERMISSION_REQUEST_CODE = 1;
    private static final int DEFAULT_ZOOM = 15;
    private static final int QUEUE_SIZE = 5;
    private final LatLng defaultLocation = new LatLng(35.6809591, 139.7673068);
    Handler mMainHandler = new Handler(Looper.getMainLooper());
    GeofencingClient geofencingClient;
    private final float GEOFENCE_RADIUS = 50;
    boolean flagIntersection = false;
    boolean flag1000m = false;
    boolean flag500m = false;
    boolean flag300m = false;
    boolean flag100m = false;
    private GoogleMap mMap;
    private Marker marker;
    private boolean locationPermissionGranted;
    private boolean backgroundLocationPermissionGranted;
    private LatLng current;
    private Location myLocation;
    private FusedLocationProviderClient fusedLocationProviderClient;
    private Location lastKnownLocation;
    private TextToSpeech tts;
    private GeofenceHelper geofenceHelper;
    private final String GEOFENCE_ID = "SOME_GEOFENCE_ID";
    private LocationManager locationManager;
    private TextView textView;
    private TextView distance_text;
    private float speed = 0f;
    private String marker_id;
    private UiSettings mUiSettings;
    private GoogleMapOptions googleMapOptions;
    private Queue<Float> floatQueue;
    private Queue<Double> navigate_latitude;
    private Queue<Double> navigate_longitude;
    private Queue<Double> navigate_distance;
    private Queue<String> navigate_maneuver;
    private Double next_lat;
    private Double next_lng;
    private int target_distance = 9999;
    ArrayList<Double> route_latitude = new ArrayList<>();
    ArrayList<Double> route_longitude = new ArrayList<>();
    ArrayList<Double> route_distance = new ArrayList<>();
    ArrayList<String> route_maneuver = new ArrayList<>();

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
        distance_text = (TextView) findViewById(R.id.distance_text);
        //harmonic mean speed
        floatQueue = new ArrayDeque<Float>();
        navigate_maneuver = new ArrayDeque<String>();
        navigate_latitude = new ArrayDeque<Double>();
        navigate_longitude = new ArrayDeque<Double>();
        navigate_distance = new ArrayDeque<Double>();
        for (int i = 0; i < QUEUE_SIZE; i++) {
            floatQueue.add((float) 0);
        }
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
        mUiSettings = mMap.getUiSettings();

        //仮の中心座標
        //アプリ起動時に現在地があればそれでよし
        // Add a marker in Sydney and move the camera
        /*
        LatLng tokyo = new LatLng(35.6809591, 139.7673068);
        marker = mMap.addMarker(new MarkerOptions().position(tokyo).title("Marker in Tokyo"));
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(tokyo, 14.0f));
        */
        mMap.setMyLocationEnabled(true);
        mMap.getUiSettings().setCompassEnabled(true);
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

    public void setCompassEnabled(View v) {
        if (!checkReady()) {
            return;
        }
        // Enables/disables the compass (icon in the top-left for LTR locale or top-right for RTL
        // locale that indicates the orientation of the map).
        mUiSettings.setCompassEnabled(((CheckBox) v).isChecked());
    }

    private boolean checkReady() {
        if (mMap == null) {
            Toast.makeText(this, R.string.map_not_ready, Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    /**
     * Returns whether the checkbox with the given id is checked.
     */
    private boolean isChecked(int id) {
        return ((CheckBox) findViewById(id)).isChecked();
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
            Log.d("getLocationPermission()", "locationPermissionGranted = true");
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                    PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
        }

        if (ContextCompat.checkSelfPermission(this.getApplicationContext(), Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            backgroundLocationPermissionGranted = true;
            Log.d("getLocationPermission()", "backgroundLocationPermissionGranted = true");
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION}, BACKGROUND_LOCATION_PERMISSION_REQUEST_CODE);
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
                                Log.d("getDeviceLocation()", "lastKnownLocation=(" + lastKnownLocation.getLatitude() + "," + lastKnownLocation.getLongitude() + ")");
                                //mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(lastKnownLocation.getLatitude(),lastKnownLocation.getLongitude()), DEFAULT_ZOOM));
                                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude()), DEFAULT_ZOOM));
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
        Log.d("onMapLongClick()", String.valueOf(current));
        if (marker != null) {
            marker.remove();
            mMap.clear();
            route_latitude.clear();
            route_longitude.clear();
            route_distance.clear();
            route_maneuver.clear();
            navigate_distance.clear();
            navigate_longitude.clear();
            navigate_latitude.clear();
            navigate_maneuver.clear();
            //geofencingClient.removeGeofences(geofenceHelper.getPendingIntent());
        }
        marker = mMap.addMarker(new MarkerOptions().position(latLng).title("(" + latLng.latitude + "," + latLng.longitude + ")"));
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 14));
        mMap.setOnMarkerClickListener(this);
        //"current:("+current.latitude +","+current.longitude+ ")\ntarget:(" +
        Toast.makeText(this, latLng.latitude + "," + latLng.longitude + ")", Toast.LENGTH_LONG).show();

        //20221102
        //最終的に冗長
        //handleMapLongClick(latLng);
        Log.d("onMapLongClick()", marker.getId());
        marker_id = marker.getId();

    }

    private void handleMapLongClick(LatLng latLng) {
        //addCircle(latLng, GEOFENCE_RADIUS);
        //addGeofence(latLng, GEOFENCE_RADIUS);

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
        Log.d("onMarkerClick()", marker.getId());
        Log.d("", marker_id);

        if (!marker_id.equals(marker.getId())) return false;

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
                SquareLatlng squareLatlng = drawSquare_1225(routeData);

                String apiUrl_1225;
                apiUrl_1225 = getAPIUrl_1225(squareLatlng.minX, squareLatlng.minY, squareLatlng.maxX, squareLatlng.maxY);
                Log.i("onMarkerClick", "apiUrl = " + apiUrl_1225);
                String feedback = getTrafficData(apiUrl_1225);
                //Log.i("debug", feedback);
                //Toast.makeText(this, "aaa", Toast.LENGTH_LONG).show();

            }
        });

        //11.4
        //仮実装
        String text = "600m先,右折してください";
        //speechText(text);

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

    private class SquareLatlng {
        Double minX, minY, maxX, maxY;

        public SquareLatlng(Double minX, Double minY, Double maxX, Double maxY) {
            this.minX = minX;
            this.minY = minY;
            this.maxX = maxX;
            this.maxY = maxY;
        }
    }

    private SquareLatlng drawSquare_1225(String data) {
        if (data == null) {
            Log.w("drawSquare_1225()", "Can not draw route because of no data!!");
            return null;
        }
        Double minX1 = null, minY1 = null, maxX1 = null, maxY1 = null;

        try {
            JSONObject jsonObject = new JSONObject(data);
            JSONArray jsonArray = jsonObject.getJSONArray("routes");
            //JSONObject bounds = jsonObject.getJSONObject("bounds");
            JSONObject jsonObject1 = ((JSONObject) jsonArray.get(0));
            JSONObject jsonObject2 = jsonObject1.getJSONObject("bounds");
            JSONObject northeast = jsonObject2.getJSONObject("northeast");
            JSONObject southwest = jsonObject2.getJSONObject("southwest");
            maxX1 = northeast.getDouble("lat");
            maxY1 = northeast.getDouble("lng");
            minX1 = southwest.getDouble("lat");
            minY1 = southwest.getDouble("lng");
            Log.i("drawSquare_1225()", String.valueOf(northeast));
            Log.i("drawSquare_1225()", String.valueOf(southwest));
            /*
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONArray legsArray = ((JSONObject) jsonArray.get(i)).getJSONArray("bounds");
                //boundsArray = ((JSONObject) jsonArray.get(i)).getJSONArray("bounds");

            }
             */
            //Log.i("drawSquare_1225()", String.valueOf(jsonArray));
        } catch (JSONException e) {
            Log.e("drawSquare_1225()", String.valueOf(e));
            e.printStackTrace();
        }

        Double finalMinX = minX1;
        Double finalMinY = minY1;
        Double finalMaxX = maxX1;
        Double finalMaxY = maxY1;
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {

                PolylineOptions polylineOptions = null;
                polylineOptions = new PolylineOptions();
                Double minX = null, maxX = null, minY = null, maxY = null;

                //
                //Log.i("route_latitude =", String.valueOf(route_latitude));
                //Log.i("route_longitude =", String.valueOf(route_longitude));

                //2点間の長方形を作成
                // ラインオプション設定
                polylineOptions.width(10);
                polylineOptions.color(Color.BLACK);
                // ラインを引く
                //mMap.addPolyline(null);

                //minX = getListOfMin(route_latitude, lastKnownLocation.getLatitude());
                //maxX = getListOfMax(route_latitude, lastKnownLocation.getLatitude());
                //minY = getListOfMin(route_longitude, lastKnownLocation.getLongitude());
                //maxY = getListOfMax(route_longitude, lastKnownLocation.getLongi
                //
                // tude());
                minX = finalMinX;
                minY = finalMinY;
                maxX = finalMaxX;
                maxY = finalMaxY;
                polylineOptions.add(new LatLng(minX, minY))
                        .add(new LatLng(maxX, minY))
                        .add(new LatLng(maxX, maxY))
                        .add(new LatLng(minX, maxY))
                        .add(new LatLng(minX, minY));

                mMap.addPolyline(polylineOptions);
            }
        });
        SquareLatlng squareLatlng = new SquareLatlng(minX1, minY1, maxX1, maxY1);
        return squareLatlng;
    }

    private void drawSquare(String data) {
        if (data == null) {
            Log.w("SampleMap", "Can not draw route because of no data!!");
            return;
        }

        JSONArray jsonArray = new JSONArray();
        JSONArray legsArray = new JSONArray();
        JSONArray stepArray = new JSONArray();
        JSONArray boundsArray = new JSONArray();
        //ArrayList<ArrayList<LatLng>> list = new ArrayList<>();
        ArrayList<Double> route_latitude = new ArrayList<>();
        ArrayList<Double> route_longitude = new ArrayList<>();


        try {
            JSONObject jsonObject = new JSONObject(data);
            jsonArray = jsonObject.getJSONArray("routes");
            for (int i = 0; i < jsonArray.length(); i++) {
                legsArray = ((JSONObject) jsonArray.get(i)).getJSONArray("legs");
                boundsArray = ((JSONObject) jsonArray.get(i)).getJSONArray("bounds");
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

    /**
     * 指定領域内の過去の交通事故を地図上に表示する
     * 赤マーカー:経路上の交差点
     * 緑マーカー:赤マーカーの半径40m以内の事故データ
     * 黄マーカー:指定領域内の無視するデータ
     * @param url xfreeサーバに自作したapiのURL
     * @return 特に意味なし
     */
    private String getTrafficData(String url) {
        final String[] comment = {null};
        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder().url(url).build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e("getTrafficData()", e.getMessage());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                final String jsonStr = response.body().string();
                //JSONObject jsonObject = new JSONObject(jsonStr);
                //JSONArray alpha = response.body().
                String status = null, message = null;
                //Double latitude[]=null, longitude[]=null;
                ArrayList<Double> latitude_list = new ArrayList<Double>();
                ArrayList<Double> longitude_list = new ArrayList<Double>();
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
                        latitude_list.add(jsonObject.getDouble("latitude"));
                        longitude_list.add(jsonObject.getDouble("longitude"));
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
                            Double world_lat, world_lng, japan_lat, japan_lng;

                            for (int i = 0; i < latitude_list.size(); i++) {
                                world_lat = latitude_list.get(i);
                                world_lng = longitude_list.get(i);
                                //japan_lat = world_lat + (world_lat*0.00010696) - (world_lng*0.000017467) - 0.0046020;
                                //japan_lng = world_lng + (world_lat*0.000046047) + (world_lng*0.000083049) - 0.010041;
                                //LatLng traffic = new LatLng(japan_lat, japan_lng);
                                LatLng traffic = new LatLng(world_lat, world_lng);
                                boolean setMarker = false;
                                for (int j = 0; j < route_latitude.size(); j++) {
                                    //float[] next_distance = getDistance(location.getLatitude(), location.getLongitude(), next_lat, next_lng);
                                    float[] check_accident_in_intersections = getDistance(route_latitude.get(j), route_longitude.get(j), world_lat, world_lng);
                                    if (check_accident_in_intersections[0] < 50) {
                                        marker = mMap.addMarker(new MarkerOptions()
                                                .position(traffic)
                                                .title("(" + world_lat + "," + world_lng + ")")
                                                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))

                                        );
                                        assert marker != null;
                                        Log.d("getTrafficData()", marker.getId());
                                        setMarker = true;
                                        break;
                                    }
                                }
                                /*
                                if (!setMarker) {
                                    marker = mMap.addMarker(new MarkerOptions()
                                            .position(traffic)
                                            .title("(" + world_lat + "," + world_lng + ")")
                                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW))

                                    );
                                    assert marker != null;
                                    Log.d("getTrafficData()", marker.getId());
                                }
                                */


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

    private String getAPIUrl_1225(Double minX, Double minY, Double maxX, Double maxY) {

        String str_url = "http://al18011.php.xdomain.jp/webapi2.php?";
        String parameter = "minX=" + minX + "&minY=" + minY + "&maxX=" + maxX + "&maxY=" + maxY;
        str_url = str_url + parameter;
        return str_url;
        //return null;
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

                        navigate_latitude.add(stepObject.getJSONObject("end_location").getDouble("lat"));
                        navigate_longitude.add(stepObject.getJSONObject("end_location").getDouble("lng"));
                        navigate_distance.add(stepObject.getJSONObject("distance").getDouble("value"));

                        String maneuver = null, distance_txt = null, duration_txt = null;
                        if (stepIndex > 0) {
                            maneuver = ((JSONObject) stepArray.get(stepIndex)).getString("maneuver");
                            route_maneuver.add(stepObject.getString("maneuver"));
                            navigate_maneuver.add(stepObject.getString("maneuver"));
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
                            navigate_maneuver.add("go straight");
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
            route_maneuver.add("go straight");
            navigate_maneuver.add("go straight");

        }
        Log.d("drawRoute()", "lat=" + route_latitude);
        Log.d("drawRoute()", "lon=" + route_longitude);
        Log.d("distance", String.valueOf(route_distance));
        Log.d("maneuver", String.valueOf(route_maneuver));
        /*
        if (Objects.equals(route_maneuver.get(0), "go straight")) {
            next_lat = route_latitude.get(1);
            next_lng = route_longitude.get(1);
            navigate_maneuver.remove();
            navigate_distance.remove();
            navigate_longitude.remove();
            navigate_latitude.remove();
        } else {
            next_lat = route_latitude.get(0);
            next_lng = route_longitude.get(0);
        }
         */

        next_lat = route_latitude.get(0);
        next_lng = route_longitude.get(0);

        Log.d("drawRoute()", "next location:" + next_lat + "," + next_lng);
        JSONArray finalHtmlArray = htmlArray;
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                String dis = String.valueOf(shapeDistance(route_distance.get(0)));
                String navigate = route_maneuver.get(0);
                switch (navigate) {
                    case "go straight":
                        navigate = "直進";
                        break;
                    case "turn-right":
                        navigate = "右方向";
                        break;
                    case "turn-left":
                        navigate = "左方向";
                        break;
                }

                speechText("およそ"+ dis + "メートル先" + navigate + "してください");

            }
        });
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
                    //addGeofence(latLng, GEOFENCE_RADIUS);
                    if (i >= 0) {
                        marker = mMap.addMarker(new MarkerOptions()
                                .position(latLng)
                                .title("announce:" + route_maneuver.get(i) + " distance:" + route_distance.get(i))
                                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));


                    } else {
                        //marker = mMap.addMarker(new MarkerOptions().position(latLng).title("announce:" + route_maneuver.get(i-1) +" distance:" + route_distance.get(i)));

                    }
                    assert marker != null;
                    Log.d("drawRoute()", marker.getId());
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
                + output + "?" + parameters + "&region=ja"  + "&avoid=tolls|highways|ferries"+ "&key=" + getString(R.string.google_maps_key);
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


    //毎秒の更新処理

    /**
     * ナビゲーションシステムの根幹
     * 1:スマホで計測した速度(km/h)
     * 2:スマホで計測した次の交差点までの距離(m)
     * 3-1:経路音声案内(交差点内で一回)
     * 3-2:曲がった直後に次の交差点までの距離 + 注意喚起
     * 3-3:経路案内(1000m)
     * 3-4:経路案内(500m)
     * 3-5:経路案内(300m) + 注意喚起
     * 3-6:経路案内(100m) + 注意喚起
     */
    @Override
    public void onLocationChanged(@NonNull Location location) {
        //1
        if (location.hasSpeed()) {
            //速度が出ている時（km/hに変換して変数speedへ）
            speed = location.getSpeed() * 3.6f;
        } else {
            //速度が出ていない時
            speed = 0;
        }

        floatQueue.remove();
        floatQueue.add(speed);
        float reverse_speed = 0;
        float harmonic_mean_speed = 0;
        for (Float n : floatQueue) {
            if (n >= 1) {
                reverse_speed += (1 / n);
            } else {
                reverse_speed += 1;
            }
        }
        harmonic_mean_speed = QUEUE_SIZE / reverse_speed;
        //速度を表示する
        if (harmonic_mean_speed <= 2) {
            harmonic_mean_speed = 0;
        }
        textView.setText(harmonic_mean_speed + " km/h");

        //2
        float[] next_distance = getDistance(location.getLatitude(), location.getLongitude(), next_lat, next_lng);
        distance_text.setText(next_distance[0] + "m");

        //3-1
        if (next_distance[0] <= 15 && !flagIntersection && !navigate_maneuver.isEmpty() && !tts.isSpeaking()) {
            String navigate;
            //navigate = route_maneuver.get(1);
            navigate = navigate_maneuver.poll();
            navigate_latitude.remove();
            navigate_longitude.remove();
            navigate_distance.remove();
            next_lat = navigate_latitude.element();
            next_lng = navigate_longitude.element();
            switch (navigate) {
                case "go straight":
                    navigate = "直進";
                    break;
                case "turn-right":
                    navigate = "右方向";
                    break;
                case "turn-left":
                    navigate = "左方向";
                    break;
            }

            speechText(navigate+"です。");
            flagIntersection = true;
        }
        //3-2 next direction
        if (next_distance[0] > 20 && flagIntersection && !tts.isSpeaking()) {

            flagIntersection = false;
            flag1000m = false;
            flag500m = false;
            flag300m = false;
            flag100m = false;

            String mane, dis;
            mane = navigate_maneuver.element();
            navigateTemplate(mane, shapeDistance(navigate_distance.element()));
        }
        try {
            //3-3 reach 1000m
            if (next_distance[0] < 1000 && !flag1000m && navigate_distance.element()>=1000 && !tts.isSpeaking()) {
                flag1000m = true;
                navigateTemplate(navigate_maneuver.element(), 1000);
            }
            //3-4 reach 500m
            if (next_distance[0] < 500 && !flag500m && navigate_distance.element()>=500 && !tts.isSpeaking()) {
                flag500m = true;
                navigateTemplate(navigate_maneuver.element(), 500);
            }
            //3-5 reach 300m
            if (next_distance[0] < 300 && !flag300m && navigate_distance.element()>=300 && !tts.isSpeaking()) {
                flag300m = true;
                navigateTemplate(navigate_maneuver.element(), 300);
            }

            if (next_distance[0] < 100 && !flag100m && navigate_distance.element()>=100 && !tts.isSpeaking()) {
                flag100m = true;
                navigateTemplate(navigate_maneuver.element(), 100);
            }

        } catch (java.util.NoSuchElementException e) {
            //e.printStackTrace();
        }

    }

    private void navigateTemplate(String element, int i) {
        switch (element) {
            case "go straight":
                element = "直進";
                break;
            case "turn-right":
                element = "右方向";
                break;
            case "turn-left":
                element = "左方向";
                break;
        }
        speechText(i + "メートル先" + element + "です");
    }

    private int shapeDistance(Double element) {
        int thousands = (int) (element /1000);
        int hundreds = (int) (element/100);
        int tens = (int) (element/10);

        if(thousands>=1){
            return thousands*1000;
        } else if (hundreds>=2) {
            return hundreds*100;
        } else if (hundreds>=1) {
            return (hundreds*100+tens*10);
        } else {
            return tens*10;
        }
    }

    private float[] getDistance(double latitude, double longitude, Double next_lat, Double next_lng) {
        float[] results = new float[3];
        if (next_lat == null || next_lng == null) {
            return results;
        }
        Location.distanceBetween(latitude, longitude, next_lat, next_lng, results);
        return results;
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