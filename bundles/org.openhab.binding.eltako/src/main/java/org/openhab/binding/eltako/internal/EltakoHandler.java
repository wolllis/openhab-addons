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
import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.PercentType;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandler;
import org.eclipse.smarthome.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link EltakoHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Martin Wenske - Initial contribution
 */
@NonNullByDefault
public class EltakoHandler extends BaseThingHandler {

    /*
     * Logger instance to create log entries
     */
    private final Logger logger = LoggerFactory.getLogger(EltakoHandler.class);

    // private @Nullable ScheduledFuture<?> pollingJob;

    private PercentType brightness;
    private DecimalType speed;

    /*
     * Initializer method
     */
    public EltakoHandler(Thing thing) {
        super(thing);
        brightness = PercentType.ZERO;
        speed = DecimalType.ZERO;
    }

    /*
     * Prepares the data used for the telegram and sends it out
     */
    private void PrepareTelegram(EltakoBridgeHandler bridgehandler) {

        int temp = brightness.intValue();
        int temp_1 = speed.intValue();

        // Log event to console
        logger.debug("Brightness level is {}", temp);
        logger.debug("Speed level is {}", temp_1);

        // Prepare OFF telegram
        byte[] data = new byte[] { (byte) 0xA5, 0x5A, 0x0B, 0x07, 0x02, (byte) temp, (byte) temp_1, 0x09, 0x00, 0x00,
                0x00, 0x03, 0x00, (byte) (32 + temp + temp_1) };

        // Write data by calling bridge handler method
        bridgehandler.write(data, 14);

    }

    /*
     * Event handler is called in case a channel has received a command
     */
    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {

        // Log event to console
        logger.debug("Channel {} received command {} with class {}", channelUID, command, command.getClass());

        // Get bridge instance
        Bridge bridge = this.getBridge();

        // Check for valid bridge instance
        if (bridge != null) {

            // Get bridge handler instance
            EltakoBridgeHandler bridgehandler = (EltakoBridgeHandler) bridge.getHandler();

            // Check for valid bridge handler instance
            if (bridgehandler != null) {

                switch (channelUID.getId()) {

                    case CHANNEL_BRIGHTNESS:
                        if (command instanceof PercentType) {
                            brightness = (PercentType) command;
                            updateState(CHANNEL_BRIGHTNESS, brightness);
                        }
                        if (command instanceof OnOffType) {
                            if (command.equals(OnOffType.OFF)) {
                                brightness = PercentType.ZERO;
                                updateState(CHANNEL_BRIGHTNESS, brightness);
                            }
                        }
                        if (command instanceof OnOffType) {
                            if (command.equals(OnOffType.ON)) {
                                brightness = PercentType.HUNDRED;
                                updateState(CHANNEL_BRIGHTNESS, brightness);
                            }
                        }
                        break;
                    case CHANNEL_SPEED:
                        if (command instanceof DecimalType) {
                            speed = (DecimalType) command;
                        }
                        break;
                    case CHANNEL_POWER:
                    case CHANNEL_BLOCKING:
                    default:
                        // Log event to console
                        logger.debug("Command {} is not supported by thing", command);
                        break;
                }
                PrepareTelegram(bridgehandler);
            } else {
                // Set thing status to OFFLINE
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_UNINITIALIZED,
                        "No valid bridge handler instance available");
            }
        } else {
            // Set thing status to OFFLINE
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_UNINITIALIZED,
                    "No valid bridge instance available");
        }
    }

    @Override
    public void initialize() {

        // Update vendor property
        updateProperty(PROPERTY_VENDOR, "Eltako");
        updateProperty(PROPERTY_ID, "I dont care");
        updateProperty(PROPERTY_PROTOCOL, "Serial and EnOcean (I think)");

        // Set bridge status to UNKNOWN (always good practice)
        updateStatus(ThingStatus.UNKNOWN);

        // Example for background initialization:
        scheduler.execute(() -> {
            boolean thingReachable = true; // <background task with long running initialization here>
            // when done do:
            if (thingReachable) {
                updateStatus(ThingStatus.ONLINE);
            } else {
                updateStatus(ThingStatus.OFFLINE);
            }
        });

        logger.debug("Finished initializing of thing handler");
    }

    @Override
    public void dispose() {
        logger.debug("Dispose thing instance");
    }
}
