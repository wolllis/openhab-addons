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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

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
    private ScheduledFuture<?> deviceDiscoveryJob;
    private Boolean DeviceDiscoveryThreadIsNotCanceled;

    // Our own thread pool for the long-running listener job
    private ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(1);

    public EltakoDeviceDiscoveryService(EltakoBridgeHandler bridgeHandler) {
        super(null, 60, false);
        this.bridgeHandler = bridgeHandler;
        DeviceDiscoveryThreadIsNotCanceled = false;
    }

    /**
     * Called on component activation.
     */
    public void activate() {
        super.activate(null);
        logger.debug("Aktivate Device Discovery Service");
    }

    @Override
    public void deactivate() {
        super.deactivate();
        logger.debug("Deaktivate Device Discovery Service");

        // Cancel Device Discovery Thread
        if (deviceDiscoveryJob != null) {
            logger.debug("Canceling DeviceDiscoveryThread");
            // SerialPollingJob.cancel(true);
            DeviceDiscoveryThreadIsNotCanceled = false;
            while (!deviceDiscoveryJob.isDone()) {
                ;
            }
            deviceDiscoveryJob = null;
            DeviceDiscoveryThreadIsNotCanceled = null;
        }
    }

    /**
     * Device scan should be started => Inform bridge about it
     */
    @Override
    protected void startScan() {
        logger.debug("Starting Eltako discovery scan");

        // Start background thread for discovery of devices
        deviceDiscoveryJob = scheduledExecutorService.schedule(DeviceDiscoveryThread, 1, TimeUnit.SECONDS);
        // Set Discovery Thread exit condition
        DeviceDiscoveryThreadIsNotCanceled = true;
    }

    /**
     * Device scan should be stopped => Inform bridge about it
     */
    @Override
    public synchronized void stopScan() {
        super.stopScan();
        logger.debug("Stopping Eltako discovery scan");

        // Cancel Device Discovery Thread
        if (deviceDiscoveryJob != null) {
            logger.debug("Canceling DeviceDiscoveryThread");
            // SerialPollingJob.cancel(true);
            DeviceDiscoveryThreadIsNotCanceled = false;
            while (!deviceDiscoveryJob.isDone()) {
                ;
            }
            deviceDiscoveryJob = null;
            DeviceDiscoveryThreadIsNotCanceled = null;
        }
    }

    /**
     * Called by framework in order to get supported thing types
     */
    @Override
    public Set<@NonNull ThingTypeUID> getSupportedThingTypes() {
        logger.debug("Get supported thing types");
        return SUPPORTED_DEVICE_THING_TYPES_UIDS;
    }

    /**
     * Thread is executed for the time devices should be discovered
     */
    protected Runnable DeviceDiscoveryThread = () -> {
        // Log event to console
        logger.debug("DeviceDiscoveryThread started");

        // Create example device
        this.createdevice();

        int[] message = new int[14];
        int[] data = new int[] { 0, 0, 0, 0 };
        int[] id = new int[] { 0, 0, 0, 0 };

        for (int i = 0; i < 256; i++) {
            // Check if Thread should end
            if (!DeviceDiscoveryThreadIsNotCanceled) {
                break;
            }
            if (this.bridgeHandler == null) {
                // Log event to console
                logger.debug("Bridge instance not available => end scan");
                break;
            }
            // Force FAM14 into config mode
            if (i == 0) {
                // - Set FAM14 into config mode (A5 5A AB FF 00 00 00 00 00 00 00 00 FF A9)
                this.bridgeHandler.constuctMessage(message, 5, 0xff, data, id, 0xFF);
                // Log event to console
                logger.debug("DiscoveryService: Force FAM14 into config mode");
            } else if (i == 255) {
                // Force FAM14 into telegram mode (a5 5a ab ff 00 00 00 00 00 00 00 00 00 aa)
                this.bridgeHandler.constuctMessage(message, 5, 0xff, data, id, 0x00);
                // Log event to console
                logger.debug("DiscoveryService: Force FAM14 into telegram mode");
            } else {
                // Scan for ID (a5 5a ab f0 00 00 00 00 00 00 00 00 02 9d => ID2)
                this.bridgeHandler.constuctMessage(message, 5, 0xf0, data, id, i);
                // Log event to console
                logger.debug("DiscoveryService: Search for device with ID {}", i);
            }
            this.bridgeHandler.serialWrite(message, 14);

            // Wait some time
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                logger.error("Sleep does not work in DeviceDiscoveryThread: {}", e);
            }
        }
        // Log event to console
        logger.debug("DeviceDiscoveryThread ended");
    };

    public void createdevice() {
        // Create instance of new thing including needed property's
        ThingTypeUID thingTypeUID = new ThingTypeUID(BINDING_ID, "FUD14");
        ThingUID thingUID = new ThingUID(thingTypeUID, bridgeHandler.getThing().getUID(), "00000001");
        // Create result (thing)
        DiscoveryResultBuilder discoveryResultBuilder = DiscoveryResultBuilder.create(thingUID)
                .withBridge(bridgeHandler.getThing().getUID());
        // Set some thing specific propertys
        discoveryResultBuilder.withProperty(PROPERTY_DEVICE_ID, "00000001");
        discoveryResultBuilder.withProperty(FUD14_ID, "00000001");
        // Add thing to discovery result list
        thingDiscovered(discoveryResultBuilder.build());
    }
}
