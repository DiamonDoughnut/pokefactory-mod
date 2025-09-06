package com.pokefactory.legends.server.data;

import java.util.UUID;

public record CaptureData(UUID playerUuid, int nationalDexNumber, long timestamp) {
    
    public String getUniqueKey() {
        return playerUuid.toString() + ":" + nationalDexNumber;
    }
}