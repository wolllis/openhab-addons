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
package org.openhab.binding.somfytahoma.internal.handler;

import static org.openhab.binding.somfytahoma.internal.SomfyTahomaBindingConstants.*;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.thing.Channel;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.RefreshType;
import org.eclipse.smarthome.core.types.State;
import org.openhab.binding.somfytahoma.internal.model.SomfyTahomaState;

/**
 * The {@link SomfyTahomaAdjustableSlatsRollerShutterHandler} is responsible for handling commands,
 * which are sent to one of the channels of the adjustable slats roller shutter thing.
 *
 * @author Ondrej Pecta - Initial contribution
 */
@NonNullByDefault
public class SomfyTahomaAdjustableSlatsRollerShutterHandler extends SomfyTahomaBaseThingHandler {

    public SomfyTahomaAdjustableSlatsRollerShutterHandler(Thing thing) {
        super(thing);
        stateNames.put(CONTROL, CLOSURE_OR_ROCKER_STATE);
        stateNames.put(ROCKER, CLOSURE_OR_ROCKER_STATE);
        stateNames.put(ORIENTATION, SLATE_ORIENTATION_STATE);
        //override state type because the control may return string 'rocker'
        cacheStateType(CONTROL, TYPE_PERCENT);
    }

    @Override
    public void updateThingChannels(SomfyTahomaState state) {
        if (CLOSURE_OR_ROCKER_STATE.equals(state.getName())) {
            Channel ch = thing.getChannel(CONTROL);
            Channel chRocker = thing.getChannel(ROCKER);
            if ("rocker".equals(state.getValue())) {
                if (chRocker != null) {
                    updateState(chRocker.getUID(), OnOffType.ON);
                }
            } else {
                if (chRocker != null) {
                    updateState(chRocker.getUID(), OnOffType.OFF);
                }
                if (ch != null) {
                    State newState = parseTahomaState(state);
                    if (newState != null) {
                        updateState(ch.getUID(), newState);
                    }
                }
            }
        } else if (SLATE_ORIENTATION_STATE.equals(state.getName())) {
            Channel ch = thing.getChannel(ORIENTATION);
            if (ch != null) {
                State newState = parseTahomaState(state);
                if (newState != null) {
                    updateState(ch.getUID(), newState);
                }
            }
        }
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        super.handleCommand(channelUID, command);
        if (!ROCKER.equals(channelUID.getId()) && !CONTROL.equals(channelUID.getId())
                && !ORIENTATION.equals(channelUID.getId())) {
            return;
        }

        if (command instanceof RefreshType) {
            return;
        } else if (ROCKER.equals(channelUID.getId())) {
            if (OnOffType.ON.equals(command)) {
                sendCommand(COMMAND_SET_ROCKERPOSITION);
            }
        } else {
            String cmd = getTahomaCommand(command.toString(), channelUID.getId());
            if (COMMAND_SET_ROCKERPOSITION.equals(cmd)) {
                String executionId = getCurrentExecutions();
                if (executionId != null) {
                    // Check if the roller shutter is moving and rocker is sent => STOP it
                    cancelExecution(executionId);
                } else {
                    sendCommand(COMMAND_SET_ROCKERPOSITION);
                }
            } else {
                String param = (COMMAND_SET_CLOSURE.equals(cmd) || COMMAND_SET_ORIENTATION.equals(cmd))
                        ? "[" + command.toString() + "]"
                        : "[]";
                sendCommand(cmd, param);
            }
        }
    }

    protected String getTahomaCommand(String command, String channelId) {
        switch (command) {
            case "OFF":
            case "DOWN":
            case "CLOSE":
                return COMMAND_DOWN;
            case "ON":
            case "UP":
            case "OPEN":
                return COMMAND_UP;
            case "STOP":
                return COMMAND_SET_ROCKERPOSITION;
            default:
                if (CONTROL.equals(channelId)) {
                    return COMMAND_SET_CLOSURE;
                } else {
                    return COMMAND_SET_ORIENTATION;
                }
        }
    }
}
