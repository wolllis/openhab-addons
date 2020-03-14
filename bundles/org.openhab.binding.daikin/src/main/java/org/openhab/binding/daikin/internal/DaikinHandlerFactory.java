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
package org.openhab.binding.daikin.internal;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandlerFactory;
import org.eclipse.smarthome.core.thing.binding.ThingHandler;
import org.eclipse.smarthome.core.thing.binding.ThingHandlerFactory;
import org.openhab.binding.daikin.internal.handler.DaikinAcUnitHandler;
import org.openhab.binding.daikin.internal.handler.DaikinAirbaseUnitHandler;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.Activate;

/**
 * The {@link DaikinHandlerFactory} is responsible for creating things and thing
 * handlers.
 *
 * @author Tim Waterhouse <tim@timwaterhouse.com> - Initial contribution
 * @author Paul Smedley <paul@smedley.id.au> - Modifications to support Airbase Controllers

 */
@Component(service = ThingHandlerFactory.class, configurationPid = "binding.daikin")
@NonNullByDefault
public class DaikinHandlerFactory extends BaseThingHandlerFactory {

    private final DaikinDynamicStateDescriptionProvider stateDescriptionProvider;

    @Activate
    public DaikinHandlerFactory(@Reference DaikinDynamicStateDescriptionProvider stateDescriptionProvider) {
        this.stateDescriptionProvider = stateDescriptionProvider;
    }

    @Override
    public boolean supportsThingType(ThingTypeUID thingTypeUID) {
        return DaikinBindingConstants.SUPPORTED_THING_TYPES_UIDS.contains(thingTypeUID);
    }

    @Override
    protected @Nullable ThingHandler createHandler(Thing thing) {
        ThingTypeUID thingTypeUID = thing.getThingTypeUID();

        if (thingTypeUID.equals(DaikinBindingConstants.THING_TYPE_AC_UNIT)) {
            return new DaikinAcUnitHandler(thing, stateDescriptionProvider);
        } else if (thingTypeUID.equals(DaikinBindingConstants.THING_TYPE_AIRBASE_AC_UNIT)) {
            return new DaikinAirbaseUnitHandler(thing, stateDescriptionProvider);
        }
        return null;
    }
}
