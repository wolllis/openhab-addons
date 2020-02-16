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
package org.openhab.binding.eltako.internal;

import static org.openhab.binding.eltako.internal.EltakoBindingConstants.SUPPORTED_DEVICE_THING_TYPES_UIDS;

import java.util.Set;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.smarthome.config.discovery.AbstractDiscoveryService;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link EnOceanDeviceDiscoveryService} is used to discover Enocean devices and to accept teach in requests.
 *
 * @author Daniel Weber - Initial contribution
 */

public class EltakoDeviceDiscoveryService extends AbstractDiscoveryService {
    private final Logger logger = LoggerFactory.getLogger(EltakoDeviceDiscoveryService.class);

    private EltakoBridgeHandler bridgeHandler;

    public EltakoDeviceDiscoveryService(EltakoBridgeHandler bridgeHandler) {
        super(null, 60, false);
        this.bridgeHandler = bridgeHandler;
    }

    /**
     * Called on component activation.
     */
    public void activate() {
        super.activate(null);
    }

    @Override
    public void deactivate() {
        super.deactivate();
    }

    @Override
    protected void startScan() {
        if (bridgeHandler == null) {
            return;
        }

        logger.debug("Starting Eltako discovery scan");
        bridgeHandler.startDiscovery(this);
    }

    @Override
    public synchronized void stopScan() {
        if (bridgeHandler == null) {
            return;
        }

        logger.debug("Stopping Eltako discovery scan");
        bridgeHandler.stopDiscovery();
        super.stopScan();
    }

    @Override
    public Set<@NonNull ThingTypeUID> getSupportedThingTypes() {
        logger.debug("Get supported thing types");
        return SUPPORTED_DEVICE_THING_TYPES_UIDS;
    }

}
