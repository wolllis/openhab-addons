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

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.openhab.binding.eltako.internal.EltakoBindingConstants.*;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandler;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.RefreshType;
import org.eclipse.smarthome.core.library.types.OnOffType;
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

    private final Logger logger = LoggerFactory.getLogger(EltakoHandler.class);
    private @Nullable EltakoConfiguration config;
    private @Nullable ScheduledFuture<?> pollingJob;
    public EltakoHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command)
    {
        switch (channelUID.getId())
        {
            case CHANNEL_1:
                if (command instanceof RefreshType)
                {
                    // TODO: handle data refresh
                }


                // TODO: handle command

                // Note: if communication with thing fails for some reason,
                // indicate that by setting the status with detail information:
                // updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                // "Could not control device at IP address x.x.x.x");

                if(command instanceof OnOffType)
                {
                    logger.info("Got OnOffType!");
                    //State state = (OnOffType) command;
                    if (command.equals(OnOffType.ON))
                        updateState(CHANNEL_1, OnOffType.OFF);
                    if (command.equals(OnOffType.OFF))
                        updateState(CHANNEL_1, OnOffType.OFF);
                }
                break;
                // ...
        }

        logger.info("Command received: {}, {}", channelUID, command);
        logger.info("For channel: {}", channelUID.getId());
    }

    @Override
    public void initialize() {
        logger.info("Start initializing!");
        config = getConfigAs(EltakoConfiguration.class);

        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                // execute some binding specific polling code
                logger.info("Some scheduled stuff!");
            }
        };

        logger.info("Creating scheduler");
        pollingJob = scheduler.scheduleWithFixedDelay(runnable, 0, 1, TimeUnit.SECONDS);

        // TODO: Initialize the handler.
        // The framework requires you to return from this method quickly. Also, before leaving this method a thing
        // status from one of ONLINE, OFFLINE or UNKNOWN must be set. This might already be the real thing status in
        // case you can decide it directly.
        // In case you can not decide the thing status directly (e.g. for long running connection handshake using WAN
        // access or similar) you should set status UNKNOWN here and then decide the real status asynchronously in the
        // background.

        // set the thing status to UNKNOWN temporarily and let the background task decide for the real status.
        // the framework is then able to reuse the resources from the thing handler initialization.
        // we set this upfront to reliably check status updates in unit tests.
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

        // Update vendor property
        updateProperty(Thing.PROPERTY_VENDOR, "Eltako");
        updateProperty(Thing.PROPERTY_MODELID, "I dont know Oo");
        updateProperty(Thing.PROPERTY_PROTOCOL, "Serial and EnOcean (I think)");

        logger.info("Finished initializing!");

        // Note: When initialization can NOT be done set the status with more details for further
        // analysis. See also class ThingStatusDetail for all available status details.
        // Add a description to give user information to understand why thing does not work as expected. E.g.
        // updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
        // "Can not access device as username and/or password are invalid");
    }

    @Override
    public void dispose() {
        logger.info("Dispose instance!");

        if(this.pollingJob != null)
            pollingJob.cancel(true);
    }
}
