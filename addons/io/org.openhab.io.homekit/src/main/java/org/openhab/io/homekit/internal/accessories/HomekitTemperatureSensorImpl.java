/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.io.homekit.internal.accessories;

import java.util.concurrent.CompletableFuture;

import com.beowulfe.hap.accessories.properties.TemperatureUnit;
import org.eclipse.smarthome.core.events.EventPublisher;
import org.eclipse.smarthome.core.items.ItemRegistry;
import org.eclipse.smarthome.core.library.items.NumberItem;
import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.library.types.QuantityType;
import org.eclipse.smarthome.core.library.unit.SIUnits;
import org.eclipse.smarthome.core.library.unit.SmartHomeUnits;
import org.eclipse.smarthome.core.types.State;
import org.openhab.io.homekit.internal.HomekitAccessoryUpdater;
import org.openhab.io.homekit.internal.HomekitSettings;
import org.openhab.io.homekit.internal.HomekitTaggedItem;

import com.beowulfe.hap.HomekitCharacteristicChangeCallback;
import com.beowulfe.hap.accessories.TemperatureSensor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.measure.Unit;

/**
 * Implements a Homekit TemperatureSensor using a NumberItem
 *
 * @author Andy Lintner
 */


class HomekitTemperatureSensorImpl extends AbstractTemperatureHomekitAccessoryImpl<NumberItem>
        implements TemperatureSensor {

    private final Logger logger = LoggerFactory.getLogger(HomekitTemperatureSensorImpl.class);

    public HomekitTemperatureSensorImpl(HomekitTaggedItem taggedItem, ItemRegistry itemRegistry,
                                        HomekitAccessoryUpdater updater, HomekitSettings settings, EventPublisher eventPublisher) {
        super(taggedItem, itemRegistry, updater, eventPublisher, settings, NumberItem.class);
    }

    @Override
    public CompletableFuture<Double> getCurrentTemperature() {
        State s = getItem().getState();

        if(s instanceof QuantityType)
            return CompletableFuture.completedFuture(toUnit(s, SIUnits.CELSIUS));

        DecimalType state = getItem().getStateAs(DecimalType.class);
        return CompletableFuture.completedFuture(convertToCelsius(state.doubleValue()));
    }

    @Override
    public void subscribeCurrentTemperature(HomekitCharacteristicChangeCallback callback) {
        getUpdater().subscribe(getItem(), callback);
    }

    @Override
    public void unsubscribeCurrentTemperature() {
        getUpdater().unsubscribe(getItem());
    }
}
