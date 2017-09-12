package com.tamada.googlemapsapi.activity;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.appindexing.AppIndex;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.places.AutocompleteFilter;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.Places;
import com.google.android.gms.location.places.ui.PlaceAutocomplete;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.tamada.googlemapsapi.R;
import com.tamada.googlemapsapi.app.AppController;
import com.tamada.googlemapsapi.helper.HttpConnection;
import com.tamada.googlemapsapi.helper.PathJSONParser;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class MapDirectionsActivity extends AppCompatActivity implements GoogleApiClient.OnConnectionFailedListener, OnMapReadyCallback, LocationListener, GoogleApiClient.ConnectionCallbacks  {

    private static final String TAG = MapDirectionsActivity.class.getSimpleName();
    private GoogleMap googleMap;
    private GoogleApiClient mGoogleApiClient;
    private LocationRequest mLocationRequest;
    double currentLatitude;
    double currentLongitude;
    double destLatitude,destLongitude;
    private MapView mMapView;
    private static final int REQUEST_CODE_ACCESS_FINE_LOCATION = 102;
    private static final String PERMISSION_ACCESS_FINE_LOCATION = Manifest.permission.ACCESS_FINE_LOCATION;
    private static final int CONNECTION_FAILURE_RESOLUTION_REQUEST = 9000;
    private static final int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;
    private LatLng CURRENT_LATLNG;
    private LatLng DEST_LATLNG;

    private static final int PLACE_AUTOCOMPLETE_REQUEST_CODE_SOURCE = 1;
    private static final int PLACE_AUTOCOMPLETE_REQUEST_CODE_DESTINATION = 2;
    private TextView lblSourceAddress,lblDestAddress;
    private ImageView imgNavigation;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map_directions);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Tracking");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        lblSourceAddress= (TextView) findViewById(R.id.idInputSourceAddress);
        lblDestAddress= (TextView) findViewById(R.id.idInputDestinationAddress);
        mMapView = (MapView) findViewById(R.id.mapView);
        imgNavigation= (ImageView) findViewById(R.id.idNavigation);
        mMapView.onCreate(savedInstanceState);
        mMapView.onResume();
        mMapView.getMapAsync(this);
        accessMap();
        imgNavigation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(currentLatitude!=0.0&&destLatitude!=0.0) {
                    CURRENT_LATLNG = new LatLng(currentLatitude, currentLongitude);
                    DEST_LATLNG = new LatLng(destLatitude, destLongitude);
                    drawMarker(DEST_LATLNG, "dest");
                    drawMarker(CURRENT_LATLNG, "source");
                    String url = getMapsApiDirectionsUrl();
                    ReadTask downloadTask = new ReadTask();
                    downloadTask.execute(url);
                }else{
                    Toast.makeText(AppController.getInstance().getApplicationContext(),"Please set your addresses",Toast.LENGTH_SHORT).show();
                }
            }
        });
        lblSourceAddress.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    AutocompleteFilter typeFilter = new AutocompleteFilter.Builder()
                            .setCountry("IN")
                            .build();
                    Intent intent =
                            new PlaceAutocomplete.IntentBuilder(PlaceAutocomplete.MODE_FULLSCREEN).setBoundsBias(new LatLngBounds(new LatLng(currentLatitude, currentLongitude), new LatLng(currentLatitude, currentLongitude))).setFilter(typeFilter)
                                    .build(MapDirectionsActivity.this);
                    startActivityForResult(intent, PLACE_AUTOCOMPLETE_REQUEST_CODE_SOURCE);
                } catch (GooglePlayServicesRepairableException | GooglePlayServicesNotAvailableException e) {
                    e.printStackTrace();
                }
            }
        });
        lblDestAddress.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    AutocompleteFilter typeFilter = new AutocompleteFilter.Builder()
                            .setCountry("IN")
                            .build();
                    Intent intent =
                            new PlaceAutocomplete.IntentBuilder(PlaceAutocomplete.MODE_FULLSCREEN).setBoundsBias(new LatLngBounds(new LatLng(currentLatitude, currentLongitude), new LatLng(currentLatitude, currentLongitude))).setFilter(typeFilter)
                                    .build(MapDirectionsActivity.this);
                    startActivityForResult(intent, PLACE_AUTOCOMPLETE_REQUEST_CODE_DESTINATION);
                } catch (GooglePlayServicesRepairableException | GooglePlayServicesNotAvailableException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void accessMap() {
        int hasWriteStoragePermission = ContextCompat.checkSelfPermission(AppController.getInstance().getApplicationContext(), PERMISSION_ACCESS_FINE_LOCATION);
        if (hasWriteStoragePermission != PackageManager.PERMISSION_GRANTED) {
            boolean showRequestAgain = ActivityCompat.shouldShowRequestPermissionRationale(this, PERMISSION_ACCESS_FINE_LOCATION);
            // Log.e(TAG, "showRequestAgain: " + showRequestAgain);
            if (showRequestAgain) {
                new AlertDialog.Builder(MapDirectionsActivity.this).setMessage("Map requires access location permission")
                        .setPositiveButton("ALLOW", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                ActivityCompat.requestPermissions(MapDirectionsActivity.this, new String[]{PERMISSION_ACCESS_FINE_LOCATION},
                                        REQUEST_CODE_ACCESS_FINE_LOCATION);
                            }
                        }).setNegativeButton("DENY", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                    }
                }).show();
                return;
            } else {
                ActivityCompat.requestPermissions(MapDirectionsActivity.this, new String[]{PERMISSION_ACCESS_FINE_LOCATION}, REQUEST_CODE_ACCESS_FINE_LOCATION);
                return;
            }
        }
        showMap();
    }

    private void showMap() {
        try {
            initializeMap();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void initializeMap() {
        MapsInitializer.initialize(AppController.getInstance().getApplicationContext());
        // Check if we were successful in obtaining the map.
        if (googleMap != null) {
            setUpMap();
        }
    }

    private void setUpMap() {
        googleMap.addMarker(new MarkerOptions().position(new LatLng(0, 0)).title("Marker"));
    }

    private void createLocationRequest() {
        // Create the LocationRequest object
        int UPDATE_INTERVAL = 10000;
        int FATEST_INTERVAL = 5000;
        int DISPLACEMENT = 50;
        mLocationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(UPDATE_INTERVAL)        // 10 seconds, in milliseconds
                .setFastestInterval(FATEST_INTERVAL).setSmallestDisplacement(DISPLACEMENT); // 1 second, in milliseconds
    }

    private void buildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(AppController.getInstance().getApplicationContext())
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API).addApi(Places.GEO_DATA_API).enableAutoManage(this, 0 /* clientId */, this)
                .addApi(Places.PLACE_DETECTION_API)
                .addApi(AppIndex.API).build();
        createLocationRequest();
    }

    private boolean checkPlayServices() {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        int resultCode = apiAvailability.isGooglePlayServicesAvailable(getApplicationContext());
        if (resultCode != ConnectionResult.SUCCESS) {
            if (apiAvailability.isUserResolvableError(resultCode)) {
                apiAvailability.getErrorDialog(this, resultCode, PLAY_SERVICES_RESOLUTION_REQUEST)
                        .show();
            } else {
                // Log.i(TAG, "This device is not supported. Google Play Services not installed!");
                Toast.makeText(AppController.getInstance().getApplicationContext(), "This device is not supported. Google Play Services not installed!", Toast.LENGTH_LONG).show();
                finish();
            }
            return false;
        }
        return true;
    }


    @Override
    public void onResume() {
        //  Log.d("Case", "onResume");
        super.onResume();
        mMapView.onResume();

    }

    @Override
    public void onPause() {
        // Log.d("Case", "onPause");
        super.onPause();
        mMapView.onPause();
        if (mGoogleApiClient != null) {
            mGoogleApiClient.stopAutoManage(this);
            mGoogleApiClient.disconnect();
        }
    }

    @Override
    public void onDestroy() {
        // Log.d("Case", "onDestroy");
        super.onDestroy();
        if (mGoogleApiClient != null) {
            mGoogleApiClient.stopAutoManage(this);
            mGoogleApiClient.disconnect();
        }
        mMapView.onDestroy();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (mGoogleApiClient != null)
            mGoogleApiClient.connect();
    }

    @Override
    public void onStop() {
        if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
            mGoogleApiClient.disconnect();
        }
        super.onStop();
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            try {
                Location location = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
                if (location == null) {
                    LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
                } else {
                    currentLatitude = location.getLatitude();
                    currentLongitude = location.getLongitude();
                    onLocationChanged(location);
                }
            } catch (SecurityException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onConnectionSuspended(int i) {
        if (mGoogleApiClient != null) {
            mGoogleApiClient.connect();
        }

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        if (connectionResult.hasResolution()) {
            try {
                connectionResult.startResolutionForResult(this, CONNECTION_FAILURE_RESOLUTION_REQUEST);
            } catch (IntentSender.SendIntentException e) {
                e.printStackTrace();
            }
        } else {
            Log.i(TAG, "Location services connection failed with code " + connectionResult.getErrorCode());
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        this.googleMap = googleMap;
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(getApplicationContext(),
                    Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) {
                if (checkPlayServices()) {
                    buildGoogleApiClient();
                }
                this.googleMap.setMyLocationEnabled(true);
            }
        } else {
            if (checkPlayServices()) {
                buildGoogleApiClient();
            }
            this.googleMap.setMyLocationEnabled(true);
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        if (location != null) {
            currentLatitude = location.getLatitude();
            currentLongitude = location.getLongitude();
            LatLng latLng = new LatLng(currentLatitude, currentLongitude);
            if (googleMap != null)
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15.0f));
        } else {
            try {
                Location mLastLocation = LocationServices.FusedLocationApi.getLastLocation(
                        mGoogleApiClient);
                if (mLastLocation != null) {
                    currentLatitude = mLastLocation.getLatitude();
                    currentLongitude = mLastLocation.getLongitude();
                    LatLng latLng = new LatLng(currentLatitude, currentLongitude);
                    if (googleMap != null)
                        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15.0f));
                }
            } catch (SecurityException e) {
                e.printStackTrace();
            }
        }
    }

    private void drawMarker(LatLng latLng, String imageType) {
        MarkerOptions markerOptions = new MarkerOptions();
        markerOptions.position(latLng);
        if (imageType.equals("dest")) {
            markerOptions.icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_dest));
        } else {
            markerOptions.icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_source));
        }
        Marker marker = googleMap.addMarker(markerOptions);
        marker.showInfoWindow();
    }

    private String getMapsApiDirectionsUrl() {
        String str_origin = "origin=" + CURRENT_LATLNG.latitude + "," + CURRENT_LATLNG.longitude;
        // Destination of route
        String str_dest = "destination=" + DEST_LATLNG.latitude + "," + DEST_LATLNG.longitude;
        // Sensor enabled
        String sensor = "sensor=false";
        // Building the parameters to the web service
        String parameters = str_origin + "&" + str_dest + "&" + sensor;
        // Output format
        String output = "json";
        // Building the url to the web service
        return "https://maps.googleapis.com/maps/api/directions/" + output + "?" + parameters;
    }

    private class ReadTask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... url) {
            String data = "";
            try {
                HttpConnection http = new HttpConnection();
                data = http.readUrl(url[0]);
            } catch (Exception e) {
                //Log.d("Background Task", e.toString());
            }
            return data;
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
            new ParserTask().execute(result);
        }
    }

    private class ParserTask extends
            AsyncTask<String, Integer, List<List<HashMap<String, String>>>> {

        @Override
        protected List<List<HashMap<String, String>>> doInBackground(
                String... jsonData) {
            JSONObject jObject;
            List<List<HashMap<String, String>>> routes = null;
            try {
                jObject = new JSONObject(jsonData[0]);
                PathJSONParser parser = new PathJSONParser();
                routes = parser.parse(jObject);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return routes;
        }

        @Override
        protected void onPostExecute(List<List<HashMap<String, String>>> routes) {
            ArrayList<LatLng> points;
            PolylineOptions polyLineOptions = null;
            if (routes != null) {
                // traversing through routes
                for (int i = 0; i < routes.size(); i++) {
                    points = new ArrayList<>();
                    polyLineOptions = new PolylineOptions();
                    List<HashMap<String, String>> path = routes.get(i);
                    for (int j = 0; j < path.size(); j++) {
                        HashMap<String, String> point = path.get(j);
                        double lat = Double.parseDouble(point.get("lat"));
                        double lng = Double.parseDouble(point.get("lng"));
                        LatLng position = new LatLng(lat, lng);
                        points.add(position);
                    }
                    polyLineOptions.addAll(points);
                    polyLineOptions.width(12);
                    polyLineOptions.color(ContextCompat.getColor(AppController.getInstance().getApplicationContext(), R.color.colorSecondary));
                }
                if (googleMap != null)
                    googleMap.addPolyline(polyLineOptions);
            }
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            // Respond to the action bar's Up/Home button
            case android.R.id.home:
                finish();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        //autocompleteFragment.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PLACE_AUTOCOMPLETE_REQUEST_CODE_SOURCE) {
            if (resultCode == RESULT_OK) {
                Place place = PlaceAutocomplete.getPlace(this, data);
                lblSourceAddress.setText(place.getAddress());
                currentLatitude = place.getLatLng().latitude;
                currentLongitude = place.getLatLng().longitude;
                Log.i(TAG, "current lat:" +place.getLatLng().latitude);

                //Log.i(TAG, "Place:" + place.getLatLng().latitude + "," + place.getLatLng().longitude);
            } else if (resultCode == PlaceAutocomplete.RESULT_ERROR) {
                Status status = PlaceAutocomplete.getStatus(this, data);
                Log.i(TAG, status.getStatusMessage());
                currentLatitude = 0.0;
                currentLongitude = 0.0;
            }
        } else if (requestCode == PLACE_AUTOCOMPLETE_REQUEST_CODE_DESTINATION) {
            if (resultCode == RESULT_OK) {
                Place place = PlaceAutocomplete.getPlace(this, data);
                Log.i(TAG, "dest lat:" +place.getLatLng().latitude);
                lblDestAddress.setText(place.getAddress());
                destLatitude = place.getLatLng().latitude;
                destLongitude = place.getLatLng().longitude;
            } else if (resultCode == PlaceAutocomplete.RESULT_ERROR) {
                Status status = PlaceAutocomplete.getStatus(this, data);
                Log.i(TAG, status.getStatusMessage());
                destLatitude = 0.0;
                destLatitude = 0.0;
            }
        }
    }
}
