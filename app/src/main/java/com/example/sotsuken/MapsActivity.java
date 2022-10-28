package com.example.sotsuken;

import static androidx.constraintlayout.helper.widget.MotionEffect.TAG;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.loader.content.AsyncTaskLoader;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.example.sotsuken.databinding.ActivityMapsBinding;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.net.PlacesClient;

import org.jetbrains.annotations.NotNull;
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


/***
 * 実装手順
 * テンプレートにGCPのAPIキーを入力
 * カメラ設定
 * 起動時に現在地を表示
 * 長押しすることでマーカー出現or Androidアプリ開発の教科書14章参考
 * その後にdirectionAPI起動
 * 経路描写
 * xfreeサーバにAPI叩く⇔イマココ
 */
public class MapsActivity extends FragmentActivity
        implements OnMapReadyCallback, GoogleMap.OnMyLocationButtonClickListener,
        GoogleMap.OnMyLocationClickListener,
        ActivityCompat.OnRequestPermissionsResultCallback, GoogleMap.OnMapLongClickListener, GoogleMap.OnMarkerClickListener {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    private static final int PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 1;
    private static final int DEFAULT_ZOOM = 15;
    private final LatLng defaultLocation = new LatLng(35.6809591, 139.7673068);
    Handler mMainHandler = new Handler(Looper.getMainLooper());
    private GoogleMap mMap;
    private Marker marker;
    private boolean locationPermissionGranted;
    private LatLng current;
    private Location myLocation;
    private FusedLocationProviderClient fusedLocationProviderClient;
    private Location lastKnownLocation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //com.example.sotsuken.databinding.ActivityMapsBinding binding = ActivityMapsBinding.inflate(getLayoutInflater());
        setContentView(R.layout.activity_maps);

        Places.initialize(getApplicationContext(), getString(R.string.google_maps_key));
        PlacesClient placesClient = Places.createClient(this);
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);


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
                                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                                        new LatLng(lastKnownLocation.getLatitude(),
                                                lastKnownLocation.getLongitude()), DEFAULT_ZOOM));
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
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(current, 20.0f));

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
        }
        marker = mMap.addMarker(new MarkerOptions().position(latLng).title("(" + latLng.latitude + "," + latLng.longitude + ")"));
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 14));
        mMap.setOnMarkerClickListener(this);
        //"current:("+current.latitude +","+current.longitude+ ")\ntarget:(" +
        Toast.makeText(this, latLng.latitude + "," + latLng.longitude + ")", Toast.LENGTH_LONG).show();

    }

    @Override
    public boolean onMarkerClick(@NonNull Marker marker) {
        //移動ルート取得
        LatLng latLng = marker.getPosition();
        Log.d("debug", String.valueOf(marker));
        Log.d("debug", String.valueOf(latLng));
        if(latLng == null) return false;

        //URL get
        String url = getURL(latLng);
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                String routeData = getRoute(url);
                drawRoute(routeData);
            }
        });

        return false;
    }

    private void drawRoute(String data) {
        if (data == null) {
            Log.w("SampleMap", "Can not draw route because of no data!!");
            return;
        }

        JSONArray jsonArray = new JSONArray();
        JSONArray legsArray = new JSONArray();
        JSONArray stepArray = new JSONArray();
        ArrayList<ArrayList<LatLng>> list = new ArrayList<>();

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
                    list.add(decodePolyline(stepObject.getJSONObject("polyline").get("points").toString()));
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
                for (int i = 0; i < list.size(); i++) {
                    // 経路を追加
                    polylineOptions.addAll(list.get(i));
                }
                // ラインオプション設定
                polylineOptions.width(10);
                polylineOptions.color(Color.RED);
                // ラインを引く
                mMap.addPolyline(polylineOptions);
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
            Log.d("myLog", "Download URL:" + data.toString());
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
                + output + "?" + parameters + "&key=" + getString(R.string.google_maps_key);
        return str_url;
    }
}