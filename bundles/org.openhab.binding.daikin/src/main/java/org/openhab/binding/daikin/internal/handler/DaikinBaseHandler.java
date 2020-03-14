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
package org.openhab.binding.daikin.internal.handler;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import java.util.EnumSet;
import java.util.List;
import java.util.ArrayList;

import javax.measure.quantity.Temperature;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.QuantityType;
import org.eclipse.smarthome.core.library.types.StringType;
import org.eclipse.smarthome.core.library.unit.SIUnits;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandler;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.State;
import org.eclipse.smarthome.core.types.StateOption;
import org.eclipse.smarthome.core.types.UnDefType;
import org.openhab.binding.daikin.internal.DaikinBindingConstants;
import org.openhab.binding.daikin.internal.DaikinCommunicationException;
import org.openhab.binding.daikin.internal.DaikinWebTargets;
import org.openhab.binding.daikin.internal.DaikinDynamicStateDescriptionProvider;

import org.openhab.binding.daikin.internal.config.DaikinConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class that handles common tasks with a Daikin air conditioning unit.
 *
 * @author Tim Waterhouse - Initial Contribution
 * @author Paul Smedley <paul@smedley.id.au> - Modifications to support Airbase Controllers
 * @author Jimmy Tanagra - Split handler classes, support Airside and DynamicStateDescription
 *
 */
@NonNullByDefault
public abstract class DaikinBaseHandler extends BaseThingHandler {
    private final Logger logger = LoggerFactory.getLogger(DaikinBaseHandler.class);

    private long refreshInterval;

    protected @Nullable DaikinWebTargets webTargets;
    private @Nullable ScheduledFuture<?> pollFuture;
    protected final DaikinDynamicStateDescriptionProvider stateDescriptionProvider;

    // Abstract methods to be overridden by specific Daikin implementation class
    protected abstract void pollStatus() throws IOException;

    protected abstract void changePower(boolean power) throws DaikinCommunicationException;
    protected abstract void changeSetPoint(double newTemperature) throws DaikinCommunicationException;
    protected abstract void changeMode(String mode) throws DaikinCommunicationException;
    protected abstract void changeFanSpeed(String fanSpeed) throws DaikinCommunicationException;

    // Power, Temp, Fan and Mode are handled in this base class. Override this to handle additional channels. 
    protected abstract boolean handleCommandInternal(ChannelUID channelUID, Command command) throws DaikinCommunicationException;


    public DaikinBaseHandler(Thing thing, DaikinDynamicStateDescriptionProvider stateDescriptionProvider) {
        super(thing);
        this.stateDescriptionProvider = stateDescriptionProvider;
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        try {
            if (handleCommandInternal(channelUID, command)) {
                return;
            }
            switch (channelUID.getId()) {
                case DaikinBindingConstants.CHANNEL_AC_POWER:
                    if (command instanceof OnOffType) {
                        changePower(((OnOffType) command).equals(OnOffType.ON));
                        return;
                    }
                    break;
                case DaikinBindingConstants.CHANNEL_AC_TEMP:
                    if (changeSetPoint(command)) {
                        return;
                    }
                    break;
                case DaikinBindingConstants.CHANNEL_AIRBASE_AC_FAN_SPEED:
                case DaikinBindingConstants.CHANNEL_AC_FAN_SPEED:
                    if (command instanceof StringType) {
                        changeFanSpeed(((StringType) command).toString());
                        return;
                    }
                    break;
                case DaikinBindingConstants.CHANNEL_AC_MODE:
                    if (command instanceof StringType) {
                        changeMode(((StringType) command).toString());
                        return;
                    }
                    break;
            }
            logger.debug("Received command ({}) of wrong type for thing '{}' on channel {}", command, thing.getUID().getAsString(), channelUID.getId());
        } catch (DaikinCommunicationException ex) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, ex.getMessage());
        }
    }

    @Override
    public void initialize() {
        logger.debug("Initializing Daikin AC Unit");
        DaikinConfiguration config = getConfigAs(DaikinConfiguration.class);
        if (config.host == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "Host address must be set");
        } else {
            webTargets = new DaikinWebTargets(config.host);
            refreshInterval = config.refresh;

            schedulePoll();
        }
    }

    @Override
    public void handleRemoval() {
        stopPoll();
        super.handleRemoval();
    }

    @Override
    public void dispose() {
        stopPoll();
        super.dispose();
    }

    protected void schedulePoll() {
        if (pollFuture != null) {
            pollFuture.cancel(false);
        }
        logger.debug("Scheduling poll for 1s out, then every {} s", refreshInterval);
        pollFuture = scheduler.scheduleWithFixedDelay(this::poll, 1, refreshInterval, TimeUnit.SECONDS);
    }

    protected synchronized void stopPoll() {
        if (pollFuture != null && !pollFuture.isCancelled()) {
            pollFuture.cancel(true);
            pollFuture = null;
        }
    }

    private synchronized void poll() {
        try {
            logger.debug("Polling for state");
            pollStatus();
        } catch (IOException e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
        } catch (RuntimeException e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
        }
    }

    protected void updateTemperatureChannel(String channel, Optional<Double> maybeTemperature) {
        updateState(channel,
                maybeTemperature.<State> map(t -> new QuantityType<Temperature>(new DecimalType(t), SIUnits.CELSIUS))
                        .orElse(UnDefType.UNDEF));
    }

    /**
     * @return true if the command was of an expected type, false otherwise
     */
    private boolean changeSetPoint(Command command) throws DaikinCommunicationException {
        double newTemperature;
        if (command instanceof DecimalType) {
            newTemperature = ((DecimalType) command).doubleValue();
        } else if (command instanceof QuantityType) {
            newTemperature = ((QuantityType<Temperature>) command).toUnit(SIUnits.CELSIUS).doubleValue();
        } else {
            return false;
        }

        // Only half degree increments are allowed, all others are silently rejected by the A/C units
        newTemperature = Math.round(newTemperature * 2) / 2.0;
        changeSetPoint(newTemperature);
        return true;
    }
}
