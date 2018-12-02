/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.io.homekit.internal.accessories;

import org.eclipse.smarthome.core.events.EventPublisher;
import org.eclipse.smarthome.core.items.ItemRegistry;
import org.openhab.io.homekit.internal.HomekitAccessoryUpdater;
import org.openhab.io.homekit.internal.HomekitSettings;
import org.openhab.io.homekit.internal.HomekitTaggedItem;

import com.beowulfe.hap.HomekitAccessory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Creates a HomekitAccessory for a given HomekitTaggedItem.
 *
 * @author Andy Lintner
 */
public class HomekitAccessoryFactory {

    private static Logger logger = LoggerFactory.getLogger(HomekitAccessoryFactory.class);

    public static HomekitAccessory create(HomekitTaggedItem taggedItem, ItemRegistry itemRegistry,
                                          HomekitAccessoryUpdater updater, HomekitSettings settings, EventPublisher eventPublisher) throws Exception {
       switch (taggedItem.getDeviceType()) {
            case LIGHTBULB:
                return new HomekitLightbulbImpl(taggedItem, itemRegistry, updater, eventPublisher);

            case DIMMABLE_LIGHTBULB:
                return new HomekitDimmableLightbulbImpl(taggedItem, itemRegistry, updater, eventPublisher);

            case COLORFUL_LIGHTBULB:
                return new HomekitColorfulLightbulbImpl(taggedItem, itemRegistry, updater, eventPublisher);

            case THERMOSTAT:
                return new HomekitThermostatImpl(taggedItem, itemRegistry, updater, settings, eventPublisher);

            case SWITCH:
                return new HomekitSwitchImpl(taggedItem, itemRegistry, updater, eventPublisher);

            case TEMPERATURE_SENSOR:
                return new HomekitTemperatureSensorImpl(taggedItem, itemRegistry, updater, settings, eventPublisher);

            case HUMIDITY_SENSOR:
                return new HomekitHumiditySensorImpl(taggedItem, itemRegistry, updater, eventPublisher);

           case FAN:
               return new HomekitFanImpl(taggedItem, itemRegistry, updater, settings, eventPublisher);
        }

        throw new Exception("Unknown homekit type: " + taggedItem.getDeviceType());
    }
}
