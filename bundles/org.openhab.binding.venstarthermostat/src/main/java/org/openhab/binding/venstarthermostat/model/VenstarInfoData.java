package org.openhab.binding.venstarthermostat.model;

public class VenstarInfoData {
    double cooltemp;
    double heattemp;

    VenstarSystemState state;
    VenstarSystemMode mode;
    int tempunits;

    public VenstarInfoData() {
        super();
    }

    public VenstarInfoData(double cooltemp, double heattemp, VenstarSystemState state, VenstarSystemMode mode) {
        super();
        this.cooltemp = cooltemp;
        this.heattemp = heattemp;
        this.state = state;
        this.mode = mode;
    }

    public double getCooltemp() {
        return cooltemp;
    }

    public void setCooltemp(double cooltemp) {
        this.cooltemp = cooltemp;
    }

    public double getHeattemp() {
        return heattemp;
    }

    public void setHeattemp(double heattemp) {
        this.heattemp = heattemp;
    }

    public VenstarSystemState getState() {
        return state;
    }

    public void setState(VenstarSystemState state) {
        this.state = state;
    }

    public VenstarSystemMode getMode() {
        return mode;
    }

    public void setMode(VenstarSystemMode mode) {
        this.mode = mode;
    }

    public int getTempunits() { return tempunits; }

    public void setTempunits(int tempunits) { this.tempunits = tempunits; }
}
