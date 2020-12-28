package com.example.mycab;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.ViewPager;

import android.os.Bundle;

import com.example.mycab.firebasetree.NodeNames;
import com.example.mycab.fragment.DriveHistoryFragment;
import com.example.mycab.fragment.RideHistoryFragment;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.Objects;

import butterknife.ButterKnife;

public class HistoryActivity extends AppCompatActivity {

    /* view history of previous rides */

    private TabLayout tabLayout;
    private ViewPager viewPager;

    FirebaseAuth firebaseAuth; // to create object of Firebase Auth class to fetch currently loged in user
    FirebaseUser firebaseUser; // to create object of Firebase User class to get current user to store currently loged in user
    DatabaseReference drivingHistoryDatabaseReference, ridingHistoryDatabaseReference;
    String currentUserId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);
        ButterKnife.bind(this);

        // getting current user

        firebaseAuth = FirebaseAuth.getInstance();
        firebaseUser = firebaseAuth.getCurrentUser();
        currentUserId = Objects.requireNonNull(firebaseUser).getUid();

        drivingHistoryDatabaseReference = FirebaseDatabase.getInstance().getReference().child(NodeNames.DRIVINGHISTORY);
        ridingHistoryDatabaseReference = FirebaseDatabase.getInstance().getReference().child(NodeNames.RIDINGHISTORY);

        tabLayout = findViewById(R.id.tabLayout);
        viewPager = findViewById(R.id.viewPager);

        setViewPager();
    }

    // to manage swipe feature we need adapter
    class ViewPagerAdapter extends FragmentPagerAdapter {
        public ViewPagerAdapter(@NonNull FragmentManager fm, int behavior) {
            super(fm, behavior);
        }

        @NonNull
        @Override
        public Fragment getItem(int position) {
            switch (position){
                case 0:
                    return new DriveHistoryFragment();

                case 1:
                    return new RideHistoryFragment();
            }
            return null;
        }

        @Override
        public int getCount() {
            return tabLayout.getTabCount();
        }
    }

    private void setViewPager(){
        tabLayout.addTab(tabLayout.newTab().setCustomView(R.layout.tab_driver_history));
        tabLayout.addTab(tabLayout.newTab().setCustomView(R.layout.tab_rider_history));

        tabLayout.setTabGravity(TabLayout.GRAVITY_FILL);
        ViewPagerAdapter viewPagerAdapter = new ViewPagerAdapter(getSupportFragmentManager(), FragmentPagerAdapter.BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT); // only current fragment will be in state of resume and other in start mode
        viewPager.setAdapter(viewPagerAdapter);

        tabLayout.setOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                viewPager.setCurrentItem(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {

            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {

            }
        });

        viewPager.addOnPageChangeListener(new TabLayout.TabLayoutOnPageChangeListener(tabLayout));
    }
}