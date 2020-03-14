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

package org.openhab.binding.miio.internal.basic;

import static java.nio.file.StandardWatchEventKinds.*;
import static org.openhab.binding.miio.internal.MiIoBindingConstants.BINDING_ID;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.WatchEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.config.core.ConfigConstants;
import org.eclipse.smarthome.core.service.AbstractWatchService;
import org.openhab.binding.miio.internal.MiIoBindingConstants;
import org.openhab.binding.miio.internal.Utils;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

/**
 * The {@link MiIoDatabaseWatchService} creates a registry of database file per ModelId
 *
 * @author Marcel Verpaalen - Initial contribution
 */
@Component(service = MiIoDatabaseWatchService.class)
@NonNullByDefault
public class MiIoDatabaseWatchService extends AbstractWatchService {
    private static final String LOCAL_DATABASE_PATH = ConfigConstants.getConfigFolder() + File.separator + "misc"
            + File.separator + BINDING_ID;
    private static final String DATABASE_FILES = ".json";

    private final Logger logger = LoggerFactory.getLogger(MiIoDatabaseWatchService.class);
    private Map<String, URL> databaseList = new HashMap<>();

    @Activate
    public MiIoDatabaseWatchService() {
        super(LOCAL_DATABASE_PATH);
        processWatchEvent(null, null, Paths.get(LOCAL_DATABASE_PATH));
        populateDatabase();
        if (logger.isTraceEnabled()) {
            for (String device : databaseList.keySet()) {
                logger.trace("Device: {} using URL: {}", device, databaseList.get(device));
            }
        }
    }

    @Override
    protected boolean watchSubDirectories() {
        return true;
    }

    @Override
    protected WatchEvent.Kind<?>[] getWatchEventKinds(@Nullable Path directory) {
        return new WatchEvent.Kind<?>[] { ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY };
    }

    @Override
    protected void processWatchEvent(@Nullable WatchEvent<?> event, WatchEvent.@Nullable Kind<?> kind,
            @Nullable Path path) {
        if (path != null && path.getFileName().toString().endsWith(DATABASE_FILES)) {
            logger.debug("Local Databases file {} changed. Refreshing device database.", path.getFileName());
            populateDatabase();
        }
    }

    /**
     * Return the database file URL for a given modelId
     *
     * @param modelId the model
     * @return URL with the definition for the model
     */
    public @Nullable URL getDatabaseUrl(String modelId) {
        return databaseList.get(modelId);
    }

    private void populateDatabase() {
        Map<String, URL> workingDatabaseList = new HashMap<>();
        List<URL> urlEntries = findDatabaseFiles();
        for (URL db : urlEntries) {
            logger.trace("Adding devices for db file: {}", db);
            try {
                @Nullable
                JsonObject deviceMapping = Utils.convertFileToJSON(db);
                if (deviceMapping != null) {
                    Gson gson = new GsonBuilder().serializeNulls().create();
                    MiIoBasicDevice devdb = gson.fromJson(deviceMapping, MiIoBasicDevice.class);
                    for (String id : devdb.getDevice().getId()) {
                        workingDatabaseList.put(id, db);
                    }
                }
            } catch (JsonIOException | JsonSyntaxException | IOException e) {
                logger.debug("Error while processing database '{}': {}", db, e.getMessage());
            }
            databaseList = workingDatabaseList;
        }
    }

    private List<URL> findDatabaseFiles() {
        List<URL> urlEntries = new ArrayList<>();
        Bundle bundle = FrameworkUtil.getBundle(getClass());
        urlEntries.addAll(Collections.list(bundle.findEntries(MiIoBindingConstants.DATABASE_PATH, "*.json", false)));
        String userDbFolder = ConfigConstants.getConfigFolder() + File.separator + "misc" + File.separator + BINDING_ID;
        try {
            File[] userDbFiles = new File(userDbFolder).listFiles((dir, name) -> name.endsWith(".json"));
            if (userDbFiles != null) {
                for (File f : userDbFiles) {
                    urlEntries.add(f.toURI().toURL());
                    logger.debug("Adding local json db file: {}, {}", f.getName(), f.toURI().toURL());
                }
            }
        } catch (IOException e) {
            logger.debug("Error while searching for database files: {}", e.getMessage());
        }
        return urlEntries;
    }
}