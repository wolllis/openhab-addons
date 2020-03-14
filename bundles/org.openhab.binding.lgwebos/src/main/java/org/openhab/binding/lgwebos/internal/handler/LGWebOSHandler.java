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
package org.openhab.binding.lgwebos.internal.handler;

import static org.openhab.binding.lgwebos.internal.LGWebOSBindingConstants.*;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.eclipse.smarthome.config.core.Configuration;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandler;
import org.eclipse.smarthome.core.thing.binding.ThingHandlerService;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.State;
import org.eclipse.smarthome.core.types.StateOption;
import org.openhab.binding.lgwebos.action.LGWebOSActions;
import org.openhab.binding.lgwebos.internal.ChannelHandler;
import org.openhab.binding.lgwebos.internal.LGWebOSBindingConstants;
import org.openhab.binding.lgwebos.internal.LGWebOSStateDescriptionOptionProvider;
import org.openhab.binding.lgwebos.internal.LauncherApplication;
import org.openhab.binding.lgwebos.internal.MediaControlPlayer;
import org.openhab.binding.lgwebos.internal.MediaControlStop;
import org.openhab.binding.lgwebos.internal.PowerControlPower;
import org.openhab.binding.lgwebos.internal.RCButtonControl;
import org.openhab.binding.lgwebos.internal.TVControlChannel;
import org.openhab.binding.lgwebos.internal.TVControlChannelName;
import org.openhab.binding.lgwebos.internal.ToastControlToast;
import org.openhab.binding.lgwebos.internal.VolumeControlMute;
import org.openhab.binding.lgwebos.internal.VolumeControlVolume;
import org.openhab.binding.lgwebos.internal.handler.LGWebOSTVSocket.WebOSTVSocketListener;
import org.openhab.binding.lgwebos.internal.handler.core.AppInfo;
import org.openhab.binding.lgwebos.internal.handler.core.ResponseListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link LGWebOSHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Sebastian Prehn - initial contribution
 */
@NonNullByDefault
public class LGWebOSHandler extends BaseThingHandler implements LGWebOSTVSocket.ConfigProvider, WebOSTVSocketListener {

    /*
     * constants for device polling
     */
    private static final int RECONNECT_INTERVAL_SECONDS = 10;
    private static final int RECONNECT_START_UP_DELAY_SECONDS = 0;

    private static final String APP_ID_LIVETV = "com.webos.app.livetv";

    /*
     * error messages
     */
    private static final String MSG_MISSING_PARAM = "Missing parameter \"host\"";

    private final Logger logger = LoggerFactory.getLogger(LGWebOSHandler.class);

    // ChannelID to CommandHandler Map
    private final Map<String, ChannelHandler> channelHandlers;

    private final LauncherApplication appLauncher = new LauncherApplication();

    private @Nullable LGWebOSTVSocket socket;
    private final WebSocketClient webSocketClient;

    private final LGWebOSStateDescriptionOptionProvider stateDescriptionProvider;

    private @Nullable ScheduledFuture<?> reconnectJob;
    private @Nullable ScheduledFuture<?> keepAliveJob;

    private @Nullable LGWebOSConfiguration config;

    public LGWebOSHandler(Thing thing, WebSocketClient webSocketClient,
            LGWebOSStateDescriptionOptionProvider stateDescriptionProvider) {
        super(thing);
        this.webSocketClient = webSocketClient;
        this.stateDescriptionProvider = stateDescriptionProvider;

        Map<String, ChannelHandler> handlers = new HashMap<>();
        handlers.put(CHANNEL_VOLUME, new VolumeControlVolume());
        handlers.put(CHANNEL_POWER, new PowerControlPower());
        handlers.put(CHANNEL_MUTE, new VolumeControlMute());
        handlers.put(CHANNEL_CHANNEL, new TVControlChannel());
        handlers.put(CHANNEL_CHANNEL_NAME, new TVControlChannelName());
        handlers.put(CHANNEL_APP_LAUNCHER, appLauncher);
        handlers.put(CHANNEL_MEDIA_STOP, new MediaControlStop());
        handlers.put(CHANNEL_TOAST, new ToastControlToast());
        handlers.put(CHANNEL_MEDIA_PLAYER, new MediaControlPlayer());
        handlers.put(CHANNEL_RCBUTTON, new RCButtonControl());
        channelHandlers = Collections.unmodifiableMap(handlers);
    }

    private LGWebOSConfiguration getLGWebOSConfig() {
        LGWebOSConfiguration c = config;
        if (c == null) {
            c = getConfigAs(LGWebOSConfiguration.class);
            config = c;
        }
        return c;
    }

    @Override
    public void initialize() {
        LGWebOSConfiguration c = getLGWebOSConfig();
        logger.trace("Handler initialized with config {}", c);
        String host = c.getHost();
        if (host.isEmpty()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, MSG_MISSING_PARAM);
            return;
        }

        LGWebOSTVSocket s = new LGWebOSTVSocket(webSocketClient, this, host, c.getPort());
        s.setListener(this);
        socket = s;

        startReconnectJob();
    }

    @Override
    public void dispose() {
        stopKeepAliveJob();
        stopReconnectJob();

        LGWebOSTVSocket s = socket;
        if (s != null) {
            s.setListener(null);
            scheduler.execute(() -> s.disconnect()); // dispose should be none-blocking
        }
        socket = null;
        super.dispose();
    }

    private void startReconnectJob() {
        ScheduledFuture<?> job = reconnectJob;
        if (job == null || job.isCancelled()) {
            reconnectJob = scheduler.scheduleWithFixedDelay(() -> getSocket().connect(),
                    RECONNECT_START_UP_DELAY_SECONDS, RECONNECT_INTERVAL_SECONDS, TimeUnit.SECONDS);
        }
    }

    private void stopReconnectJob() {
        ScheduledFuture<?> job = reconnectJob;
        if (job != null && !job.isCancelled()) {
            job.cancel(true);
        }
        reconnectJob = null;
    }

    /**
     * Keep alive ensures that the web socket connection is used and does not time out.
     */
    private void startKeepAliveJob() {
        ScheduledFuture<?> job = keepAliveJob;
        if (job == null || job.isCancelled()) {
            // half of idle time out setting
            long keepAliveInterval = this.webSocketClient.getMaxIdleTimeout() / 2;

            // it is irrelevant which service is queried. Only need to send some packets over the wire

            keepAliveJob = scheduler
                    .scheduleWithFixedDelay(() -> getSocket().getRunningApp(new ResponseListener<AppInfo>() {

                        @Override
                        public void onSuccess(AppInfo responseObject) {
                            // ignore - actual response is not relevant here
                        }

                        @Override
                        public void onError(String message) {
                            // ignore
                        }
                    }), keepAliveInterval, keepAliveInterval, TimeUnit.MILLISECONDS);

        }
    }

    private void stopKeepAliveJob() {
        ScheduledFuture<?> job = keepAliveJob;
        if (job != null && !job.isCancelled()) {
            job.cancel(true);
        }
        keepAliveJob = null;
    }

    public LGWebOSTVSocket getSocket() {
        LGWebOSTVSocket s = this.socket;
        if (s == null) {
            throw new IllegalStateException("Component called before it was initialized or already disposed.");
        }
        return s;
    }

    public LauncherApplication getLauncherApplication() {
        return appLauncher;
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        logger.debug("handleCommand({},{})", channelUID, command);
        ChannelHandler handler = channelHandlers.get(channelUID.getId());
        if (handler == null) {
            logger.warn(
                    "Unable to handle command {}. No handler found for channel {}. This must not happen. Please report as a bug.",
                    command, channelUID);
            return;
        }

        handler.onReceiveCommand(channelUID.getId(), this, command);
    }

    @Override
    public String getKey() {
        return getLGWebOSConfig().getKey();
    }

    @Override
    public void storeKey(@Nullable String key) {
        // store it current configuration and avoiding complete re-initialization via handleConfigurationUpdate
        getLGWebOSConfig().key = key;

        // persist the configuration change
        Configuration configuration = editConfiguration();
        configuration.put(LGWebOSBindingConstants.CONFIG_KEY, key);
        updateConfiguration(configuration);
    }

    @Override
    public void storeProperties(Map<String, String> properties) {
        Map<String, String> map = editProperties();
        map.putAll(properties);
        updateProperties(map);
    }

    @Override
    public void onStateChanged(LGWebOSTVSocket.State state) {
        switch (state) {
            case DISCONNECTED:
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.NONE, "TV is off");
                channelHandlers.forEach((k, v) -> {
                    v.onDeviceRemoved(k, this);
                    v.removeAnySubscription(this);
                });

                stopKeepAliveJob();
                startReconnectJob();
                break;

            case REGISTERING:
                stopReconnectJob();
                startKeepAliveJob();
                updateStatus(ThingStatus.ONLINE, ThingStatusDetail.NONE,
                        "Registering - You may need to confirm pairing on TV.");
                break;
            case REGISTERED:
                updateStatus(ThingStatus.ONLINE, ThingStatusDetail.NONE, "Connected");

                channelHandlers.forEach((k, v) -> {
                    // refresh subscriptions except on channel, which can only be subscribe in livetv app. see
                    // postUpdate method
                    if (!CHANNEL_CHANNEL.equals(k) && !CHANNEL_CHANNEL_NAME.equals(k)) {
                        v.refreshSubscription(k, this);
                    }
                    v.onDeviceReady(k, this);
                });

                break;

        }

    }

    @Override
    public void onError(String error) {
        logger.debug("Connection failed - error: {}", error);

        switch (getSocket().getState()) {
            case DISCONNECTED:
                break;
            case REGISTERING:
            case REGISTERED:
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, "Connection Failed: " + error);
                break;
        }
    }

    public void setOptions(String channelId, List<StateOption> options) {
        logger.debug("setOptions channelId={} options.size()={}", channelId, options.size());
        stateDescriptionProvider.setStateOptions(new ChannelUID(getThing().getUID(), channelId), options);
    }

    public void postUpdate(String channelId, State state) {
        if (isLinked(channelId)) {
            updateState(channelId, state);
        }

        // channel subscription only works when on livetv app.
        if (CHANNEL_APP_LAUNCHER.equals(channelId) && APP_ID_LIVETV.equals(state.toString())) {
            Stream.of(CHANNEL_CHANNEL, CHANNEL_CHANNEL_NAME)
                    .forEach(k -> channelHandlers.get(k).refreshSubscription(k, this));
        }
    }

    @Override
    public Collection<Class<? extends ThingHandlerService>> getServices() {
        return Collections.singleton(LGWebOSActions.class);
    }
}
