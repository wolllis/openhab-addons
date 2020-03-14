/**
 * Copyright (c) 2010-2020 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.bluetooth.airthings.internal;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.config.discovery.DiscoveryResult;
import org.eclipse.smarthome.config.discovery.DiscoveryResultBuilder;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.openhab.binding.bluetooth.BluetoothBindingConstants;
import org.openhab.binding.bluetooth.BluetoothDevice;
import org.openhab.binding.bluetooth.discovery.BluetoothDiscoveryParticipant;
import org.osgi.service.component.annotations.Component;

/**
 * This discovery participant is able to recognize Airthings devices and create discovery results for them.
 *
 * @author Pauli Anttila - Initial contribution
 *
 */
@NonNullByDefault
@Component(immediate = true)
public class AirthingsDiscoveryParticipant implements BluetoothDiscoveryParticipant {

    private static final int AIRTHINGS_COMPANY_ID = 820; // Formerly Corentium AS

    private static final String WAVE_PLUS_MODEL = "2930";

    @Override
    public Set<ThingTypeUID> getSupportedThingTypeUIDs() {
        return Collections.singleton(AirthingsBindingConstants.THING_TYPE_AIRTHINGS_WAVE_PLUS);
    }

    @Override
    @Nullable
    public ThingUID getThingUID(BluetoothDevice device) {
        if (isAirthingsDevice(device)) {
            if (WAVE_PLUS_MODEL.equals(device.getModel())) {
                return new ThingUID(AirthingsBindingConstants.THING_TYPE_AIRTHINGS_WAVE_PLUS,
                        device.getAdapter().getUID(), device.getAddress().toString().toLowerCase().replace(":", ""));
            }
        }
        return null;
    }

    @Override
    @Nullable
    public DiscoveryResult createResult(BluetoothDevice device) {
        if (!isAirthingsDevice(device)) {
            return null;
        }
        ThingUID thingUID = getThingUID(device);
        if (thingUID == null) {
            return null;
        }
        if (WAVE_PLUS_MODEL.equals(device.getModel())) {
            return createWavePlus(device, thingUID);
        }
        return null;
    }

    @Override
    public boolean requiresConnection(BluetoothDevice device) {
        return isAirthingsDevice(device);
    }

    private boolean isAirthingsDevice(BluetoothDevice device) {
        Integer manufacturerId = device.getManufacturerId();
        if (manufacturerId != null && manufacturerId == AIRTHINGS_COMPANY_ID) {
            return true;
        }
        return false;
    }

    private DiscoveryResult createWavePlus(BluetoothDevice device, ThingUID thingUID) {
        Map<String, Object> properties = new HashMap<>();
        properties.put(BluetoothBindingConstants.CONFIGURATION_ADDRESS, device.getAddress().toString());
        properties.put(Thing.PROPERTY_VENDOR, "Airthings AS");
        if (device.getSerialNumber() != null) {
            properties.put(Thing.PROPERTY_SERIAL_NUMBER, device.getSerialNumber());
        }
        if (device.getFirmwareRevision() != null) {
            properties.put(Thing.PROPERTY_FIRMWARE_VERSION, device.getFirmwareRevision());
        }
        if (device.getModel() != null) {
            properties.put(Thing.PROPERTY_MODEL_ID, device.getModel());
        }
        if (device.getHardwareRevision() != null) {
            properties.put(Thing.PROPERTY_HARDWARE_VERSION, device.getHardwareRevision());
        }
        if (device.getAddress() != null) {
            properties.put(Thing.PROPERTY_MAC_ADDRESS, device.getAddress().toString());
        }
        if (device.getTxPower() != null) {
            properties.put(BluetoothBindingConstants.PROPERTY_TXPOWER, Integer.toString(device.getTxPower()));
        }

        // Create the discovery result and add to the inbox
        return DiscoveryResultBuilder.create(thingUID).withProperties(properties)
                .withRepresentationProperty(BluetoothBindingConstants.CONFIGURATION_ADDRESS)
                .withBridge(device.getAdapter().getUID()).withLabel("Airthings Wave+").build();
    }
}
