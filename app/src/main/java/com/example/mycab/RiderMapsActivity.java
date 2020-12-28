package com.example.mycab;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.example.mycab.firebasetree.Constants;
import com.example.mycab.firebasetree.NodeNames;
import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.firebase.geofire.GeoQuery;
import com.firebase.geofire.GeoQueryEventListener;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.places.AutocompleteFilter;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.ui.PlaceAutocompleteFragment;
import com.google.android.gms.location.places.ui.PlaceSelectionListener;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
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

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import de.hdodenhof.circleimageview.CircleImageView;

public class RiderMapsActivity extends FragmentActivity implements OnMapReadyCallback {

    /* rider can search for available cabs nearby, search places nearby, see real time location of Cab */

    private GoogleMap mMap;

    private CircleImageView profileImageView;
    private TextView searchCab;
    private SearchView locationSearchView;

    LocationManager locationManager; // to obtain periodic updates of the device's geographical location
    LocationListener locationListener; // Used for receiving notifications from the LocationManager when the location has changed
    LatLng lastKnownLatLng, destinationLatLng; // Represents a geographical location with a latitude and a longitude
    Location lastKnownLocation; // class representing a geographic location consisting of a latitude, longitude, timestamp, and other information such as bearing, altitude and velocity
    FusedLocationProviderClient fusedLocationProviderClient; // location APIs in Google Play services that intelligently combines different signals to provide the location
    View mapView, myLocationBtn;
    RelativeLayout.LayoutParams layoutParams;

    int accessFineLocationRequestCode = 101, radius = 1;
    Boolean driverFound = false, requestCancelled = false;
    GeoQuery geoQuery; // to show nearest user depending on certain criteria in a given circle
    ValueEventListener workingDriverListener, rideEndedListener;

    FirebaseAuth firebaseAuth; // to create object of Firebase Auth class to fetch currently loged in user
    FirebaseUser firebaseUser; // to create object of Firebase User class to get current user to store currently loged in user
    DatabaseReference databaseReference,riderDatabaseReference, riderRequestsDatabaseReference, availableDriverDatabaseReference, workingDriverDatabaseReference, driverDatabaseReference, rideEndedDatabaseReference;

    HashMap<String,Object> riderInfoHashMap, referenceHashMap;
    String currentUserId, nodeReference, driverFoundId, riderDestination;

    private ProgressDialog progressDialog;
    Marker driverLocationMarker, riderMarker, destinationMarker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rider_maps);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.ridersMap);
        mapFragment.getMapAsync(this);

        mapView = mapFragment.getView(); // for adjusting my location button
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(RiderMapsActivity.this); // get last known location of device

    }

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

        locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(@NonNull Location location) {
                getLastKnownLocation();
            }
        };

        if (Build.VERSION.SDK_INT < 23) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
            }
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
            getLastKnownLocation();
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) // GPS Service of our Device is ON
            {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
                getLastKnownLocation();
            }
        }

        // getting current user

        firebaseAuth = FirebaseAuth.getInstance();
        firebaseUser = firebaseAuth.getCurrentUser();
        currentUserId = Objects.requireNonNull(firebaseUser).getUid();

        // nodes reference of database

        databaseReference = FirebaseDatabase.getInstance().getReference();
        riderDatabaseReference = databaseReference.child(NodeNames.RIDERS);
        riderRequestsDatabaseReference = databaseReference.child(NodeNames.RIDERREQUESTS);
        driverDatabaseReference = databaseReference.child(NodeNames.DRIVERS);

        riderInfoHashMap = new HashMap<>();

        profileImageView = findViewById(R.id.profileImageView);

        StorageReference profileImage = FirebaseStorage.getInstance().getReference().child(Constants.IMAGESFOLDER).child(currentUserId);
        profileImage.getDownloadUrl().addOnSuccessListener(new OnSuccessListener<Uri>() {
            @Override
            public void onSuccess(Uri uri) {
                Glide.with(RiderMapsActivity.this).load(uri).placeholder(R.drawable.profile).into(profileImageView); // loading profile image
            }
        });

        profileImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(RiderMapsActivity.this,EditProfileActivity.class));
            }
        });

    /*    PlaceAutocompleteFragment autocompleteFragment = (PlaceAutocompleteFragment) getFragmentManager().findFragmentById(R.id.place_autocomplete_fragment);

        AutocompleteFilter typeFilter = new AutocompleteFilter.Builder().setTypeFilter(AutocompleteFilter.TYPE_FILTER_ADDRESS).build(); // filter returning only results with a precise address
        autocompleteFragment.setFilter(typeFilter);

        autocompleteFragment.setOnPlaceSelectedListener(new PlaceSelectionListener() {
            @Override
            public void onPlaceSelected(Place place) {
                riderDestination = place.getName().toString();
            }

            @SuppressLint("LongLogTag")
            @Override
            public void onError(Status status) {
                Toast.makeText(RiderMapsActivity.this,"Failed to fetch location: " + status,Toast.LENGTH_LONG).show();
            }
        }); */

        /* <fragment
                android:id="@+id/place_autocomplete_fragment"
                android:name="com.google.android.gms.location.places.ui.PlaceAutocompleteFragment"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                map:layout_constraintBottom_toBottomOf="parent"
                map:layout_constraintEnd_toEndOf="parent"
                map:layout_constraintStart_toStartOf="parent"
                map:layout_constraintTop_toTopOf="parent">

            </fragment> */

        destinationLatLng = new LatLng(0.0,0.0);

        // searching for destination

        locationSearchView = findViewById(R.id.locationSearchView);
        locationSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                if(destinationMarker!=null){
                    destinationMarker.remove();
                }
                riderDestination = locationSearchView.getQuery().toString();
                List<Address> addressList = null;
                if(!riderDestination.equals("")){
                    Geocoder geocoder = new Geocoder(RiderMapsActivity.this); // generate LatLng from address
                    try {
                        addressList = geocoder.getFromLocationName(riderDestination,1);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    Address address = Objects.requireNonNull(addressList).get(0);
                    destinationLatLng = new LatLng(address.getLatitude(),address.getLongitude()); // retrieving destination Latitude & Longitude
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(destinationLatLng, 18)); // focusing on destination location
                    Toast.makeText(RiderMapsActivity.this,riderDestination,Toast.LENGTH_LONG).show();
                    destinationMarker = mMap.addMarker(new MarkerOptions().position(destinationLatLng).title(riderDestination)); // adding marker to destination location
                }
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                return false;
            }
        });

        searchCab = findViewById(R.id.searchCab);
        searchCab.setText("Search Cab");

        progressDialog = new ProgressDialog(RiderMapsActivity.this); // instantiating ProgressDialog
    }

    private void getLastKnownLocation() {
        // checking permission to Access Location of device
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
                    riderMarker = mMap.addMarker(new MarkerOptions().position(lastKnownLatLng).title("Your Location"));  // adding marker to rider location
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(lastKnownLatLng, 18)); // focusing on rider location
                    if (ActivityCompat.checkSelfPermission(RiderMapsActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(RiderMapsActivity.this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        return;
                    }
                    mMap.setMyLocationEnabled(true);
                    mMap.getUiSettings().setMyLocationButtonEnabled(true);

                    riderInfoHashMap.put(NodeNames.RIDERID,currentUserId);
                    riderInfoHashMap.put(NodeNames.RIDERLATITUDE,lastKnownLocation.getLatitude());
                    riderInfoHashMap.put(NodeNames.RIDERLONGITUDE,lastKnownLocation.getLongitude());

                    nodeReference = NodeNames.RIDERS + "/" + currentUserId;
                    referenceHashMap = new HashMap<>();
                    referenceHashMap.put(nodeReference,riderInfoHashMap);
                    databaseReference.updateChildren(referenceHashMap, new DatabaseReference.CompletionListener() {
                        @Override
                        public void onComplete(@Nullable DatabaseError error, @NonNull DatabaseReference ref) {
                            if(error==null){
                                Toast.makeText(RiderMapsActivity.this,"Request your Cab ride",Toast.LENGTH_SHORT).show();
                            }else {
                                Toast.makeText(RiderMapsActivity.this,"error: " + error.getMessage(),Toast.LENGTH_LONG).show();
                            }
                        }
                    });

                    // searching for nearby available driver

                    searchCab.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {

                            if(requestCancelled){
                                rideEndedByRider(); // rider cancelled ride
                            }else {
                                requestCancelled = true;
                                GeoFire geoFire = new GeoFire(riderRequestsDatabaseReference);
                                geoFire.setLocation(currentUserId, new GeoLocation(lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude()));

                                progressDialog.setTitle("Getting Your Driver");
                                progressDialog.setMessage("Please wait while we are looking for nearby drivers");
                                progressDialog.show();

                                getClosestDriver();
                            }
                        }
                    });
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
                    locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
                    getLastKnownLocation();
                }
            }
        }
    }

    // getting nearby free driver

    private void getClosestDriver() {
        availableDriverDatabaseReference = databaseReference.child(NodeNames.AVAILABLEDRIVERS);

        // uses the Firebase Realtime Database for data storage to read and write geo location data to your Firebase database and to create queries

        GeoFire geoFire = new GeoFire(availableDriverDatabaseReference);

        geoQuery = geoFire.queryAtLocation(new GeoLocation(lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude()),radius); // creating radius around customer requests
        geoQuery.removeAllListeners();

        geoQuery.addGeoQueryEventListener(new GeoQueryEventListener() {
            @Override
            public void onKeyEntered(String key, GeoLocation location) {
                // triggered when driver is found within the radius
                if(!driverFound && requestCancelled){
                    driverFound = true;
                    driverFoundId = key;

                    HashMap<String,Object> hashMap = new HashMap<>();
                    hashMap.put(NodeNames.RIDERID,currentUserId);
                    hashMap.put(NodeNames.DRIVERID,driverFoundId);
                    hashMap.put(NodeNames.RIDERDESTINATION,riderDestination);
                    hashMap.put(NodeNames.DESTINATIONLATITUDE,destinationLatLng.latitude);
                    hashMap.put(NodeNames.DESTINATIONLONGITUDE,destinationLatLng.longitude);

                    driverDatabaseReference.child(driverFoundId).updateChildren(hashMap).addOnCompleteListener(new OnCompleteListener<Void>() {
                        @Override
                        public void onComplete(@NonNull Task<Void> task) {
                            if(task.isSuccessful()){
                                progressDialog.dismiss();
                                searchCab.setText("Driver Found");
                                progressDialog.setTitle("Fetching Driver Location");
                                progressDialog.setMessage("Please wait while we are retrieving drivers location");
                                progressDialog.show();

                                getDriverLocation();
                                checkIfRideEnded();
                            }
                        }
                    });
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
                if(!driverFound){
                    radius++; // if driver is not found in previous radius, increment driver search radius
                    getClosestDriver();
                }
            }

            @Override
            public void onGeoQueryError(DatabaseError error) {

            }
        });
    }

    private void getDriverLocation() {
        workingDriverDatabaseReference = databaseReference.child(NodeNames.WORKINGDRIVERS).child(driverFoundId).child("l");
        workingDriverListener = workingDriverDatabaseReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if(snapshot.exists()){

                    List<Object> latLng = (List<Object>) snapshot.getValue();
                    double lat = 0, lng = 0;

                    if(latLng.get(0)!=null){
                        lat = Double.parseDouble(latLng.get(0).toString());
                    }
                    if(latLng.get(1)!=null){
                        lng = Double.parseDouble(latLng.get(1).toString());
                    }

                    LatLng driverLatLng = new LatLng(lat,lng);
                    if(driverLocationMarker!=null){
                        driverLocationMarker.remove();
                    }
                    driverLocationMarker = mMap.addMarker(new MarkerOptions().position(driverLatLng).title("Your Driver").icon(BitmapDescriptorFactory.fromResource(R.mipmap.driver_marker)));

                    Location pickUpLocation = new Location("");
                    pickUpLocation.setLatitude(lastKnownLatLng.latitude);
                    pickUpLocation.setLongitude(lastKnownLatLng.longitude);

                    Location driverLocation = new Location("");
                    driverLocation.setLatitude(driverLatLng.latitude);
                    driverLocation.setLongitude(driverLatLng.longitude);

                    float distance = driverLocation.distanceTo(pickUpLocation);

                    searchCab.setText("Driver is:" + String.valueOf(distance) + " m away");

                    progressDialog.dismiss();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
    }

    private void checkIfRideEnded(){
        rideEndedDatabaseReference = driverDatabaseReference.child(driverFoundId).child(currentUserId);
        rideEndedListener = rideEndedDatabaseReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if(snapshot.exists()){

                }else {
                    rideEndedByRider();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
    }

    private void rideEndedByRider() {
        View infoView = LayoutInflater.from(RiderMapsActivity.this).inflate(R.layout.driver_info_dialog,null);

        CircleImageView driverImage = infoView.findViewById(R.id.driverProfileImageView);
        TextView profileNameTextView = infoView.findViewById(R.id.profileNameTextView);
        TextView cancelCabTextView = infoView.findViewById(R.id.cancelCabTextView);
        TextView callTextView = infoView.findViewById(R.id.callTextView);

        StorageReference profileImage = FirebaseStorage.getInstance().getReference().child(Constants.IMAGESFOLDER + "/" + driverFoundId);
        profileImage.getDownloadUrl().addOnSuccessListener(new OnSuccessListener<Uri>() {
            @Override
            public void onSuccess(Uri uri) {
                Glide.with(RiderMapsActivity.this).load(uri).placeholder(R.drawable.profile).into(driverImage);
            }
        });

        databaseReference.child(NodeNames.USERS).child(driverFoundId).addListenerForSingleValueEvent(new ValueEventListener() {
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

        AlertDialog alertDialog = new AlertDialog.Builder(RiderMapsActivity.this).setView(infoView).create();
        alertDialog.show();

        cancelCabTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                requestCancelled = false;
                geoQuery.removeAllListeners(); // removing request radius around rider
                workingDriverDatabaseReference.removeEventListener(workingDriverListener); // removing Value Event Listener
                rideEndedDatabaseReference.removeEventListener(rideEndedListener);
                GeoFire geoFire = new GeoFire(riderRequestsDatabaseReference);
                geoFire.removeLocation(currentUserId);

                if(driverFoundId != null){
                    driverDatabaseReference.child(driverFoundId).child(NodeNames.RIDERID).setValue(null).addOnCompleteListener(new OnCompleteListener<Void>() {
                        @Override
                        public void onComplete(@NonNull Task<Void> task) {
                            if(task.isSuccessful()){
                                driverFoundId = null;
                                driverFound = false;
                                radius = 1;
                                if(driverLocationMarker!=null){
                                    driverLocationMarker.remove();
                                }
                                searchCab.setText("Search Cab");
                                driverDatabaseReference.child(driverFoundId).child(NodeNames.RIDERDESTINATION).setValue(null);
                                driverDatabaseReference.child(driverFoundId).child(NodeNames.DESTINATIONLATITUDE).setValue(null);
                                driverDatabaseReference.child(driverFoundId).child(NodeNames.DESTINATIONLONGITUDE).setValue(null);
                                alertDialog.dismiss();
                            }
                        }
                    });
                }
            }
        });

        callTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                alertDialog.dismiss();
            }
        });
    }
}