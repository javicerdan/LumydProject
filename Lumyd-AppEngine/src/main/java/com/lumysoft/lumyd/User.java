package com.lumysoft.lumyd;

import com.beoui.geocell.annotations.Geocells;
import com.beoui.geocell.annotations.Latitude;
import com.beoui.geocell.annotations.Longitude;
import com.google.appengine.api.images.Image;

import java.util.ArrayList;
import java.util.List;

import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.OneToMany;

/**
 * Created by Javier Cerdán on 13/07/13.
 */
@Entity
public class User {

    @Id
    private String sourceDevice;

    @Longitude
    private double longitude;
    @Latitude
    private double latitude;
    @Geocells
    @OneToMany(fetch = FetchType.EAGER)
    private java.util.List<String> geoCellsData = new ArrayList<String>();
    private String name;
    private int age;
    private Image photo;
    private long timestamp;

    public String getSourceDevice() {
        return sourceDevice;
    }

    public void setSourceDevice(String sourceDevice) {
        this.sourceDevice = sourceDevice;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    public Image getPhoto() {
        return photo;
    }

    public void setPhoto(Image photo) {
        this.photo = photo;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public List<String> getGeoCellsData() {
        return geoCellsData;
    }

    public void setGeoCellsData(List<String> geoCellsData) {
        this.geoCellsData = geoCellsData;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
