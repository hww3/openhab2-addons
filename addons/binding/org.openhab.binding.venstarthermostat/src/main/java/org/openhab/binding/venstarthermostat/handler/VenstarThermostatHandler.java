/**
 * Copyright (c) 2014-2016 by the respective copyright holders.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.venstarthermostat.handler;

import static org.openhab.binding.venstarthermostat.VenstarThermostatBindingConstants.*;

import java.io.IOException;
import java.math.BigDecimal;
//import java.net.URISyntaxException;
//import java.net.URL;
//import java.security.KeyManagementException;
//import java.security.NoSuchAlgorithmException;
//import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.ws.rs.core.UriBuilder;

import org.apache.http.HttpEntity;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.Authentication;
import org.eclipse.jetty.client.api.AuthenticationStore;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.util.BasicAuthentication;
import org.eclipse.jetty.client.util.DigestAuthentication;
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
 */
public class VenstarThermostatHandler extends ConfigStatusThingHandler {

    private static final int TIMEOUT = 30;
    private Logger log = LoggerFactory.getLogger(VenstarThermostatHandler.class);
    ScheduledFuture<?> refreshJob;
    private BigDecimal refresh;
    private List<VenstarSensor> sensorData = new ArrayList<>();
    private VenstarInfoData infoData = new VenstarInfoData();
    private Future<?> initializeTask;
    private Future<?> updatesTask;
    private boolean shouldRunUpdates = false;
    private VenstarThermostatConfiguration config;
    private HttpClient httpClient;

    public VenstarThermostatHandler(Thing thing) {
        super(thing);
        httpClient = new HttpClient(true);
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
        if (channelUID.getId().equals(CHANNEL_HEATING_SETPOINT)) {
            // Note: if communication with thing fails for some reason,
            // indicate that by setting the status with detail information
            // updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
            // "Could not control device at IP address x.x.x.x");
            log.debug("Setting heating setpoint to " + command.toFullString());
            setHeatingSetpoint(((DecimalType) command).intValue());

        }
        if (channelUID.getId().equals(CHANNEL_COOLING_SETPOINT)) {
            log.debug("Setting cooling setpoint to " + command.toFullString());
            setCoolingSetpoint(((DecimalType) command).intValue());

        }
        if (channelUID.getId().equals(CHANNEL_SYSTEM_MODE)) {
            log.debug("Setting system mode to " + command.toFullString());
            setSystemMode(((DecimalType) command).intValue());

        }
    }

    public void updateUrl(String url) {
        Map<String, String> props = editProperties();
        props.put(VenstarThermostatBindingConstants.PROPERTY_URL, url);
        updateProperties(props);
        thingUpdated(getThing());
    }

    @Override
    public void initialize() {
        config = getConfigAs(VenstarThermostatConfiguration.class);
        stopHttpClient();
        httpClient.getAuthenticationStore().addAuthentication(new DigestAuthentication(config.url,"*", config.getUsername(), config.getPassword());
        startHttpClient();
        scheduleCheckCommunication(1);
    }

    protected void checkCommunication() {

        HttpResponse response = null;
        try {
            response = getConnection();
            log.debug("got response from venstar: " + response.getStatusLine());
        } catch (Throwable e) {
            log.warn("communication error: " + e.getMessage(), e);
            goOffline(ThingStatusDetail.COMMUNICATION_ERROR,
                    "Failed to connect to URL (" + url.toString() + "): " + e.getMessage());
            return;
        }

        if (response.getStatusLine().getStatusCode() == 401 || response.getStatusLine().getStatusCode() == 403) {
            goOffline(ThingStatusDetail.CONFIGURATION_ERROR, "Invalid Credentials");
            return;
        } else if (response.getStatusLine().getStatusCode() != 200) {
            goOffline(ThingStatusDetail.COMMUNICATION_ERROR,
                    "Unexpected response: " + response.getStatusLine().getStatusCode());
            return;
        }

        log.debug("setting online");
        goOnline();
        return;
    }

    protected void scheduleCheckCommunication(int seconds) {

        log.info("running communication check in {} seconds", seconds);
        Future<?> initializeTask1 = scheduler.schedule(new Runnable() {
            @Override
            public void run() {
                checkCommunication();
            }
        }, seconds, TimeUnit.SECONDS);

        // only one initialization task at a time, please.
        Future<?> i = initializeTask;
        initializeTask = initializeTask1;
        if (i != null && !i.isDone()) {
            i.cancel(true);
        }
    }

    protected void goOnline() {
        // we don't need to check communications if we're online already.
        // only one initialization task at a time, please.
        if (initializeTask != null && !initializeTask.isDone()) {
            initializeTask.cancel(true);
        }

        if (updatesTask != null && !updatesTask.isDone()) {
            updatesTask.cancel(true);
        }
        shouldRunUpdates = true;
        updateStatus(ThingStatus.ONLINE);
        startUpdatesTask();
    }

    protected void goOffline(ThingStatusDetail detail, String reason) {
        if (updatesTask != null && !updatesTask.isDone()) {
            updatesTask.cancel(true);
        }

        shouldRunUpdates = false;
        updateStatus(ThingStatus.OFFLINE, detail, reason);
        scheduleCheckCommunication(15);
    }

    @Override
    public void dispose() {
        if (refreshJob != null) {
            refreshJob.cancel(true);
        }
        if (updatesTask != null) {
            updatesTask.cancel(true);
        }
    }

    private void fetchUpdate() {
        try {
            log.debug("running refresh");
            boolean success = updateSensorData();
            if (success) {
                updateState(new ChannelUID(getThing().getUID(), CHANNEL_TEMPERATURE), getTemperature());
                updateState(new ChannelUID(getThing().getUID(), CHANNEL_EXTERNAL_TEMPERATURE), getOutdoorTemperature());
                updateState(new ChannelUID(getThing().getUID(), CHANNEL_HUMIDITY), getHumidity());
            }
        } catch (Exception e) {
            log.debug("Exception occurred during execution: {}", e.getMessage(), e);
        }

        try {
            log.debug("updating info");
            boolean success = updateInfoData();
            if (success) {
                updateState(new ChannelUID(getThing().getUID(), CHANNEL_HEATING_SETPOINT), getHeatingSetpoint());
                updateState(new ChannelUID(getThing().getUID(), CHANNEL_COOLING_SETPOINT), getCoolingSetpoint());
                updateState(new ChannelUID(getThing().getUID(), CHANNEL_SYSTEM_STATE), getSystemState());
                updateState(new ChannelUID(getThing().getUID(), CHANNEL_SYSTEM_MODE), getSystemMode());
            }
        } catch (Exception e) {
            log.debug("Exception occurred during execution: {}", e.getMessage(), e);
        }
    }

    private void startUpdatesTask() {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                fetchUpdate();
                if (shouldRunUpdates) {
                    startUpdatesTask();
                }
            }
        };

        updatesTask = scheduler.schedule(runnable, refresh.intValue(), TimeUnit.SECONDS);
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

        log.debug("Updating thermostat {}  heat:{} cool {} mode: ", getThing().getLabel(), heat, cool, mode);
        if (heat > 0) {
            params.put("heattemp", String.valueOf(heat));
        }
        if (cool > 0) {
            params.put("cooltemp", "" + String.valueOf(cool));
        }
        params.put("mode", String.valueOf(mode));

        try {
            HttpResponse result = postConnection("/control", params);
            if (log.isTraceEnabled()) {
                log.trace("Result from theromstat: " + result.getStatusLine().toString());
                log.trace("Result from theromstat: ");
            }
            if (result.getStatusLine().getStatusCode() == 401) {
                goOffline(ThingStatusDetail.CONFIGURATION_ERROR, "Invalid credentials");
                log.info("Failed to update thermostat: invalid credentials");
                return;
            }

            HttpEntity entity = result.getEntity();
            String data = EntityUtils.toString(entity, "UTF-8");
            if (log.isTraceEnabled()) {
                log.trace("Result from theromstat: " + data);
            }

            VenstarResponse res = new Gson().fromJson(data, VenstarResponse.class);
            if (res.isSuccess()) {
                log.info("Updated thermostat");
            } else {
                log.info("Failed to update: " + res.getReason());
                goOffline(ThingStatusDetail.COMMUNICATION_ERROR, "Thermostat update failed: " + res.getReason());
            }

        } catch (IOException | JsonSyntaxException e) {
            log.info("Failed to update thermostat: " + e.getMessage());
            goOffline(ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
            return;
        }

    }

    private boolean updateSensorData() {
        try {
            String sensorData = getData("/query/sensors");
            if (log.isTraceEnabled()) {
                log.trace("got sensordata from thermostat: " + sensorData);
            }

            VenstarSensorData res = new Gson().fromJson(sensorData, VenstarSensorData.class);

            this.sensorData = res.getSensors();
            return true;
        } catch (Exception e) {

            log.debug("Unable to fetch url '{}': {}", url, e.getMessage());
            return false;
        }
    }

    private boolean updateInfoData() {
        try {

            String infoData = getData("/query/info");
            if (log.isTraceEnabled()) {
                log.trace("got info from thermostat: " + infoData);
            }
            VenstarInfoData id = new Gson().fromJson(infoData, VenstarInfoData.class);
            if (id != null) {
                this.infoData = id;
            }
            return true;
        } catch (Exception e) {
            log.debug("Unable to fetch url '{}': {}", url, e.getMessage());
            return false;
        }
    }

    String getData(String path) {
        try {

            Request request = httpClient.newRequest(config.getUrl() + path).timeout(TIMEOUT, TimeUnit.SECONDS);
            ContentResponse response;
            response = request.send();
            if (response.getStatus() == 401) {
                goOffline(ThingStatusDetail.CONFIGURATION_ERROR, "Invalid credentials");
                return null;
            }

            if (response.getStatus() != 200) {
                goOffline(ThingStatusDetail.COMMUNICATION_ERROR,
                        "Error communitcating with thermostat. Error Code: " + response.getStatus());
                return null;
            }
            return response.getContentAsString();
        } catch (InterruptedException | TimeoutException | ExecutionException e) {
            log.warn("failed to open connection: {}", e.getMessage());
            goOffline(ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
            return null;
        }

    }

    HttpResponse postConnection(String path, Map<String, String> params) throws IOException {
        DefaultHttpClient httpclient = buildHttpClient();

        UriBuilder builder = UriBuilder.fromUri(getThing().getProperties().get(PROPERTY_URL));
        if (path != null) {
            builder.path(path);
        }

        HttpPost post = new HttpPost(builder.build());
        List<NameValuePair> urlParameters = new ArrayList<NameValuePair>();

        for (Entry<String, String> ent : params.entrySet()) {
            if (log.isTraceEnabled()) {
                log.trace("setting " + ent.getKey() + ": " + ent.getValue());
            }
            urlParameters.add(new BasicNameValuePair(ent.getKey(), ent.getValue()));
        }
        post.setEntity(new UrlEncodedFormEntity(urlParameters));

        HttpResponse response = httpclient.execute(post);

        return response;
    }

    private void startHttpClient() {
        if (!httpClient.isStarted()) {
            try {
                httpClient.start();
            } catch (Exception e) {
                log.error("Could not stop HttpClient", e);
            }
        }
    }

    private void stopHttpClient() {
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

}
