package org.openhab.binding.venstarthermostat.model;

public class VenstarInfoData {
    double cooltemp;
    double heattemp;

    int state;
    int mode;
    int tempunits;

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

    public int getState() {
        return state;
    }

    public void setState(int state) {
        this.state = state;
    }

    public int getMode() {
        return mode;
    }

    public void setMode(int mode) {
        this.mode = mode;
    }

    public int getTempunits() { return tempunits; }

    public void setTempunits(int tempunits) { this.tempunits = tempunits; }
}
