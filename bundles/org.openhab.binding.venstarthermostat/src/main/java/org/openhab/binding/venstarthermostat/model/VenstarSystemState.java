package org.openhab.binding.venstarthermostat.model;

public enum VenstarSystemState {
    IDLE(0, "idle", "Idle"),
    HEATING(1, "heating", "Heating"),
    COOLING(2, "cooling", "Cooling"),
    LOCKOUT(3, "lockout", "Lockout"),
    ERROR(4, "error", "Error");

    private int state;
    private String name;
    private String friendlyName;

    VenstarSystemState(int state, String name, String friendlyName) {
        this.state = state;
        this.name = name;
        this.friendlyName = friendlyName;
    }

    public int state() {
        return state;
    }

    public String stateName() {
        return name;
    }

    public String friendlyName() {
        return friendlyName;
    }

    public static VenstarSystemState fromInt(int state) {
        for(VenstarSystemState ss: values()) {
            if(ss.state == state) return ss;
        }

        throw(new IllegalArgumentException("Invalid system state " + state));

    }
}
