/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.io.homekit.internal.accessories;

import com.beowulfe.hap.HomekitCharacteristicChangeCallback;
import com.beowulfe.hap.accessories.Fan;
import com.beowulfe.hap.accessories.properties.RotationDirection;
import org.eclipse.smarthome.core.events.EventPublisher;
import org.eclipse.smarthome.core.items.GenericItem;
import org.eclipse.smarthome.core.items.GroupItem;
import org.eclipse.smarthome.core.items.Item;
import org.eclipse.smarthome.core.items.ItemRegistry;
import org.eclipse.smarthome.core.items.events.ItemCommandEvent;
import org.eclipse.smarthome.core.items.events.ItemEventFactory;
import org.eclipse.smarthome.core.library.items.NumberItem;
import org.eclipse.smarthome.core.library.items.SwitchItem;
import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.PercentType;
import org.eclipse.smarthome.core.library.types.QuantityType;
import org.eclipse.smarthome.core.library.unit.SIUnits;
import org.eclipse.smarthome.core.types.State;
import org.openhab.io.homekit.internal.HomekitAccessoryUpdater;
import org.openhab.io.homekit.internal.HomekitCharacteristicType;
import org.openhab.io.homekit.internal.HomekitSettings;
import org.openhab.io.homekit.internal.HomekitTaggedItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.measure.quantity.Temperature;
import java.util.concurrent.CompletableFuture;

import static com.beowulfe.hap.accessories.properties.RotationDirection.CLOCKWISE;
import static com.beowulfe.hap.accessories.properties.RotationDirection.COUNTER_CLOCKWISE;

/**
 * Implements Fan as a GroupedAccessory made up of multiple items:
 * <ul>
 * <li>Speed: Decimal type</li>
 * <li>Power: Boolean type</li>
 * <li>Direction: Boolean type</li>
 * </ul>
 *
 * @author Andy Lintner
 */
class HomekitFanImpl extends AbstractHomekitAccessoryImpl<GroupItem>
        implements Fan, GroupedAccessory {

    private final String groupName;
    private final HomekitSettings settings;
    private String powerItemName;
    private String speedItemName;
    private String directionItemName;

    private Logger logger = LoggerFactory.getLogger(HomekitFanImpl.class);

    public HomekitFanImpl(HomekitTaggedItem taggedItem, ItemRegistry itemRegistry,
                          HomekitAccessoryUpdater updater, HomekitSettings settings, EventPublisher eventPublisher) {
        super(taggedItem, itemRegistry, updater, eventPublisher, GroupItem.class);
        this.groupName = taggedItem.getItem().getName();
        this.settings = settings;
    }

    @Override
    public String getGroupName() {
        return groupName;
    }

    @Override
    public void addCharacteristic(HomekitTaggedItem item) {
        switch (item.getCharacteristicType()) {
            case ROTATION_DIRECTION:
                directionItemName = item.getItem().getName();
                break;

            case FAN_POWER:
                powerItemName = item.getItem().getName();
                break;

            case FAN_SPEED:
                speedItemName = item.getItem().getName();
                break;

            default:
                logger.error("Unrecognized thermostat characteristic: {}", item.getCharacteristicType().name());
                break;

        }
    }

    @Override
    public boolean isComplete() {
        return powerItemName != null && speedItemName != null
                && directionItemName != null;
    }

    @SuppressWarnings("unchecked")
    private <T extends SwitchItem> T getSwitchItem(String name) {
        Item item = getItemRegistry().get(name);
        if (item == null) {
            return null;
        }
        if (!(item instanceof SwitchItem)) {
            throw new RuntimeException("Expected SwitchItem, found " + item.getClass().getCanonicalName());
        }
        return (T) item;
    }

    @SuppressWarnings("unchecked")
    private <T extends GenericItem> T getGenericItem(String name) {
        Item item = getItemRegistry().get(name);
        if (item == null) {
            return null;
        }
        if (!(item instanceof GenericItem)) {
            throw new RuntimeException("Expected GenericItem, found " + item.getClass().getCanonicalName());
        }
        return (T) item;
    }


    @Override
    public CompletableFuture<Boolean> getFanPower() {
        if (powerItemName != null) {
            Item item = getItemRegistry().get(powerItemName);
            State s = item.getState();
            OnOffType val = s.as(OnOffType.class);
            return CompletableFuture.completedFuture(OnOffType.ON==val?true:false);
        }

        else return CompletableFuture.completedFuture(false);
    }

    // TODO openhab doesn't really have a type that reflects a direction of rotation. so, we assume that
    // ON=COUNTER CLOCKWISE and OFF=CLOCKWISE, which corresponds with the HomeKit internal representation
    // of 1 and 0 respectively.
    @Override
    public CompletableFuture<RotationDirection> getRotationDirection() {
        if (powerItemName != null) {
            Item item = getItemRegistry().get(powerItemName);
            State s = item.getState();
            OnOffType val = s.as(OnOffType.class);
            return CompletableFuture.completedFuture(OnOffType.ON==val?COUNTER_CLOCKWISE:CLOCKWISE);
        }

        else return CompletableFuture.completedFuture(CLOCKWISE);
    }

    @Override
    public CompletableFuture<Integer> getRotationSpeed() {
        if (speedItemName != null) {
            Item item = getItemRegistry().get(speedItemName);
            State s = item.getState();

            if (s == null) {
                return CompletableFuture.completedFuture(null);
            }

            PercentType val = s.as(PercentType.class);

            return CompletableFuture.completedFuture(val.intValue());
        } else {
            return CompletableFuture.completedFuture(null);
        }
    }

    @Override
    public CompletableFuture<Void> setFanPower(boolean b) throws Exception {
        SwitchItem item = getSwitchItem(powerItemName);
        ItemCommandEvent command = ItemEventFactory.createCommandEvent(item.getName(),
                b?OnOffType.ON:OnOffType.OFF);

//        logger.warn("command: " + command);
//        logger.warn("publisher: " + eventPublisher);
        eventPublisher.post(command);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> setRotationDirection(RotationDirection rotationDirection) throws Exception {
        SwitchItem item = getSwitchItem(directionItemName);
        ItemCommandEvent command = ItemEventFactory.createCommandEvent(item.getName(),
                (rotationDirection.equals(COUNTER_CLOCKWISE)?OnOffType.ON:OnOffType.OFF));
//       logger.warn("command: " + command);
//        logger.warn("publisher: " + eventPublisher);
        eventPublisher.post(command);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> setRotationSpeed(Integer integer) throws Exception {

        Item item = getItemRegistry().get(speedItemName);

        ItemCommandEvent command = ItemEventFactory.createCommandEvent(item.getName(),
                new PercentType(integer));

//        logger.warn("command: " + command);
//        logger.warn("publisher: " + eventPublisher);
        eventPublisher.post(command);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void subscribeFanPower(HomekitCharacteristicChangeCallback callback) {
        getUpdater().subscribe(getGenericItem(powerItemName), callback);
    }

    @Override
    public void subscribeRotationDirection(HomekitCharacteristicChangeCallback callback) {
        getUpdater().subscribe(getGenericItem(directionItemName), callback);
    }

    @Override
    public void subscribeRotationSpeed(HomekitCharacteristicChangeCallback callback) {
        getUpdater().subscribe(getGenericItem(speedItemName), callback);
    }

    @Override
    public void unsubscribeFanPower() {
        getUpdater().unsubscribe(getGenericItem(powerItemName));
    }

    @Override
    public void unsubscribeRotationDirection() {
        getUpdater().unsubscribe(getGenericItem(directionItemName));
    }

    @Override
    public void unsubscribeRotationSpeed() {
        getUpdater().unsubscribe(getGenericItem(speedItemName));
    }
}
