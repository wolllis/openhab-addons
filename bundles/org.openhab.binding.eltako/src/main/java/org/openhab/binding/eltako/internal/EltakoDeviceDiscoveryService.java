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

import static org.openhab.binding.eltako.internal.EltakoBindingConstants.*;

import java.util.Set;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.smarthome.config.discovery.AbstractDiscoveryService;
import org.eclipse.smarthome.config.discovery.DiscoveryResultBuilder;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link EltakoDeviceDiscoveryService} is used to discover Eltako devices
 *
 * @author Martin Wenske - Initial contribution
 */

public class EltakoDeviceDiscoveryService extends AbstractDiscoveryService {
    private final Logger logger = LoggerFactory.getLogger(EltakoDeviceDiscoveryService.class);

    private EltakoBridgeHandler bridgeHandler;

    public EltakoDeviceDiscoveryService(EltakoBridgeHandler bridgeHandler) {
        super(null, 60, false);
        this.bridgeHandler = bridgeHandler;
        bridgeHandler.setDiscoveryService(this);
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

    /**
     * Device scan should be started => Inform bridge about it
     */
    @Override
    protected void startScan() {
        if (bridgeHandler == null) {
            return;
        }

        logger.debug("Starting Eltako discovery scan");
        bridgeHandler.startDiscovery(this);
    }

    /**
     * Device scan should be stopped => Inform bridge about it
     */
    @Override
    public synchronized void stopScan() {
        if (bridgeHandler == null) {
            return;
        }

        logger.debug("Stopping Eltako discovery scan");
        bridgeHandler.stopDiscovery();
        super.stopScan();
    }

    /**
     * Called by framework in order to get supported thing types
     */
    @Override
    public Set<@NonNull ThingTypeUID> getSupportedThingTypes() {
        logger.debug("Get supported thing types");
        return SUPPORTED_DEVICE_THING_TYPES_UIDS;
    }

    public void createdevice() {
        ThingTypeUID thingTypeUID = new ThingTypeUID(BINDING_ID, "FUD14");
        ThingUID thingUID = new ThingUID(thingTypeUID, bridgeHandler.getThing().getUID(), "Tada");

        DiscoveryResultBuilder discoveryResultBuilder = DiscoveryResultBuilder.create(thingUID)
                .withBridge(bridgeHandler.getThing().getUID());

        thingDiscovered(discoveryResultBuilder.build());
    }
}
