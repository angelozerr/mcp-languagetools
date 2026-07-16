package com.redhat.mcp.languagetools.server;

public interface ServerConfigListener {

    default void onAdded(ServerConfigAddedEvent event) {}

    default void onRemoved(ServerConfigRemovedEvent event) {}

    default void onInstalled(ServerConfigInstalledEvent event) {}
}
