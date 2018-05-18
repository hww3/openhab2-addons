/**
 * Copyright (c) 2014-2016 by the respective copyright holders.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.venstarthermostat.handler;

import static org.openhab.binding.venstarthermostat.VenstarThermostatBindingConstants.*;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.util.DigestAuthentication;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.smarthome.config.core.Configuration;
import org.eclipse.smarthome.config.core.status.ConfigStatusMessage;
import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.binding.ConfigStatusThingHandler;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.State;
import org.eclipse.smarthome.core.types.UnDefType;
import org.openhab.binding.venstarthermostat.VenstarThermostatBindingConstants;
import org.openhab.binding.venstarthermostat.internal.VenstarThermostatConfiguration;
import org.openhab.binding.venstarthermostat.model.VenstarInfoData;
import org.openhab.binding.venstarthermostat.model.VenstarResponse;
import org.openhab.binding.venstarthermostat.model.VenstarSensor;
import org.openhab.binding.venstarthermostat.model.VenstarSensorData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

/**
 * The {@link VenstarThermostatHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author William Welliver - Initial contribution
 * @author Dan Cunningham
 */
public class VenstarThermostatHandler extends ConfigStatusThingHandler {

    private static final int TIMEOUT = 30;
    private Logger log = LoggerFactory.getLogger(VenstarThermostatHandler.class);
    ScheduledFuture<?> refreshJob;
    private List<VenstarSensor> sensorData = new ArrayList<>();
    private VenstarInfoData infoData = new VenstarInfoData();
    private Future<?> updatesTask;
    private VenstarThermostatConfiguration config;
    private HttpClient httpClient;
    private URL baseURL;

    public VenstarThermostatHandler(Thing thing) {
        super(thing);
        httpClient = new HttpClient(new SslContextFactory(true));
        log.trace("VenstarThermostatHandler for thing {}", getThing().getUID());
    }

    @Override
    public Collection<ConfigStatusMessage> getConfigStatus() {
        log.warn("getConfigStatus called.");
        Collection<ConfigStatusMessage> status = new ArrayList<>();
        VenstarThermostatConfiguration config = getConfigAs(VenstarThermostatConfiguration.class);
        if (config.getUsername() == null || config.getUsername().isEmpty()) {
            log.warn("username is empty");
            status.add(ConfigStatusMessage.Builder.error(CONFIG_USERNAME).withMessageKeySuffix(EMPTY_INVALID)
                    .withArguments(CONFIG_USERNAME).build());
        }

        if (config.getPassword() == null || config.getPassword().isEmpty()) {
            log.warn("password is empty");
            status.add(ConfigStatusMessage.Builder.error(CONFIG_PASSWORD).withMessageKeySuffix(EMPTY_INVALID)
                    .withArguments(CONFIG_PASSWORD).build());
        }

        if (config.getRefresh() == null || config.getRefresh().intValue() < 10) {
            log.warn("refresh is too small: {}", config.getRefresh());

            status.add(ConfigStatusMessage.Builder.error(CONFIG_REFRESH).withMessageKeySuffix(REFRESH_INVALID)
                    .withArguments(CONFIG_REFRESH).build());
        }
        log.debug("getConfigStatus returning {}", status);
        return status;
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (!(command instanceof DecimalType)) {
            log.warn("Invalid cooling setpoint command " + command);
            return;
        }
        int value = ((DecimalType) command).intValue();
        if (channelUID.getId().equals(CHANNEL_HEATING_SETPOINT)) {
            log.debug("Setting heating setpoint to {}", value);
            setHeatingSetpoint(value);
        }
        if (channelUID.getId().equals(CHANNEL_COOLING_SETPOINT)) {
            log.debug("Setting cooling setpoint to {}", value);
            setCoolingSetpoint(value);
        }
        if (channelUID.getId().equals(CHANNEL_SYSTEM_MODE)) {
            log.debug("Setting system mode to  {}", value);
            setSystemMode(value);
        }
    }

    @Override
    public void dispose() {
        stopUpdateTasks();
    }

    @Override
    public void initialize() {
        config = getConfigAs(VenstarThermostatConfiguration.class);
        stopUpdateTasks();
        try {
            baseURL = new URL(config.getUrl());
            httpClient.getAuthenticationStore().addAuthentication(new DigestAuthentication(baseURL.toURI(),
                    "thermostat", config.getUsername(), config.getPassword()));
            startUpdatesTask();
        } catch (MalformedURLException | URISyntaxException e) {
            log.error("Invalid url " + config.url, e);
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, e.getMessage());
        }
    }

    public void updateUrl(String url) {
        Configuration configuration = editConfiguration();
        configuration.put(VenstarThermostatBindingConstants.PROPERTY_URL, url);
        updateConfiguration(configuration);
        thingUpdated(getThing());
    }

    protected void goOnline() {
        if (getThing().getStatus() != ThingStatus.ONLINE) {
            updateStatus(ThingStatus.ONLINE);
        }
    }

    protected void goOffline(ThingStatusDetail detail, String reason) {
        if (getThing().getStatus() != ThingStatus.OFFLINE) {
            updateStatus(ThingStatus.OFFLINE, detail, reason);
        }
    }

    private synchronized void startUpdatesTask() {
        if (!httpClient.isStarted()) {
            try {
                httpClient.start();
            } catch (Exception e) {
                log.error("Could not stop HttpClient", e);
            }
        }
        updatesTask = scheduler.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                updateData();
            }
        }, 0, config.refresh.intValue(), TimeUnit.SECONDS);
    }

    private void stopUpdateTasks() {
        if (refreshJob != null) {
            refreshJob.cancel(true);
        }
        if (updatesTask != null) {
            updatesTask.cancel(true);
        }
        httpClient.getAuthenticationStore().clearAuthentications();
        httpClient.getAuthenticationStore().clearAuthenticationResults();
        if (httpClient.isStarted()) {
            try {
                httpClient.stop();
            } catch (Exception e) {
                log.error("Could not stop HttpClient", e);
            }
        }
    }

    private State getTemperature() {
        for (VenstarSensor sensor : sensorData) {
            String name = sensor.getName();
            if (name.equalsIgnoreCase("Thermostat")) {
                return new DecimalType(sensor.getTemp());
            }
        }

        return UnDefType.UNDEF;
    }

    private State getHumidity() {
        for (VenstarSensor sensor : sensorData) {
            String name = sensor.getName();
            if (name.equalsIgnoreCase("Thermostat")) {
                return new DecimalType(sensor.getHum());
            }
        }

        return UnDefType.UNDEF;
    }

    private State getOutdoorTemperature() {
        for (VenstarSensor sensor : sensorData) {
            String name = sensor.getName();
            if (name.equalsIgnoreCase("Outdoor")) {
                return new DecimalType(sensor.getTemp());
            }
        }
        return UnDefType.UNDEF;
    }

    private void setCoolingSetpoint(int cool) {
        int heat = ((DecimalType) getHeatingSetpoint()).intValue();
        int mode = ((DecimalType) getSystemMode()).intValue();

        updateThermostat(heat, cool, mode);
    }

    private void setSystemMode(int mode) {
        int cool = ((DecimalType) getCoolingSetpoint()).intValue();
        int heat = ((DecimalType) getHeatingSetpoint()).intValue();

        updateThermostat(heat, cool, mode);
    }

    private void setHeatingSetpoint(int heat) {
        int cool = ((DecimalType) getCoolingSetpoint()).intValue();
        int mode = ((DecimalType) getSystemMode()).intValue();

        updateThermostat(heat, cool, mode);
    }

    private State getCoolingSetpoint() {
        return new DecimalType(infoData.getCooltemp());
    }

    private State getHeatingSetpoint() {
        return new DecimalType(infoData.getHeattemp());
    }

    private State getSystemState() {
        return new DecimalType(infoData.getState());
    }

    private State getSystemMode() {
        return new DecimalType(infoData.getMode());
    }

    private void updateThermostat(int heat, int cool, int mode) {
        Map<String, String> params = new HashMap<>();
        log.debug("Updating thermostat {}  heat:{} cool {} mode: {}", getThing().getLabel(), heat, cool, mode);
        if (heat > 0) {
            params.put("heattemp", String.valueOf(heat));
        }
        if (cool > 0) {
            params.put("cooltemp", "" + String.valueOf(cool));
        }
        params.put("mode", String.valueOf(mode));
        try {
            String result = postData("/control", params);
            VenstarResponse res = new Gson().fromJson(result, VenstarResponse.class);
            if (res.isSuccess()) {
                log.debug("Updated thermostat");
            } else {
                log.warn("Failed to update thermostat: {}", res.getReason());
                goOffline(ThingStatusDetail.COMMUNICATION_ERROR, "Thermostat update failed: " + res.getReason());
            }
        } catch (VenstarCommunicationException | JsonSyntaxException e) {
            log.debug("Unable to fetch info data", e);
            goOffline(ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
        } catch (VenstarAuthenticationException e) {
            goOffline(ThingStatusDetail.CONFIGURATION_ERROR, "Authorization Failed");
        }
    }

    private void updateData() {
        try {
            String response = getData("/query/sensors");
            VenstarSensorData res = new Gson().fromJson(response, VenstarSensorData.class);
            sensorData = res.getSensors();
            updateState(new ChannelUID(getThing().getUID(), CHANNEL_TEMPERATURE), getTemperature());
            updateState(new ChannelUID(getThing().getUID(), CHANNEL_EXTERNAL_TEMPERATURE), getOutdoorTemperature());
            updateState(new ChannelUID(getThing().getUID(), CHANNEL_HUMIDITY), getHumidity());

            response = getData("/query/info");
            infoData = new Gson().fromJson(response, VenstarInfoData.class);
            updateState(new ChannelUID(getThing().getUID(), CHANNEL_HEATING_SETPOINT), getHeatingSetpoint());
            updateState(new ChannelUID(getThing().getUID(), CHANNEL_COOLING_SETPOINT), getCoolingSetpoint());
            updateState(new ChannelUID(getThing().getUID(), CHANNEL_SYSTEM_STATE), getSystemState());
            updateState(new ChannelUID(getThing().getUID(), CHANNEL_SYSTEM_MODE), getSystemMode());

            goOnline();
        } catch (VenstarCommunicationException | JsonSyntaxException e) {
            log.debug("Unable to fetch info data", e);
            goOffline(ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
        } catch (VenstarAuthenticationException e) {
            goOffline(ThingStatusDetail.CONFIGURATION_ERROR, "Authorization Failed");
        }
    }

    private String getData(String path) throws VenstarAuthenticationException, VenstarCommunicationException {
        try {
            URL getURL = new URL(baseURL, path);
            Request request = httpClient.newRequest(getURL.toURI()).timeout(TIMEOUT, TimeUnit.SECONDS);
            return sendRequest(request);
        } catch (MalformedURLException | URISyntaxException e) {
            throw new VenstarCommunicationException(e);
        }
    }

    private String postData(String path, Map<String, String> params)
            throws VenstarAuthenticationException, VenstarCommunicationException {
        try {
            URL postURL = new URL(baseURL, path);
            Request request = httpClient.newRequest(postURL.toURI()).timeout(TIMEOUT, TimeUnit.SECONDS)
                    .method(HttpMethod.POST);
            params.forEach((k, v) -> {
                request.param(k, v);
            });
            return sendRequest(request);
        } catch (MalformedURLException | URISyntaxException e) {
            throw new VenstarCommunicationException(e);
        }
    }

    private String sendRequest(Request request) throws VenstarAuthenticationException, VenstarCommunicationException {
        log.trace("sendRequest: requesting {}", request.getURI());
        try {
            ContentResponse response = request.send();
            if (response.getStatus() == 401) {
                throw new VenstarAuthenticationException();
            }

            if (response.getStatus() != 200) {
                throw new VenstarCommunicationException(
                        "Error communitcating with thermostat. Error Code: " + response.getStatus());
            }
            String content = response.getContentAsString();
            log.trace("sendRequest: response {}", content);
            return content;
        } catch (InterruptedException | TimeoutException | ExecutionException e) {
            throw new VenstarCommunicationException(e);

        }
    }

    @SuppressWarnings("serial")
    private class VenstarAuthenticationException extends Exception {
        public VenstarAuthenticationException() {
            super("Invalid Credentials");
        }
    }

    @SuppressWarnings("serial")
    private class VenstarCommunicationException extends Exception {
        public VenstarCommunicationException(Exception e) {
            super(e);
        }

        public VenstarCommunicationException(String message) {
            super(message);
        }
    }

}
