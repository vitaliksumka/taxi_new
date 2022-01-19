package com.android.uraall.taxiapp;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import androidx.fragment.app.FragmentActivity;

import com.directions.route.AbstractRouting;
import com.directions.route.Routing;
import com.directions.route.RoutingListener;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.model.ButtCap;
import com.google.android.gms.maps.model.JointType;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;

import android.Manifest;

import android.app.Activity;

import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;

import android.location.Location;

import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;

import android.provider.Settings;
import android.util.Log;
import android.view.View;

import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Toast;

import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.firebase.geofire.GeoQuery;
import com.firebase.geofire.GeoQueryEventListener;

import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;

import com.google.android.material.snackbar.Snackbar;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.reactivex.SingleObserver;
import io.reactivex.android.schedulers.AndroidSchedulers;

import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;

public class PassengerMapsActivity extends FragmentActivity implements OnMapReadyCallback, GoogleApiClient.OnConnectionFailedListener, RoutingListener {


    private GoogleMap mMap;

    private static final int CHECK_SETTINGS_CODE = 111;
    private static final int REQUEST_LOCATION_PERMISSION = 222;


    private FusedLocationProviderClient fusedLocationClient;
    private SettingsClient settingsClient;
    private LocationRequest locationRequest;
    private LocationSettingsRequest locationSettingsRequest;
    private LocationCallback locationCallback;
    private Location currentLocation;

    private boolean isLocationUpdatesActive;

    private Button settingsButton, signOutButton, bookTaxiButton;

    private FirebaseAuth auth;
    private FirebaseUser currentUser;

    private DatabaseReference driversGeoFire;
    private DatabaseReference nearestDriverLocation;
    private int searchRadius = 1;
    private boolean isDriverFound = false;
    private String nearestDriverId;
    private Marker driverMarker;


    private PolylineOptions polylineOptions;
    private AppIntefrace appIntefrace;
    private List<LatLng> polylinelist;
    private LatLng origion, dest;
    private String st1;
    private String st2;
    private String st3;
    private String st4;
    private int cameraIncrement;
    private ImageButton locationButton;

    Polyline polyline;
    //current and destination location objects
    Location myLocation = null;
    Location destinationLocation = null;
    protected LatLng start = null;
    protected LatLng end = null;

    //to get location permissions.
    private final static int LOCATION_REQUEST_CODE = 23;
    boolean locationPermission = false;

    //polyline object
    private List<Polyline> polylines = null;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_passenger_maps);

        Retrofit retrofit = new Retrofit.Builder().addConverterFactory(GsonConverterFactory.create())
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .baseUrl("https://maps.googleapis.com/").build();

        appIntefrace = retrofit.create(AppIntefrace.class);

        requestPermision();


        auth = FirebaseAuth.getInstance();
        currentUser = auth.getCurrentUser();

        settingsButton = findViewById(R.id.settingsButton);
        signOutButton = findViewById(R.id.signOutButton);
        bookTaxiButton = findViewById(R.id.bookTaxiButton);
        locationButton = findViewById(R.id.locationButton);

        driversGeoFire = FirebaseDatabase.getInstance("https://taxiapp-37fd1-default-rtdb.europe-west1.firebasedatabase.app/").getReference()
                .child("driversGeoFires");


        signOutButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                auth.signOut();
                signOutPassenger();
            }
        });

        bookTaxiButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                bookTaxiButton.setText("Getting your taxi...");

                gettingNearestTaxi();

            }
        });

        locationButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                LatLng ltlng = new LatLng(currentLocation.getLatitude(), currentLocation.getLongitude());

                CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngZoom(
                        ltlng, 17f);

                mMap.animateCamera(cameraUpdate);
            }
        });

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        fusedLocationClient = LocationServices
                .getFusedLocationProviderClient(this);
        settingsClient = LocationServices.getSettingsClient(this);

        buildLocationRequest();
        buildLocationCallBack();
        buildLocationSettingsRequest();

        startLocationUpdates();

    }


    private void requestPermision() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                    LOCATION_REQUEST_CODE);
        } else {
            locationPermission = true;
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case LOCATION_REQUEST_CODE: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    //if permission granted.
                    locationPermission = true;
                    getMyLocation();

                } else {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }
                return;
            }
        }
    }


    private void getMyLocation() {
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
        mMap.setMyLocationEnabled(true);
        mMap.setOnMyLocationChangeListener(new GoogleMap.OnMyLocationChangeListener() {
            @Override
            public void onMyLocationChange(Location location) {

                myLocation = location;
                LatLng ltlng = new LatLng(location.getLatitude(), location.getLongitude());
                CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngZoom(
                        ltlng, 16f);

                if (cameraIncrement == 0) {
                    mMap.animateCamera(cameraUpdate);
                }
                cameraIncrement++;
            }


        });

        //get destination location when user click on map
//        mMap.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
//            @Override
//            public void onMapClick(LatLng latLng) {
//
//                end = latLng;
//
//                mMap.clear();
//
//                start = new LatLng(myLocation.getLatitude(), myLocation.getLongitude());
//                //start route finding
//                Findroutes(start, end);
//            }
//        });

    }


    // function to find Routes.
    public void Findroutes(LatLng Start, LatLng End) {
        if (Start == null || End == null) {
            Toast.makeText(PassengerMapsActivity.this, "Unable to get location", Toast.LENGTH_LONG).show();
        } else {

            Routing routing = new Routing.Builder()
                    .travelMode(AbstractRouting.TravelMode.DRIVING)
                    .withListener(this)
                    .alternativeRoutes(true)
                    .waypoints(Start, End)
                    .key("AIzaSyCE5hI39arZzUDQcnA6b-x09sRxUoezhBc")  //also define your api key here.
                    .build();
            routing.execute();
        }
    }

    //Routing call back functions.


    @Override
    public void onRoutingFailure(com.directions.route.RouteException e) {
        View parentLayout = findViewById(android.R.id.content);
        Snackbar snackbar = Snackbar.make(parentLayout, e.toString(), Snackbar.LENGTH_LONG);
        snackbar.show();
        //        Findroutes(start,end);
    }

    @Override
    public void onRoutingStart() {
        Toast.makeText(PassengerMapsActivity.this, "Finding Route...", Toast.LENGTH_LONG).show();
    }

    @Override
    public void onRoutingSuccess(ArrayList<com.directions.route.Route> route, int shortestRouteIndex) {
        CameraUpdate center = CameraUpdateFactory.newLatLng(start);
        CameraUpdate zoom = CameraUpdateFactory.zoomTo(16);
        if (polylines != null) {
            polylines.clear();
        }
        PolylineOptions polyOptions = new PolylineOptions();
        LatLng polylineStartLatLng = null;
        LatLng polylineEndLatLng = null;


        polylines = new ArrayList<>();
        //add route(s) to the map using polyline
        for (int i = 0; i < route.size(); i++) {

            if (i == shortestRouteIndex) {
                polyOptions.color(getResources().getColor(R.color.colorPrimary));
                polyOptions.width(7);
                polyOptions.addAll(route.get(shortestRouteIndex).getPoints());
                Polyline polyline = mMap.addPolyline(polyOptions);
                polylineStartLatLng = polyline.getPoints().get(0);
                int k = polyline.getPoints().size();
                polylineEndLatLng = polyline.getPoints().get(k - 1);
                polylines.add(polyline);

            } else {

            }

        }
        //Add Marker on route starting position
        MarkerOptions startMarker = new MarkerOptions();
        startMarker.position(polylineStartLatLng);
        startMarker.title("My Location");
        mMap.addMarker(startMarker);

        //Add Marker on route ending position
        MarkerOptions endMarker = new MarkerOptions();
        endMarker.position(polylineEndLatLng);
        endMarker.title("Destination");
        mMap.addMarker(endMarker);
    }


    @Override
    public void onRoutingCancelled() {
        Findroutes(start, end);
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Findroutes(start, end);
    }


    private void gettingNearestTaxi() {


        GeoFire geoFire = new GeoFire(driversGeoFire);
        GeoQuery geoQuery = geoFire.queryAtLocation(new GeoLocation(
                currentLocation.getLatitude(),
                currentLocation.getLongitude()
        ), searchRadius);

        geoQuery.removeAllListeners();

        geoQuery.addGeoQueryEventListener(new GeoQueryEventListener() {
            @Override
            public void onKeyEntered(String key, GeoLocation location) {

                if (!isDriverFound) {

                    isDriverFound = true;
                    nearestDriverId = key;

                    getNearestDriverLocation();

                }

            }

            @Override
            public void onKeyExited(String key) {

            }

            @Override
            public void onKeyMoved(String key, GeoLocation location) {

            }

            @Override
            public void onGeoQueryReady() {

                if (!isDriverFound) {

                    searchRadius++;
                    gettingNearestTaxi();

                }

            }

            @Override
            public void onGeoQueryError(DatabaseError error) {

            }
        });

    }

    private void getNearestDriverLocation() {

        bookTaxiButton.setText("Getting your driver location...");

        nearestDriverLocation = FirebaseDatabase.getInstance("https://taxiapp-37fd1-default-rtdb.europe-west1.firebasedatabase.app/").getReference()
                .child("driversGeoFires").child(nearestDriverId).child("l");

        nearestDriverLocation.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {

                if (dataSnapshot.exists()) {

                    List<Object> driverLocationParameters =
                            (List<Object>) dataSnapshot.getValue();

                    double latitude = 0;
                    double longitude = 0;
                    String res1 = "";
                    String res2 = "";

                    if (driverLocationParameters.get(0) != null) {

                        latitude = Double.parseDouble(
                                driverLocationParameters.get(0).toString()
                        );
                        res1 = driverLocationParameters.get(0).toString();

                    }

                    if (driverLocationParameters.get(1) != null) {

                        longitude = Double.parseDouble(
                                driverLocationParameters.get(1).toString()
                        );
                        res2 = driverLocationParameters.get(1).toString();

                    }

                    LatLng driverLatLng = new LatLng(latitude, longitude);

                    if (driverMarker != null) {
                        driverMarker.remove();
                    }

                    Location driverLocation = new Location("");
                    driverLocation.setLatitude(latitude);
                    driverLocation.setLongitude(longitude);

                    float distanceToDriver =
                            driverLocation.distanceTo(currentLocation);

                    float distunceResult = distanceToDriver / 1000;
                    String kmResult = NumberFormat.getNumberInstance(Locale.US).format(distunceResult);


                    bookTaxiButton.setText("Distance to driver: " +
                            kmResult + " km");


                    driverMarker = mMap.addMarker(
                            new MarkerOptions().position(driverLatLng)
                                    .title("Your driver is here")
                    );

//-----------------------------------------------------------------------------------------

                    if (currentLocation != null) {

                        LatLng passengerLocation = new LatLng(currentLocation.getLatitude(),
                                currentLocation.getLongitude());

                        st1 = String.valueOf(currentLocation.getLatitude());
                        st2 = String.valueOf(currentLocation.getLongitude());

                        dest = driverLatLng;
                        origion = passengerLocation;
                        st3 = res1;
                        st4 = res2;

                        getDirection(st1 + "," + st2, st3 + "," + st4);

                    }

//-----------------------------------------------------------------------------------------

                }

            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });

    }

    private void signOutPassenger() {

        String passengerUserId = currentUser.getUid();
        DatabaseReference passengers = FirebaseDatabase.getInstance("https://taxiapp-37fd1-default-rtdb.europe-west1.firebasedatabase.app/")
                .getReference()
                .child("passengers");

        GeoFire geoFire = new GeoFire(passengers);
        geoFire.removeLocation(passengerUserId);

        Intent intent = new Intent(PassengerMapsActivity.this,
                ChooseModeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();

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
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        mMap.getUiSettings().setZoomControlsEnabled(true);

        mMap.setPadding(30, 30, 30, 160);
        mMap.getUiSettings().setMyLocationButtonEnabled(true);
        mMap.getUiSettings().setMapToolbarEnabled(true);
        //mMap.getUiSettings().setCompassEnabled(true);
        mMap.getUiSettings().setZoomControlsEnabled(true);
        mMap.getUiSettings().setRotateGesturesEnabled(false);


        if (currentLocation != null) {

            // Add a marker in Sydney and move the camera
            LatLng driverLocation = new LatLng(currentLocation.getLatitude(),
                    currentLocation.getLongitude());
            mMap.addMarker(new MarkerOptions().position(driverLocation).title("Driver location"));
            mMap.moveCamera(CameraUpdateFactory.newLatLng(driverLocation));
        }

        if (locationPermission) {
            getMyLocation();
        }
    }

    private void stopLocationUpdates() {

        if (!isLocationUpdatesActive) {
            return;
        }

        fusedLocationClient.removeLocationUpdates(locationCallback)
                .addOnCompleteListener(this, new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        isLocationUpdatesActive = false;

                    }
                });

    }

    private void startLocationUpdates() {

        isLocationUpdatesActive = true;


        settingsClient.checkLocationSettings(locationSettingsRequest)
                .addOnSuccessListener(this,
                        new OnSuccessListener<LocationSettingsResponse>() {
                            @Override
                            public void onSuccess(
                                    LocationSettingsResponse locationSettingsResponse) {

                                if (ActivityCompat.checkSelfPermission(
                                        PassengerMapsActivity.this,
                                        Manifest.permission.ACCESS_FINE_LOCATION) !=
                                        PackageManager.PERMISSION_GRANTED &&
                                        ActivityCompat
                                                .checkSelfPermission(
                                                        PassengerMapsActivity.this,
                                                        Manifest.permission
                                                                .ACCESS_COARSE_LOCATION) !=
                                                PackageManager.PERMISSION_GRANTED) {
                                    // TODO: Consider calling
                                    //    ActivityCompat#requestPermissions
                                    // here to request the missing permissions, and then overriding
                                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                    //                                          int[] grantResults)
                                    // to handle the case where the user grants the permission. See the documentation
                                    // for ActivityCompat#requestPermissions for more details.
                                    return;
                                }
                                fusedLocationClient.requestLocationUpdates(
                                        locationRequest,
                                        locationCallback,
                                        Looper.myLooper()
                                );

                                updateLocationUi();

                            }
                        })
                .addOnFailureListener(this, new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {


                        int statusCode = ((ApiException) e).getStatusCode();

                        switch (statusCode) {

                            case LocationSettingsStatusCodes
                                    .RESOLUTION_REQUIRED:
                                try {
                                    ResolvableApiException resolvableApiException =
                                            (ResolvableApiException) e;
                                    resolvableApiException.startResolutionForResult(
                                            PassengerMapsActivity.this,
                                            CHECK_SETTINGS_CODE
                                    );
                                } catch (IntentSender.SendIntentException sie) {
                                    sie.printStackTrace();
                                }
                                break;

                            case LocationSettingsStatusCodes
                                    .SETTINGS_CHANGE_UNAVAILABLE:
                                String message =
                                        "Adjust location settings on your device";
                                Toast.makeText(PassengerMapsActivity.this, message,
                                        Toast.LENGTH_LONG).show();

                                isLocationUpdatesActive = false;


                        }

                        updateLocationUi();

                    }
                });

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {

        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {

            case CHECK_SETTINGS_CODE:

                switch (resultCode) {

                    case Activity.RESULT_OK:
                        Log.d("MainActivity", "User has agreed to change location" +
                                "settings");
                        startLocationUpdates();
                        break;

                    case Activity.RESULT_CANCELED:
                        Log.d("MainActivity", "User has not agreed to change location" +
                                "settings");
                        isLocationUpdatesActive = false;
                        updateLocationUi();
                        break;
                }

                break;
        }

    }

    private void buildLocationSettingsRequest() {

        LocationSettingsRequest.Builder builder =
                new LocationSettingsRequest.Builder();
        builder.addLocationRequest(locationRequest);
        locationSettingsRequest = builder.build();

    }

    private void buildLocationCallBack() {

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                super.onLocationResult(locationResult);

                currentLocation = locationResult.getLastLocation();

                updateLocationUi();

            }
        };

    }

    private void updateLocationUi() {

        if (currentLocation != null) {

            LatLng passengerLocation = new LatLng(currentLocation.getLatitude(),
                    currentLocation.getLongitude());
            //   mMap.moveCamera(CameraUpdateFactory.newLatLng(passengerLocation));
            //    mMap.animateCamera(CameraUpdateFactory.zoomTo(12));
            mMap.addMarker(new MarkerOptions().position(passengerLocation).title("Passenger location"));

            String passengerUserId = currentUser.getUid();
            DatabaseReference passengersGeoFire = FirebaseDatabase.getInstance("https://taxiapp-37fd1-default-rtdb.europe-west1.firebasedatabase.app/").getReference()
                    .child("passengersGeoFire");
            DatabaseReference passengers = FirebaseDatabase.getInstance("https://taxiapp-37fd1-default-rtdb.europe-west1.firebasedatabase.app/").getReference()
                    .child("passengers");
            passengers.setValue(true);

            GeoFire geoFire = new GeoFire(passengersGeoFire);
            geoFire.setLocation(passengerUserId, new GeoLocation(currentLocation.getLatitude(),
                    currentLocation.getLongitude()));
        }

    }

    private void buildLocationRequest() {

        locationRequest = new LocationRequest();
        locationRequest.setInterval(10000);
        locationRequest.setFastestInterval(3000);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

    }

    @Override
    protected void onPause() {
        super.onPause();
        stopLocationUpdates();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (isLocationUpdatesActive && checkLocationPermission()) {

            startLocationUpdates();

        } else if (!checkLocationPermission()) {
            requestLocationPermission();
        }
    }

    private void requestLocationPermission() {

        boolean shouldProvideRationale = ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
        );

        if (shouldProvideRationale) {

            showSnackBar(
                    "Location permission is needed for " +
                            "app functionality",
                    "OK",
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            ActivityCompat.requestPermissions(
                                    PassengerMapsActivity.this,
                                    new String[]{
                                            Manifest.permission.ACCESS_FINE_LOCATION
                                    },
                                    REQUEST_LOCATION_PERMISSION
                            );
                        }
                    }

            );

        } else {

            ActivityCompat.requestPermissions(
                    PassengerMapsActivity.this,
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION
                    },
                    REQUEST_LOCATION_PERMISSION
            );

        }

    }

    private void showSnackBar(
            final String mainText,
            final String action,
            View.OnClickListener listener) {

        Snackbar.make(
                findViewById(android.R.id.content),
                mainText,
                Snackbar.LENGTH_INDEFINITE)
                .setAction(
                        action,
                        listener
                )
                .show();

    }


//    @Override
//    public void onRequestPermissionsResult(int requestCode,
//                                           @NonNull String[] permissions,
//                                           @NonNull int[] grantResults) {
//
//        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
//        if (requestCode == REQUEST_LOCATION_PERMISSION) {
//
//            if (grantResults.length <= 0) {
//                Log.d("onRequestPermissions",
//                        "Request was cancelled");
//            } else if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//                if (isLocationUpdatesActive) {
//                    startLocationUpdates();
//                }
//            } else {
//                showSnackBar(
//                        "Turn on location on settings",
//                        "Settings",
//                        new View.OnClickListener() {
//                            @Override
//                            public void onClick(View v) {
//                                Intent intent = new Intent();
//                                intent.setAction(
//                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
//                                Uri uri = Uri.fromParts(
//                                        "package",
//                                        BuildConfig.APPLICATION_ID,
//                                        null
//                                );
//                                intent.setData(uri);
//                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//                                startActivity(intent);
//                            }
//                        }
//                );
//            }
//
//        }
//
//    }

    private boolean checkLocationPermission() {

        int permissionState = ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION);
        return permissionState == PackageManager.PERMISSION_GRANTED;

    }


    private void getDirection(String origin, String destination) {
        appIntefrace.getDirection("driving", "less driving", origin, destination,
                getString(R.string.google_maps_key_api)
        ).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
                .subscribe(new SingleObserver<Result>() {
                    @Override
                    public void onSubscribe(@NonNull Disposable d) {

                    }

                    @Override
                    public void onSuccess(@NonNull Result result) {


                        polylinelist = new ArrayList<>();
                        List<Route> routeList = result.getRoutes();
                        for (Route route : routeList) {
                            String polyline = route.getOverviewPolyline().getPoints();
                            polylinelist.addAll(decodePoly(polyline));
                        }
                        polylineOptions = new PolylineOptions();
                        polylineOptions.color(ContextCompat.getColor(getApplicationContext(),
                                R.color.colorPrimary));
                        polylineOptions.width(10);
                        polylineOptions.startCap(new ButtCap());
                        polylineOptions.jointType(JointType.ROUND);
                        polylineOptions.addAll(polylinelist);
                        mMap.addPolyline(polylineOptions);
                        LatLngBounds.Builder builder = new LatLngBounds.Builder();
                        builder.include(origion);
                        builder.include(dest);
                        //  mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(),
                        //           100));
                    }

                    @Override
                    public void onError(@NonNull Throwable e) {

                    }
                });
    }

    private List<LatLng> decodePoly(String encoded) {

        List<LatLng> poly = new ArrayList<>();
        int index = 0, len = encoded.length();
        int lat = 0, lng = 0;

        while (index < len) {
            int b, shift = 0, result = 0;
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

            LatLng p = new LatLng((((double) lat / 1E5)),
                    (((double) lng / 1E5)));
            poly.add(p);
        }

        return poly;
    }

}
