package com.example.mycab.fragment;

import android.location.Address;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.example.mycab.R;
import com.example.mycab.RiderMapsActivity;
import com.example.mycab.firebasetree.Constants;
import com.example.mycab.firebasetree.NodeNames;
import com.example.mycab.model.RideHistoryModelClass;
import com.firebase.ui.database.FirebaseRecyclerAdapter;
import com.firebase.ui.database.FirebaseRecyclerOptions;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.Objects;

import de.hdodenhof.circleimageview.CircleImageView;

public class DriveHistoryFragment extends Fragment {

    private RecyclerView driveHistoryFragmentRecyclerView;

    FirebaseAuth firebaseAuth; // to create object of Firebase Auth class to fetch currently loged in user
    FirebaseUser firebaseUser; // to create object of Firebase User class to get current user to store currently loged in user
    DatabaseReference databaseReference, drivingHistoryDatabaseReference, usersDatabaseReference;
    StorageReference storageReference;

    private String currentUserId, destination;

    LatLng destinationLatLng;
    Marker destinationMarker;

    private GoogleMap mMap;
    private MapView mapView;
    private SupportMapFragment supportMapFragment;

    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";

    private String mParam1;
    private String mParam2;

    public DriveHistoryFragment() {
        // Required empty public constructor
    }

    public static DriveHistoryFragment newInstance(String param1, String param2) {
        DriveHistoryFragment fragment = new DriveHistoryFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PARAM1, param1);
        args.putString(ARG_PARAM2, param2);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mParam1 = getArguments().getString(ARG_PARAM1);
            mParam2 = getArguments().getString(ARG_PARAM2);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_drive_history, container, false);

        driveHistoryFragmentRecyclerView = view.findViewById(R.id.driveHistoryFragmentRecyclerView);
        driveHistoryFragmentRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        firebaseAuth = FirebaseAuth.getInstance();
        firebaseUser = firebaseAuth.getCurrentUser();
        currentUserId = Objects.requireNonNull(firebaseUser).getUid();
        databaseReference = FirebaseDatabase.getInstance().getReference();
        drivingHistoryDatabaseReference = databaseReference.child(NodeNames.DRIVINGHISTORY);
        usersDatabaseReference = databaseReference.child(NodeNames. USERS);
        storageReference = FirebaseStorage.getInstance().getReference();

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();

        FirebaseRecyclerOptions<RideHistoryModelClass> firebaseRecyclerOptions = new FirebaseRecyclerOptions.Builder<RideHistoryModelClass>().setQuery(drivingHistoryDatabaseReference.child(currentUserId),RideHistoryModelClass.class).build();

        FirebaseRecyclerAdapter<RideHistoryModelClass,RideHistoryViewHolder> firebaseRecyclerAdapter = new FirebaseRecyclerAdapter<RideHistoryModelClass, RideHistoryViewHolder>(firebaseRecyclerOptions) {
            @Override
            protected void onBindViewHolder(@NonNull RideHistoryViewHolder holder, int position, @NonNull RideHistoryModelClass model) {

                holder.placeTextView.setText(model.getRiderDestination());
                destination = model.getRiderDestination();
            //    destinationLatLng = model.getDestinationLatLng();
                holder.dateTextView.setText(model.getRideDate());
                holder.timeTextView.setText(model.getRideTime());

                String riderId = model.getRiderId();

                usersDatabaseReference.child(riderId).addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if(snapshot.exists()){
                            if(snapshot.hasChild(NodeNames.PROFILENAME)){
                                String name = Objects.requireNonNull(snapshot.child(NodeNames.PROFILENAME).getValue()).toString();
                                holder.userNameTextView.setText(name);
                            }
                            if(snapshot.hasChild(NodeNames.MOBILENUMBER)){
                                String contact = snapshot.child(NodeNames.MOBILENUMBER).getValue().toString();
                                holder.contactTextView.setText(contact);
                            }
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {

                    }
                });

                StorageReference profileImage = storageReference.child(Constants.IMAGESFOLDER).child(riderId);
                profileImage.getDownloadUrl().addOnSuccessListener(new OnSuccessListener<Uri>() {
                    @Override
                    public void onSuccess(Uri uri) {
                        Glide.with(getContext()).load(uri).placeholder(R.drawable.profile).into(holder.userProfileImageView);
                    }
                });
            }

            @NonNull
            @Override
            public RideHistoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View view = LayoutInflater.from(getContext()).inflate(R.layout.ride_history_layout,parent, false);
                return new RideHistoryViewHolder(view);
            }
        };
        driveHistoryFragmentRecyclerView.setAdapter(firebaseRecyclerAdapter);
        firebaseRecyclerAdapter.startListening();
    }

    public static class RideHistoryViewHolder extends RecyclerView.ViewHolder {

        private CircleImageView userProfileImageView;
        private TextView userNameTextView,placeTextView, contactTextView, dateTextView, timeTextView;

        public RideHistoryViewHolder(@NonNull View itemView) {
            super(itemView);

            userProfileImageView = itemView.findViewById(R.id.userProfileImageView);
            userNameTextView = itemView.findViewById(R.id.userNameTextView);
            placeTextView = itemView.findViewById(R.id.placeTextView);
            contactTextView = itemView.findViewById(R.id.contactTextView);
            dateTextView = itemView.findViewById(R.id.dateTextView);
            timeTextView = itemView.findViewById(R.id.timeTextView);
        }
    }
}