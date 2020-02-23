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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.smarthome.core.thing.ThingTypeUID;

/**
 * The {@link EltakoBindingConstants} class defines common constants, which are
 * used across the whole binding.
 *
 * @author Martin Wenske - Initial contribution
 */
@NonNullByDefault
public class EltakoBindingConstants {

    // binding
    public static final String BINDING_ID = "eltako";

    // bridge
    public final static ThingTypeUID THING_TYPE_BRIDGE = new ThingTypeUID(BINDING_ID, "bridge");

    // List of all Thing Type UIDs
    public static final ThingTypeUID THING_TYPE_FUD14 = new ThingTypeUID(BINDING_ID, "FUD14");

    public static final Set<ThingTypeUID> SUPPORTED_DEVICE_THING_TYPES_UIDS = new HashSet<>(
            Arrays.asList(THING_TYPE_FUD14));

    // List of all FUD14 channel IDs
    public static final String CHANNEL_BRIGHTNESS = "brightness";
    public static final String CHANNEL_SPEED = "speed";
    public static final String CHANNEL_POWER = "power";
    public static final String CHANNEL_BLOCKING = "blocking";

    // List of all configuration parameters
    public static final String FUD14_HARDWARE_VERSION = "Version";
    public static final String FUD14_ID = "ID";

    // Bridge config properties
    public static final String SERIALCOMPORT = "SerialComPort";

    // Bridge config properties
}
