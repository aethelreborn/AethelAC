package ac.aethel.aethelac.manager.config.update;

import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Centralised registry of {@link ConfigUpdater.Spec} instances for every
 * Aethel config file. Each yml file owns:
 *
 * <ul>
 *   <li>a {@code config-flavor} marker (V2 / V3) that the updater rejects on
 *       a mismatch — a wrong-flavor file fails fast instead of silently
 *       flat-merging into the wrong key set;</li>
 *   <li>a {@code config-version} integer bumped on every breaking shape
 *       change to the bundled default;</li>
 *   <li>an optional in-version {@link ConfigUpdater.Migration} chain that
 *       lifts values forward across each version step. Multiple steps
 *       stack — v3 → v8 runs each registered v4/v5/v6/v7/v8 migration.</li>
 * </ul>
 *
 * <p>Resource paths name the directory ({@code "/database/"}) — the updater
 * picks the language-appropriate {@code en.yml} / {@code zh.yml} / … from
 * inside it at update time, falling back to {@code en.yml} when the active
 * locale isn't bundled. Same shape Configuralize uses for runtime loading.
 *
 * <p>Migrations write through the {@link MigrationContext} API: typed
 * dotted-path reads from {@code input()}, writes to {@code output()},
 * cross-file pushes via {@code otherFile(name)}. The updater applies
 * recorded write ops via the line-mapped patcher so the bundled default's
 * comments survive the rewrite.
 *
 * <p>{@code punishments.yml} is intentionally absent — open-ended user-
 * defined data (operator-authored punishment groups), no schema versioning.
 */
@UtilityClass
public final class AethelConfigSpecs {

    /**
     * Spec for the main {@code config.yml}.
     *
     * <p>v9 → v10: migrates Aethel 2.x's {@code history:} block out of
     * config.yml and into the new {@code database.yml} + the matching
     * {@code databases/<id>.yml}. Pre-V2 operators had all DB settings
     * inline in config.yml; V2 splits the datastore config into its own
     * file. The bundled default at v10 has no {@code history:} block, so
     * the auto-lift naturally drops it from the new file; the migration
     * only needs to ferry the values over.
     *
     * <p>v10 → v11: adds {@code update-permission-ticks} to the bundled
     * config. No explicit migration is needed; the updater's default rewrite
     * adds the key, and auto-lift preserves an existing user value if present.
     *
     * <p>v11 → v12: adds the {@code client-fingerprint} section (known cheat
     * client brands and registered channels) used by the Fingerprint check.
     * Like v11, no explicit migration is needed — the default rewrite adds
     * the section and auto-lift keeps any existing user lists.
     *
     * <p>v12 → v13: adds the {@code attack-patterns} section (autoclicker
     * cadence thresholds) used by the Autoclicker check. Same drift-free
     * default rewrite as v11/v12.
     *
     * <p>v13 → v14: adds {@code attack-patterns.no-burst-min-cps} /
     * {@code burst-gap-ms} and the {@code aim-analysis} section
     * (LinearRotation thresholds). Same drift-free default rewrite.
     *
     * <p>v14 → v15: adds the {@code shadow-checks} staging list and the yaw /
     * pitch series thresholds under {@code aim-analysis}. Same drift-free
     * default rewrite.
     *
     * <p>v15 → v16: adds the {@code aim-analysis} decay band used by the
     * RotationDecay check. Same drift-free default rewrite.
     *
     * <p>v16 → v17: adds the {@code attack-patterns} metronome and
     * rotating-attack knobs. Same drift-free default rewrite.
     *
     * <p>v17 → v18: widens the {@code aim-analysis} decay band and adds the
     * attack-anchored {@code return-*} knobs. Same drift-free default rewrite.
     *
     * <p>v18 → v19: adds the {@code attack-patterns} steady-cadence band.
     * Same drift-free default rewrite.
     *
     * <p>v19 → v20: adds the {@code idle-automation} section. Same drift-free
     * default rewrite.
     *
     * <p>v20 → v21: adds the {@code inventory-automation} section. Same
     * drift-free default rewrite.
     *
     * <p>v21 → v22: adds the {@code reaction-analysis} section. Same
     * drift-free default rewrite.
     *
     * <p>v22 → v23: adds the {@code inventory-automation} tool-swap knobs.
     * Same drift-free default rewrite.
     *
     * <p>v23 → v24: adds the {@code inventory-automation} drop-burst knobs.
     * Same drift-free default rewrite.
     *
     * <p>v24 → v25: widens the metronome CV default to 0.05 so jittered
     * autoclickers are caught while human rhythm stays out.
     *
     * <p>v25 → v26: adds the {@code attack-patterns} swing knobs for the
     * no-swing aura check.
     *
     * <p>v26 → v27: locale parity pass (drops the legacy German
     * {@code VehicleC} block, adds the missing Romanian
     * {@code reset-item-usage-on-item-use} knob).
     *
     * <p>v27 → v28: adds the {@code attack-patterns} mace smash knobs.
     *
     * <p>v28 → v29: lowers the rotation decay ratio floor to 0.18 so fast
     * geometric rotators are caught; the redundant half-decay gate was
     * removed.
     *
     * <p>v29 → v30: adds the {@code pack-spoof} section for the
     * resource-pack spoof check.
     *
     * <p>v30 → v31: adds the {@code inventory-automation} menu-cadence
     * knobs for the FastClick second tier.
     *
     * <p>v31 → v32: adds the {@code elytra-analysis} firework cadence knobs
     * for the FireworkCadence check.
     *
     * <p>v32 → v33: adds the {@code fishing-analysis} reaction knobs for the
     * AutoFish check.
     *
     * <p>v33 → v34: adds the {@code aim-analysis} silent aim knobs for the
     * SilentAim check.
     *
     * <p>v34 → v35: adds the {@code attack-patterns} meta-swap knobs for the
     * SwapAttack check.
     *
     * <p>v35 → v36: adds the {@code attack-patterns} triggerbot knobs for the
     * TriggerBot check.
     *
     * <p>v36 → v37: adds the {@code attack-patterns} instant crystal knobs for
     * the CrystalCadence check.
     *
     * <p>v37 → v38: adds the {@code attack-patterns} ping gate and the
     * optional high-ping attack cancellation policy.
     *
     * <p>v38 → v39: adds the {@code attack-patterns} anchor cadence knobs for
     * the AnchorCadence check.
     *
     * <p>v39 → v40: adds the {@code placement-analysis} airplace knobs for the
     * AirPlace check.
     *
     * <p>v40 → v41: adds the tick-quantized tier knobs for the CrystalCadence
     * check.
     *
     * <p>v41 → v42: adds the {@code aim-analysis} hitbox-center precision
     * knobs for the Accuracy check.
     *
     * <p>v42 → v43: adds the {@code inventory-automation} fake-close knobs
     * for the CloseSpoof check.
     *
     * <p>v43 → v44: adds the {@code jump-analysis} metronome knobs for the
     * JumpCadence check.
     */
    public static @NotNull ConfigUpdater.Spec mainConfig() {
        return ConfigUpdater.Spec.builder("/config/", 44, ConfigUpdater.ConfigFlavor.V2)
                .migration(10, ctx -> {
                    String typeRaw = ctx.input().getString("history.database.type");
                    String type = typeRaw == null ? null : typeRaw.trim().toUpperCase(Locale.ROOT);
                    String backendId = backendIdFor(type);
                    if (backendId != null) {
                        // Route every relational category to the operator's
                        // chosen backend so the new layout matches the old
                        // single-DB shape. blob is intentionally absent —
                        // none of the SQL backends declare support for it
                        // (would fail capability validation); leave it on
                        // whatever the bundled default routes blob to.
                        for (String cat : new String[]{"violation", "session", "player-identity", "setting"}) {
                            ctx.otherFile("database.yml").put("database.routing." + cat, backendId);
                        }
                    }
                    // Carry the rendering settings over — these moved out of
                    // history: into database.* with the same names.
                    Integer entriesPerPage = ctx.input().getInt("history.entries-per-page");
                    if (entriesPerPage != null) {
                        ctx.otherFile("database.yml").put("database.history.entries-per-page", entriesPerPage);
                    }
                    String serverName = ctx.input().getString("history.server-name");
                    if (serverName != null) {
                        ctx.otherFile("database.yml").put("database.server-name", serverName);
                    }
                    if (backendId != null && !backendId.equals("sqlite")) {
                        // Connection details only matter for networked backends.
                        // SQLite settings (file path) stay at their defaults.
                        String target = "databases/" + backendId + ".yml";
                        Object host = ctx.input().get("history.database.host");
                        Object port = ctx.input().get("history.database.port");
                        Object db = ctx.input().get("history.database.database");
                        Object user = ctx.input().get("history.database.username");
                        Object pass = ctx.input().get("history.database.password");
                        if (host != null) ctx.otherFile(target).put(backendId + ".host", host);
                        if (port != null) ctx.otherFile(target).put(backendId + ".port", port);
                        if (db != null) ctx.otherFile(target).put(backendId + ".database", db);
                        if (user != null) ctx.otherFile(target).put(backendId + ".user", user);
                        if (pass != null) ctx.otherFile(target).put(backendId + ".password", pass);
                    }
                })
                .build();
    }

    private static @Nullable String backendIdFor(@Nullable String legacyType) {
        if (legacyType == null) return null;
        return switch (legacyType) {
            case "SQLITE" -> "sqlite";
            case "MYSQL" -> "mysql";
            case "POSTGRESQL", "POSTGRES" -> "postgres";
            default -> null;
        };
    }

    public static @NotNull ConfigUpdater.Spec discord() {
        return ConfigUpdater.Spec.builder("/discord/", 1, ConfigUpdater.ConfigFlavor.V2)
                .build();
    }

    /**
     * The messages bundle. Drift-free default rewrite, same as the main
     * config; operators' edits to existing keys are preserved.
     *
     * <p>v2 → v3: adds the automation metrics (attack cadence, container
     * clicks, idle events, tool swaps, damage reactions) to the
     * {@code profile} template.
     *
     * <p>v3 → v4: adds the missing Japanese reload messages.
     *
     * <p>v4 → v5: adds the mace smash window and fixed-gap pack report
     * metrics to the {@code profile} template.
     *
     * <p>v5 → v6: adds the quick reel and firework boost metrics to the
     * {@code profile} template.
     */
    public static @NotNull ConfigUpdater.Spec messages() {
        return ConfigUpdater.Spec.builder("/messages/", 6, ConfigUpdater.ConfigFlavor.V2)
                .build();
    }

    /**
     * The datastore router config (top-level {@code database:} wrapper).
     * No own-file migrations — operators arriving from Aethel 2.x have no
     * existing database.yml on disk; the legacy lift happens on the
     * config.yml side via cross-file writes (see {@link #mainConfig}).
     */
    public static @NotNull ConfigUpdater.Spec database() {
        return ConfigUpdater.Spec.builder("/database/", 1, ConfigUpdater.ConfigFlavor.V2)
                .build();
    }

    /**
     * Per-backend file at {@code databases/<id>/en.yml}. Each backend's
     * settings live under a top-level {@code <id>:} wrapper so all
     * per-backend files load through the shared ConfigManager without
     * key collisions across backends.
     */
    public static @NotNull ConfigUpdater.Spec backend(@NotNull String backendId) {
        ConfigUpdater.Spec.Builder builder = ConfigUpdater.Spec.builder(
                "/databases/" + backendId + "/",
                backendVersion(backendId),
                ConfigUpdater.ConfigFlavor.V2);
        if (backendSupportsHikariPoolSettings(backendId)) {
            builder.migration(2, ctx -> preservePoolSettingOverrides(ctx, backendId));
        }
        return builder.build();
    }

    private static int backendVersion(@NotNull String backendId) {
        return backendSupportsHikariPoolSettings(backendId) ? 2 : 1;
    }

    private static boolean backendSupportsHikariPoolSettings(@NotNull String backendId) {
        return backendId.equals("mysql") || backendId.equals("postgres");
    }

    private static void preservePoolSettingOverrides(
            @NotNull MigrationContext ctx,
            @NotNull String backendId) {
        // v1 did not ship these keys, but preserve values if an operator
        // already added them by hand before the bundled defaults caught up.
        String prefix = backendId + ".pool-settings.";
        for (String key : new String[]{
                "maximum-pool-size",
                "minimum-idle",
                "maximum-lifetime-ms",
                "keepalive-time-ms",
                "connection-timeout-ms"}) {
            Object value = ctx.input().get(prefix + key);
            if (value != null) {
                ctx.output().put(prefix + key, value);
            }
        }
    }
}
