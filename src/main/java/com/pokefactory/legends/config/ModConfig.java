package com.pokefactory.legends.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public class ModConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    
    public static final ModConfigSpec.ConfigValue<String> API_BASE_URL = BUILDER
            .comment("Base URL for the PokéFactory API")
            .define("api_base_url", "http://localhost:8080/api/v1");
            
    public static final ModConfigSpec.ConfigValue<String> SERVER_TOKEN = BUILDER
            .comment("JWT token for server authentication")
            .define("server_token", "");

    public static final ModConfigSpec.ConfigValue<String> SERVER_SECRET = BUILDER
            .comment("Secret Key for JWT creation - must match backend secret")
            .define("secret_key", "");
            
    public static final ModConfigSpec.IntValue CONNECTION_TIMEOUT = BUILDER
            .comment("HTTP connection timeout in seconds")
            .defineInRange("connection_timeout", 30, 5, 300);
            
    public static final ModConfigSpec.BooleanValue DEV_MODE = BUILDER
            .comment("Enable development mode (works in single-player)")
            .define("dev_mode", false);
            
    public static final ModConfigSpec.BooleanValue SIMULATE_MULTIPLAYER = BUILDER
            .comment("Simulate multiplayer behavior in single-player for testing")
            .define("simulate_multiplayer", false);
            
    public static final ModConfigSpec SPEC = BUILDER.build();
}