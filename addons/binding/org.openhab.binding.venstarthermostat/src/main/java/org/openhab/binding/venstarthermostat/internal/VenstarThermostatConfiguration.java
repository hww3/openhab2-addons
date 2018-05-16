package org.openhab.binding.venstarthermostat.internal;

public class VenstarThermostatConfiguration {
    public String url;
    public String username;
    public String password;
    public Integer refresh;

    public String getUrl() {
        return url;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public Integer getRefresh() {
        return refresh;
    }

}
