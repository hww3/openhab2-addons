package org.openhab.binding.venstarthermostat.model;

public enum VenstarSystemMode {
    OFF(0, "off", "Off"),
    HEAT(1, "heat", "Heat"),
    COOL(2, "cool", "Cool"),
    AUTO(3, "auto", "Auto");

    private int mode;
    private String name;
    private String friendlyName;

    VenstarSystemMode(int mode, String name, String friendlyName) {
        this.mode = mode;
        this.name = name;
        this.friendlyName = friendlyName;
    }

    public int mode() {
        return mode;
    }

    public String modeName() {
        return name;
    }

    public String friendlyName() {
        return friendlyName;
    }

    public static VenstarSystemMode fromInt(int mode) {
        for(VenstarSystemMode sm: values()) {
            if(sm.mode == mode) return sm;
        }

        throw(new IllegalArgumentException("Invalid system mode " + mode));
    }
}