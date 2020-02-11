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
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandlerFactory;
import org.eclipse.smarthome.core.thing.binding.ThingHandler;
import org.eclipse.smarthome.core.thing.binding.ThingHandlerFactory;
import org.eclipse.smarthome.io.transport.serial.SerialPortManager;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link EltakoHandlerFactory} is responsible for creating things and thing
 * handlers.
 *
 * @author Martin Wenske - Initial contribution
 */
@Component(configurationPid = "binding.eltako", service = ThingHandlerFactory.class)
public class EltakoHandlerFactory extends BaseThingHandlerFactory {

    /*
     * Create list of things which are supported by this binding
     */
    private final static Set<ThingTypeUID> SUPPORTED_THING_TYPES_UIDS = Stream
            .concat(EltakoBridgeHandler.SUPPORTED_THING_TYPES.stream(),
                    EltakoBindingConstants.SUPPORTED_DEVICE_THING_TYPES_UIDS.stream())
            .collect(Collectors.toSet());

    @Reference
    SerialPortManager serialPortManager;

    /*
     * Logger instance to create log entries
     */
    private Logger logger = LoggerFactory.getLogger(EltakoHandlerFactory.class);

    /*
     * Public getter method to let framework know which things are available for this binding
     */
    @Override
    public boolean supportsThingType(ThingTypeUID thingTypeUID) {
        return SUPPORTED_THING_TYPES_UIDS.contains(thingTypeUID);
    }

    /*
     * CreateHandler() method is called in case a new thing should be added (this includes bridge things).
     * It need to return the created instance of the thing.
     */
    @Override
    protected @Nullable ThingHandler createHandler(Thing thing) {
        ThingTypeUID thingTypeUID = thing.getThingTypeUID();

        // Log event to console
        logger.debug("Create new handler => {}", thingTypeUID);

        // Create new thing of type bridge using serialPortManager instance
        if (THING_TYPE_BRIDGE.equals(thingTypeUID)) {
            return new EltakoBridgeHandler((Bridge) thing, serialPortManager);
        }

        // Create new thing of type FUD14
        if (THING_TYPE_FUD14.equals(thingTypeUID)) {
            return new EltakoHandler(thing);
        }

        // Log event to console
        logger.debug("Thing handler could be created because type is not supported => {}", thingTypeUID);
        return null;
    }
}
