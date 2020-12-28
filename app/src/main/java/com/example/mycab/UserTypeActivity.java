package com.example.mycab;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.LocationManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.Toast;

import com.example.mycab.firebasetree.NodeNames;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.w3c.dom.Node;

import java.util.HashMap;
import java.util.Objects;

import butterknife.BindView;
import butterknife.ButterKnife;

public class UserTypeActivity extends AppCompatActivity {

    @BindView(R.id.riderTextView)
    TextView riderTextView;
    @BindView(R.id.cabSwitch)
    SwitchMaterial cabSwitch;
    @BindView(R.id.driverTextView)
    TextView driverTextView;
    @BindView(R.id.getStartedTextView)
    TextView getStartedTextView;

    FirebaseAuth firebaseAuth; // to create object of Firebase Auth class to fetch currently loged in user
    FirebaseUser firebaseUser; // to create object of Firebase User class to get current user to store currently loged in user
    DatabaseReference databaseReference,riderDatabaseReference, driverDatabaseReference;

    HashMap<String,Object> userInfoHashMap, referenceHashMap;
    String currentUserId, nodeReference;

    LocationManager locationManager;
    boolean gpsProviderEnabled;
    AlertDialog gpsAlertDialog;
    int gpsEnableRequestCode = 101;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_type);
        ButterKnife.bind(this);

        if(isGPSEnabled()){
            ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.ACCESS_FINE_LOCATION},1); // permission for accessing GPS for device
        }

        firebaseAuth = FirebaseAuth.getInstance();
        firebaseUser = firebaseAuth.getCurrentUser();
        currentUserId = Objects.requireNonNull(firebaseUser).getUid();

        databaseReference = FirebaseDatabase.getInstance().getReference();
        riderDatabaseReference = databaseReference.child(NodeNames.RIDERS);
        driverDatabaseReference = databaseReference.child(NodeNames.DRIVERS);

        riderDatabaseReference.child(currentUserId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if(snapshot.exists()){
                    riderDatabaseReference.child(currentUserId).removeValue();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });

        driverDatabaseReference.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if(snapshot.exists()){
                    driverDatabaseReference.child(currentUserId).removeValue();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });

        getStartedTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(cabSwitch.isChecked()){
                    Toast.makeText(UserTypeActivity.this,"Continuing as Driver",Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(UserTypeActivity.this,DriverMapsActivity.class));
                    finish();
                }else {
                    Toast.makeText(UserTypeActivity.this,"Continuing as Rider",Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(UserTypeActivity.this,RiderMapsActivity.class));
                    finish();
                }
            }
        });
    }

    private boolean isGPSEnabled(){
        locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        gpsProviderEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);

        if(gpsProviderEnabled){
            return true;
        }else {
            gpsAlertDialog = new AlertDialog.Builder(this)
                    .setTitle("GPS Enabling Permission").setMessage("GPS is required for tracking location,Please enable Location Services")
                    .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            Intent gpsSettingsIntent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                            startActivityForResult(gpsSettingsIntent,gpsEnableRequestCode);
                        }
                    }).setCancelable(false).show();
        }
        return false;
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode==gpsEnableRequestCode){
            gpsProviderEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            if(gpsProviderEnabled){
                Toast.makeText(UserTypeActivity.this,"Location Services Enabled",Toast.LENGTH_SHORT).show();
            }else {
                Toast.makeText(UserTypeActivity.this,"GPS not enabled,Unable to track user location",Toast.LENGTH_SHORT).show();
            }
        }
    }
}