package com.example.mycab.model;

import com.google.android.gms.maps.model.LatLng;

public class RideHistoryModelClass {

    String DriverId, RideId, RiderDestination, RiderId, RideDate, RideTime;
    LatLng destinationLatLng;

    public String getDriverId() {
        return DriverId;
    }

    public void setDriverId(String driverId) {
        DriverId = driverId;
    }

    public String getRideId() {
        return RideId;
    }

    public void setRideId(String rideId) {
        RideId = rideId;
    }

    public String getRiderDestination() {
        return RiderDestination;
    }

    public void setRiderDestination(String riderDestination) {
        RiderDestination = riderDestination;
    }

    public String getRiderId() {
        return RiderId;
    }

    public void setRiderId(String riderId) {
        RiderId = riderId;
    }

    public String getRideDate() {
        return RideDate;
    }

    public void setRideDate(String rideDate) {
        RideDate = rideDate;
    }

    public String getRideTime() {
        return RideTime;
    }

    public void setRideTime(String rideTime) {
        RideTime = rideTime;
    }

    public LatLng getDestinationLatLng() {
        return destinationLatLng;
    }

    public void setDestinationLatLng(LatLng destinationLatLng) {
        this.destinationLatLng = destinationLatLng;
    }

    public RideHistoryModelClass(String driverId, String rideId, String riderDestination, String riderId, String rideDate, String rideTime, LatLng destinationLatLng) {
        DriverId = driverId;
        RideId = rideId;
        RiderDestination = riderDestination;
        RiderId = riderId;
        RideDate = rideDate;
        RideTime = rideTime;
        this.destinationLatLng = destinationLatLng;
    }

    public RideHistoryModelClass() {
    }
}
