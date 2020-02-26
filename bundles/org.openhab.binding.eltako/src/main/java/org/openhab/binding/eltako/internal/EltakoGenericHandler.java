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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.library.types.PercentType;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusInfo;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandler;
import org.eclipse.smarthome.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link EltakoGenericHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Martin Wenske - Initial contribution
 */
@NonNullByDefault
public class EltakoGenericHandler extends BaseThingHandler implements EltakoTelegramListener {

    /*
     * Logger instance to create log entries
     */
    private final Logger logger = LoggerFactory.getLogger(EltakoGenericHandler.class);

    /**
     * Channel variables
     */
    private int deviceId;

    /**
     * Initializer method
     */
    public EltakoGenericHandler(Thing thing) {
        super(thing);
        deviceId = 0;
    }

    /**
     * Called by framework after creation of thing
     */
    @Override
    public void initialize() {

        // Acquire device ID from thing configuration (set by the user)
        // this.deviceId = Integer.parseInt(getThing().getConfiguration().get(GENERIC_DEVICE_ID).toString(), 16);
        // Update thing property
        updateProperty(GENERIC_HARDWARE_VERSION, "Unknown");

        // Set thing status to UNKNOWN
        this.updateStatus(ThingStatus.UNKNOWN);

        EltakoBridgeHandler bridgeHandle = this.getMyBridgeHandle();
        if (bridgeHandle != null) {
            // Listen for a specific ID so received telegrams are forwarded to thing (Ignore 4th byte)
            bridgeHandle.addPacketListener(this,
                    Integer.parseInt(getThing().getConfiguration().get(GENERIC_DEVICE_ID).toString()) & 0xFFFFFF);
            // Set thing status depending on status of bridge
            Thing bridge = this.getMyBridge();
            if (bridge != null) {
                if (bridge.getStatus() != ThingStatus.ONLINE) {
                    // Set thing status to offline
                    this.updateStatus(ThingStatus.OFFLINE);
                } else {
                    // Set thing status to offline
                    this.updateStatus(ThingStatus.ONLINE);
                    // TODO: Check communication to thing first before setting it to ONLINE
                }

            }
        }

        logger.debug("Finished initializing of thing handler");
    }

    /**
     * Is called in case the bridge is changing its state
     */
    @Override
    public void bridgeStatusChanged(ThingStatusInfo bridgeStatusInfo) {
        // Log event to console
        logger.debug("bridgeStatusChanged => {}", bridgeStatusInfo);
        if (bridgeStatusInfo.getStatus() == ThingStatus.ONLINE) {
            // Set thing status to unknown
            this.updateStatus(ThingStatus.ONLINE);
        }
        if (bridgeStatusInfo.getStatus() == ThingStatus.OFFLINE) {
            // Set thing status to unknown
            this.updateStatus(ThingStatus.UNKNOWN);
        }
    }

    /**
     * Bridge handler getter
     */
    protected @Nullable EltakoBridgeHandler getMyBridgeHandle() {
        Thing bridge = this.getBridge();
        if (bridge != null) {
            EltakoBridgeHandler bridgeHandle = (EltakoBridgeHandler) bridge.getHandler();
            if (bridgeHandle != null) {
                // Return
                return bridgeHandle;
            } else {
                // Log event to console
                logger.error("BridgeHandle not available for thing");
                return null;
            }
        } else {
            // Log event to console
            logger.error("Bridge not available for thing");
            return null;
        }
    }

    /**
     * Bridge getter
     */
    protected @Nullable Thing getMyBridge() {
        Thing bridge = this.getBridge();
        if (bridge != null) {
            // Return
            return bridge;
        } else {
            // Log event to console
            logger.error("BridgeHandle not available for thing");
            return null;
        }
    }

    /**
     * Called by Bridge when a new telegram has been received
     */
    @Override
    public void telegramReceived(int[] packet) {
        // Prepare data to be written to log
        StringBuffer strbuf = new StringBuffer();
        // Create string out of byte data
        for (int i = 0; i < 14; i++) {
            strbuf.append(String.format("%02X ", packet[i]));
        }
        // Log event to console
        logger.trace("GENERIC: Telegram Received: {}", strbuf);

        if ((packet[11] | (packet[10] << 8) | (packet[9] << 16)) == (deviceId & 0xFFFFFF)) {
            updateState(CHANNEL_BRIGHTNESS, PercentType.valueOf(String.valueOf(packet[5])));
            updateState(CHANNEL_SPEED, DecimalType.valueOf(String.valueOf(packet[6])));
            // updateState(CHANNEL_POWER, OnOffType.from(Integer.packet[7])));
            // updateState(CHANNEL_BLOCKING, OnOffType.from(Integer.packet[7])));
        }
    }

    /**
     * Event handler is called in case a channel has received a command
     */
    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
    }

    /**
     * Last method called before thing will be destroyed
     */
    @Override
    public void dispose() {
        logger.debug("Dispose thing instance");

        EltakoBridgeHandler bridgeHandle = this.getMyBridgeHandle();
        if (bridgeHandle != null) {
            // Listen for a specific ID so received telegrams are forwarded to thing
            bridgeHandle.removePacketListener(this, deviceId);
        }
    }

}
