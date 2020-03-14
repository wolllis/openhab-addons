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
package org.openhab.binding.miio.internal;

import static org.openhab.binding.miio.internal.MiIoBindingConstants.*;

import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandlerFactory;
import org.eclipse.smarthome.core.thing.binding.ThingHandler;
import org.eclipse.smarthome.core.thing.binding.ThingHandlerFactory;
import org.openhab.binding.miio.internal.basic.MiIoDatabaseWatchService;
import org.openhab.binding.miio.internal.handler.MiIoBasicHandler;
import org.openhab.binding.miio.internal.handler.MiIoGenericHandler;
import org.openhab.binding.miio.internal.handler.MiIoUnsupportedHandler;
import org.openhab.binding.miio.internal.handler.MiIoVacuumHandler;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * The {@link MiIoHandlerFactory} is responsible for creating things and thing
 * handlers.
 *
 * @author Marcel Verpaalen - Initial contribution
 */
@Component(service = ThingHandlerFactory.class, configurationPid = "binding.miio")
public class MiIoHandlerFactory extends BaseThingHandlerFactory {

    private MiIoDatabaseWatchService miIoDatabaseWatchService;

    @Activate
    public MiIoHandlerFactory(@Reference MiIoDatabaseWatchService miIoDatabaseWatchService) {
        this.miIoDatabaseWatchService = miIoDatabaseWatchService;
    }

    @Override
    public boolean supportsThingType(ThingTypeUID thingTypeUID) {
        return SUPPORTED_THING_TYPES_UIDS.contains(thingTypeUID);
    }

    @Override
    protected ThingHandler createHandler(Thing thing) {
        ThingTypeUID thingTypeUID = thing.getThingTypeUID();
        if (thingTypeUID.equals(THING_TYPE_MIIO)) {
            return new MiIoGenericHandler(thing, miIoDatabaseWatchService);
        }
        if (thingTypeUID.equals(THING_TYPE_BASIC)) {
            return new MiIoBasicHandler(thing, miIoDatabaseWatchService);
        }
        if (thingTypeUID.equals(THING_TYPE_VACUUM)) {
            return new MiIoVacuumHandler(thing, miIoDatabaseWatchService);
        }
        return new MiIoUnsupportedHandler(thing, miIoDatabaseWatchService);
    }
}
