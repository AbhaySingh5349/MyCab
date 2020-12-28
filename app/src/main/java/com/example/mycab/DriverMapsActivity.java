package com.example.mycab;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.directions.route.AbstractRouting;
import com.directions.route.Route;
import com.directions.route.RouteException;
import com.directions.route.Routing;
import com.directions.route.RoutingListener;
import com.example.mycab.firebasetree.Constants;
import com.example.mycab.firebasetree.NodeNames;
import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import butterknife.BindView;
import de.hdodenhof.circleimageview.CircleImageView;

public class DriverMapsActivity extends FragmentActivity implements OnMapReadyCallback, RoutingListener {

    private GoogleMap mMap;

    private CircleImageView profileImageView;
    private TextView riderDistance;

    LocationRequest locationRequest; // to get the location of the device at regular intervals
    LatLng lastKnownLatLng, riderLatLng, destinationLatLng, currentLatLng; // Represents a geographical location with a latitude and a longitude.
    Location lastKnownLocation; // A data class representing a geographic location  consisting of a latitude, longitude, timestamp, and other information such as bearing, altitude and velocity
    FusedLocationProviderClient fusedLocationProviderClient; // location APIs in Google Play services that intelligently combines different signals to provide the location information

    int rideStatus = 0;

    DatabaseReference availableDriverDatabaseReference, workingDriverDatabaseReference, databaseReference, driverDatabaseReference, riderRequestsDatabaseReference, drivingHistoryDatabaseReference, ridingHistoryDatabaseReference;
    ValueEventListener riderRequestsListener;

    HashMap<String,Object> driverInfoHashMap;
    String currentUserId, nodeReference, riderId="";

    LocationCallback locationCallback; // Used for receiving notifications from the FusedLocationProviderApi when the device location has changed

    View mapView, myLocationBtn;
    RelativeLayout.LayoutParams layoutParams;

    Marker riderMarker;

    int accessFineLocationRequestCode = 101;

    private List<Polyline> polylines;
    private static final int[] COLORS = new int[]{R.color.primary_dark_material_light};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_driver_maps);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.driversMap);
        Objects.requireNonNull(mapFragment).getMapAsync(this);

        polylines = new ArrayList<>();

        mapView = mapFragment.getView(); // for adjusting my location button
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(DriverMapsActivity.this); // get last known location of device
        locationRequest = LocationRequest.create();
        locationRequest.setInterval(4000);
        locationRequest.setFastestInterval(2000);
        locationRequest.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);
    }

    //  method is called when the map is ready to be used

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        // adjusting MyLocation button

        if (mapView != null && mapView.findViewById(Integer.parseInt("1")) != null) {
            myLocationBtn = ((View) mapView.findViewById(Integer.parseInt("1")).getParent()).findViewById(Integer.parseInt("2"));
            layoutParams = (RelativeLayout.LayoutParams) myLocationBtn.getLayoutParams();  // fetching layout params of Location Button
            layoutParams.addRule(RelativeLayout.ALIGN_PARENT_TOP, 0); // removing location button from top right corner
            layoutParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE);  // adding location button to bottom right corner
            layoutParams.setMargins(0, 0, 40, 180);
        }

        // checking permission to Access Location of device

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationUpdates();
        } else {
            askLocationPermission();
        }

        riderDistance = findViewById(R.id.riderDistance);
        profileImageView = findViewById(R.id.profileImageView);

        currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid(); // getting current user

        StorageReference profileImage = FirebaseStorage.getInstance().getReference().child(Constants.IMAGESFOLDER).child(currentUserId);
        profileImage.getDownloadUrl().addOnSuccessListener(new OnSuccessListener<Uri>() {
            @Override
            public void onSuccess(Uri uri) {
                Glide.with(DriverMapsActivity.this).load(uri).placeholder(R.drawable.profile).into(profileImageView); // loading user profile image
            }
        });

        profileImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(DriverMapsActivity.this,EditProfileActivity.class));
            }
        });

        // nodes reference of database

        databaseReference = FirebaseDatabase.getInstance().getReference();
        driverDatabaseReference = databaseReference.child(NodeNames.DRIVERS);
        availableDriverDatabaseReference = databaseReference.child(NodeNames.AVAILABLEDRIVERS);
        workingDriverDatabaseReference = databaseReference.child(NodeNames.WORKINGDRIVERS);
        riderRequestsDatabaseReference = databaseReference.child(NodeNames.RIDERREQUESTS);
        drivingHistoryDatabaseReference = databaseReference.child(NodeNames.DRIVINGHISTORY);
        ridingHistoryDatabaseReference = databaseReference.child(NodeNames.RIDINGHISTORY);

        driverInfoHashMap = new HashMap<>();
        driverInfoHashMap.put(NodeNames.DRIVERID,currentUserId);
        nodeReference = NodeNames.DRIVERS + "/" + currentUserId;
        HashMap<String,Object> nodeHashMap = new HashMap<>();
        nodeHashMap.put(nodeReference,driverInfoHashMap);
        databaseReference.updateChildren(nodeHashMap, new DatabaseReference.CompletionListener() {
            @Override
            public void onComplete(@Nullable DatabaseError error, @NonNull DatabaseReference ref) {
                if(error!=null){
                    Toast.makeText(DriverMapsActivity.this,"error: " + error.getMessage(),Toast.LENGTH_LONG).show();
                }
            }
        });

        getRider();

        // Used for receiving notifications from the FusedLocationProviderApi when the device location has changed

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if(locationResult==null){
                    return;
                }else {
                    // GeoFire uses the Firebase Realtime Database for data storage to read and write geo location data to your Firebase database and to create queries
                    GeoFire geoFireAvailable = new GeoFire(availableDriverDatabaseReference);
                    GeoFire geoFireWorking = new GeoFire(workingDriverDatabaseReference);
                    for (Location location : locationResult.getLocations()){
                        Log.d("Location Updated","Current location: " + location.toString());

                        if(riderId.equals("")){
                            riderDistance.setText("No Rider Requests");
                            if(riderMarker!=null){
                                riderMarker.remove();
                            }
                            geoFireWorking.removeLocation(currentUserId); // removing location updates from Working Driver
                            geoFireAvailable.setLocation(currentUserId,new GeoLocation(location.getLatitude(),location.getLongitude())); // removing location updates from Available Driver

                            currentLatLng = new LatLng(location.getLatitude(),location.getLongitude()); // retrieving current Latitude & Longitude
                            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 18)); // focusing on current location
                        }else {
                            geoFireAvailable.removeLocation(currentUserId); // removing location updates from Available Driver
                            geoFireWorking.setLocation(currentUserId,new GeoLocation(location.getLatitude(),location.getLongitude())); // removing location updates from Working Driver

                            currentLatLng = new LatLng(location.getLatitude(),location.getLongitude()); // retrieving current Latitude & Longitude
                            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 18)); // focusing on current location

                            // Location class represents a geographic location consisting of a latitude, longitude, timestamp, and other information such as bearing, altitude and velocity.

                            Location pickUpLocation = new Location("");
                            pickUpLocation.setLatitude(riderLatLng.latitude);
                            pickUpLocation.setLongitude(riderLatLng.longitude);

                            Location driverLocation = new Location("");
                            driverLocation.setLatitude(location.getLatitude());
                            driverLocation.setLongitude(location.getLongitude());

                            float distance = driverLocation.distanceTo(pickUpLocation); // distance between driver and rider

                            riderDistance.setText("Rider is:" + String.valueOf(distance) + " m away");
                        }
                    }
                }
            }
        };
    }

    // requesting Real Time location updates.

    private void locationUpdates() {
        LocationSettingsRequest locationSettingsRequest = new LocationSettingsRequest.Builder().addLocationRequest(locationRequest).build();
        SettingsClient settingsClient = LocationServices.getSettingsClient(this);

        Task<LocationSettingsResponse> locationSettingsResponseTask = settingsClient.checkLocationSettings(locationSettingsRequest);
        locationSettingsResponseTask.addOnSuccessListener(new OnSuccessListener<LocationSettingsResponse>() {
            @Override
            public void onSuccess(LocationSettingsResponse locationSettingsResponse) {
                startLocationUpdates();
            }
        });
    }

    private void startLocationUpdates() {

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        fusedLocationProviderClient.requestLocationUpdates(locationRequest,locationCallback, Looper.getMainLooper());
        getLastKnownLocation();
    }

    private void askLocationPermission() {
        // checking permission to Access Location of device
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){
            if(ActivityCompat.shouldShowRequestPermissionRationale(this,Manifest.permission.ACCESS_FINE_LOCATION)){
                ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.ACCESS_FINE_LOCATION},accessFineLocationRequestCode);
            }else {
                ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.ACCESS_FINE_LOCATION},accessFineLocationRequestCode);
            }
        }
    }

    private void getLastKnownLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        Task<Location> task = fusedLocationProviderClient.getLastLocation();
        task.addOnSuccessListener(new OnSuccessListener<Location>() {
            @Override
            public void onSuccess(Location location) {
                if (location != null) {
                    lastKnownLocation = location;
                    lastKnownLatLng = new LatLng(lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude());
                //    mMap.addMarker(new MarkerOptions().position(lastKnownLatLng).title("Your Location"));
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(lastKnownLatLng, 18));
                    if (ActivityCompat.checkSelfPermission(DriverMapsActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(DriverMapsActivity.this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        return;
                    }
                    mMap.setMyLocationEnabled(true);
                    mMap.getUiSettings().setMyLocationButtonEnabled(true);
                }
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == accessFineLocationRequestCode) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    getLastKnownLocation();
                }
            }
        }
    }

    private void getRider() {
        driverDatabaseReference.child(currentUserId).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if(snapshot.exists()){
                    if(snapshot.hasChild(NodeNames.RIDERID)){
                        riderId = snapshot.child(NodeNames.RIDERID).getValue().toString();
                        rideStatus = 1; // when driver is on the way to pick rider
                        getAssignedRiderLocation();
                    }else {
                    /*    riderId = "";
                        if(riderMarker!=null){
                            riderMarker.remove();
                        }
                        riderDistance.setText("No Rider Requests");
                        if(riderRequestsListener != null){
                            riderRequestsDatabaseReference.removeEventListener(riderRequestsListener);
                        }
                        removePolyLines(); */
                        rideEndedByDriver(); // indicates driver is available
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
    }

    private void getAssignedRiderLocation() {
        riderRequestsDatabaseReference = databaseReference.child(NodeNames.RIDERREQUESTS).child(riderId).child("l");
        riderRequestsListener = riderRequestsDatabaseReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if(snapshot.exists() && !riderId.equals("")){
                    List<Object> latLng = (List<Object>) snapshot.getValue();
                    double lat = 0, lng = 0;

                    if(latLng.get(0)!=null){
                        lat = Double.parseDouble(latLng.get(0).toString()); // retrieving Latitude from GeoFire
                    }
                    if(latLng.get(1)!=null){
                        lng = Double.parseDouble(latLng.get(1).toString()); // retrieving Longitude from GeoFire
                    }

                    riderLatLng = new LatLng(lat,lng);
                    riderMarker = mMap.addMarker(new MarkerOptions().position(riderLatLng).title("Pick Up Location")); // adding marker at rider location

                    getRoute(riderLatLng);

                    riderDistance.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            View infoView = LayoutInflater.from(DriverMapsActivity.this).inflate(R.layout.rider_info_dialog,null);

                            CircleImageView riderImage = infoView.findViewById(R.id.riderProfileImageView);
                            TextView profileNameTextView = infoView.findViewById(R.id.profileNameTextView);
                            TextView destinationTextView = infoView.findViewById(R.id.destinationTextView);
                            TextView rideStatusTextView = infoView.findViewById(R.id.rideStatusTextView);

                            StorageReference profileImage = FirebaseStorage.getInstance().getReference().child(Constants.IMAGESFOLDER + "/" + riderId);
                            profileImage.getDownloadUrl().addOnSuccessListener(new OnSuccessListener<Uri>() {
                                @Override
                                public void onSuccess(Uri uri) {
                                    Glide.with(DriverMapsActivity.this).load(uri).placeholder(R.drawable.profile).into(riderImage);
                                }
                            });

                            databaseReference.child(NodeNames.USERS).child(riderId).addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(@NonNull DataSnapshot snapshot) {
                                    if(snapshot.hasChild(NodeNames.PROFILENAME)){
                                        profileNameTextView.setText(Objects.requireNonNull(snapshot.child(NodeNames.PROFILENAME).getValue()).toString());
                                    }
                                }

                                @Override
                                public void onCancelled(@NonNull DatabaseError error) {

                                }
                            });

                            driverDatabaseReference.child(currentUserId).addValueEventListener(new ValueEventListener() {
                                @Override
                                public void onDataChange(@NonNull DataSnapshot snapshot) {
                                    if(snapshot.exists()){
                                        if(snapshot.hasChild(NodeNames.RIDERDESTINATION)){
                                            String destination = snapshot.child(NodeNames.RIDERDESTINATION).getValue().toString();
                                            destinationTextView.setText("Destination: " + destination);
                                        }else {
                                            destinationTextView.setText("Destination: --");
                                        }

                                        Double destinationLat = 0.0;
                                        Double destinationLng = 0.0;

                                        if(snapshot.hasChild(NodeNames.DESTINATIONLATITUDE) && snapshot.hasChild(NodeNames.DESTINATIONLONGITUDE)){
                                            destinationLat = Double.parseDouble(snapshot.child(NodeNames.DESTINATIONLATITUDE).getValue().toString());
                                            destinationLng = Double.parseDouble(snapshot.child(NodeNames.DESTINATIONLONGITUDE).getValue().toString());
                                        }

                                        destinationLatLng = new LatLng(destinationLat,destinationLng);
                                    }
                                }

                                @Override
                                public void onCancelled(@NonNull DatabaseError error) {

                                }
                            });

                            AlertDialog alertDialog = new AlertDialog.Builder(DriverMapsActivity.this).setView(infoView).create();
                            alertDialog.show();

                            rideStatusTextView.setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View view) {
                                    switch (rideStatus){
                                        case 1:
                                            // when driver is on the way to pick rider and when driver clicks, means it has reached rider pickup location
                                            rideStatus = 2;
                                            removePolyLines();
                                            if(destinationLatLng.latitude!=0.0 && destinationLatLng.longitude!=0.0){
                                                getRoute(destinationLatLng);
                                            }
                                            rideStatusTextView.setText("Reached Destination?");
                                            break;

                                        case 2:
                                            // when driver is on the way to destination with rider and when driver clicks, means it has reached destination
                                            recordRide();
                                            rideEndedByDriver();
                                            break;
                                    }
                                }
                            });
                        }
                    });
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
    }

    private void getRoute(LatLng riderLatLng) {
        Routing routing = new Routing.Builder()
                .travelMode(AbstractRouting.TravelMode.DRIVING)
                .withListener(this)
                .alternativeRoutes(false)
                .waypoints(new LatLng(currentLatLng.latitude, currentLatLng.longitude), riderLatLng)
                .build();
        routing.execute();
    }


    @Override
    public void onRoutingFailure(RouteException e) {
        // The Routing request failed
        if(e != null) {
        //    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            Toast.makeText(DriverMapsActivity.this,"No PolyLines",Toast.LENGTH_LONG).show();
        }else {
            Toast.makeText(this, "Something went wrong, Try again", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRoutingStart() {

    }

    @Override
    public void onRoutingSuccess(ArrayList<Route> routeArrayList, int shortestRouteIndex) {
        if(polylines.size()>0) {
            for (Polyline poly : polylines) {
                poly.remove();
            }
        }

        polylines = new ArrayList<>();
        //add route(s) to the map.
        for (int i = 0; i <routeArrayList.size(); i++) {

            //In case of more than 5 alternative routes
            int colorIndex = i % COLORS.length;

            PolylineOptions polyOptions = new PolylineOptions();
            polyOptions.color(getResources().getColor(COLORS[colorIndex]));
            polyOptions.width(10 + i * 3);
            polyOptions.addAll(routeArrayList.get(i).getPoints());
            Polyline polyline = mMap.addPolyline(polyOptions);
            polylines.add(polyline);

            Toast.makeText(getApplicationContext(),"Route "+ (i+1) +": distance - "+ routeArrayList.get(i).getDistanceValue()+": duration - "+ routeArrayList.get(i).getDurationValue(),Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRoutingCancelled() {

    }

    private void removePolyLines(){
        for (Polyline polyline : polylines){
            polyline.remove(); // removing each n every polyline from map
        }
        polylines.clear(); // clearing polylines array
    }

    // updating ride history for Driver and Rider

    private void recordRide() {
        DatabaseReference historyRef = databaseReference.child("History");
        String rideId = historyRef.push().getKey();

        HashMap<String, Object> rideHashMap = new HashMap<>();
        rideHashMap.put(NodeNames.DRIVERID,currentUserId);
        rideHashMap.put(NodeNames.RIDERID,riderId);
        rideHashMap.put(NodeNames.RIDEID,rideId);
        rideHashMap.put(NodeNames.DESTINATIONLATLNG,destinationLatLng);

        Calendar date = Calendar.getInstance();
        @SuppressLint("SimpleDateFormat")
        SimpleDateFormat currentDateFormat = new SimpleDateFormat("MMM dd,yyyy"); // Dec2,2020
        String currentDate = currentDateFormat.format(date.getTime());
        rideHashMap.put(NodeNames.RIDEDATE,currentDate);

        Calendar time = Calendar.getInstance();
        @SuppressLint("SimpleDateFormat")
        SimpleDateFormat currentTimeFormat = new SimpleDateFormat("hh:mm a"); // 05:27 PM
        String currentTime = currentTimeFormat.format(time.getTime());
        rideHashMap.put(NodeNames.RIDETIME,currentTime);

        String riderReference = NodeNames.RIDINGHISTORY + "/" + riderId + "/" + rideId;
        HashMap<String,Object> riderHashMap = new HashMap<>();
        riderHashMap.put(riderReference,rideHashMap);

        String driverReference = NodeNames.DRIVINGHISTORY + "/" + currentUserId + "/" + rideId;
        HashMap<String,Object> driverHashMap = new HashMap<>();
        driverHashMap.put(driverReference,rideHashMap);

        driverDatabaseReference.child(currentUserId).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if(snapshot.hasChild(NodeNames.RIDERDESTINATION)){
                    rideHashMap.put(NodeNames.RIDERDESTINATION,snapshot.child(NodeNames.RIDERDESTINATION).getValue().toString());

                    databaseReference.updateChildren(riderHashMap, new DatabaseReference.CompletionListener() {
                        @Override
                        public void onComplete(@Nullable DatabaseError error, @NonNull DatabaseReference ref) {
                            if(error!=null){
                                Toast.makeText(DriverMapsActivity.this,"error: " + error.getMessage(),Toast.LENGTH_SHORT).show();
                            }
                        }
                    });

                    databaseReference.updateChildren(driverHashMap, new DatabaseReference.CompletionListener() {
                        @Override
                        public void onComplete(@Nullable DatabaseError error, @NonNull DatabaseReference ref) {
                            if(error!=null){
                                Toast.makeText(DriverMapsActivity.this,"error: " + error.getMessage(),Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
    }

    private void rideEndedByDriver() {
        GeoFire geoFire = new GeoFire(riderRequestsDatabaseReference);
        geoFire.removeLocation(riderId); // removing location updates
        if(riderRequestsListener != null){
            riderRequestsDatabaseReference.removeEventListener(riderRequestsListener);
        }
        if(driverDatabaseReference.child(currentUserId).child(NodeNames.RIDERID)!=null){
            driverDatabaseReference.child(currentUserId).child(NodeNames.RIDERID).setValue(null).addOnCompleteListener(new OnCompleteListener<Void>() {
                @Override
                public void onComplete(@NonNull Task<Void> task) {
                    if(task.isSuccessful()){
                        driverDatabaseReference.child(currentUserId).child(NodeNames.DESTINATIONLATITUDE).setValue(null);
                        driverDatabaseReference.child(currentUserId).child(NodeNames.DESTINATIONLONGITUDE).setValue(null);
                        Toast.makeText(DriverMapsActivity.this,"Destination Reached",Toast.LENGTH_LONG).show();
                    }
                }
            });
        }

        riderId = "";
        if(riderMarker!=null){
            riderMarker.remove();
        }

        removePolyLines();
        riderDistance.setText("No Request Found");
    }

    // when activity is stopped, location updates are removed

    @Override
    protected void onStop() {
        super.onStop();

        GeoFire geoFireAvailable = new GeoFire(availableDriverDatabaseReference);
        geoFireAvailable.removeLocation(currentUserId);
        GeoFire geoFireWorking = new GeoFire(workingDriverDatabaseReference);
        geoFireWorking.removeLocation(currentUserId);

        fusedLocationProviderClient.removeLocationUpdates(locationCallback);
    }
}