package org.Cry_Legende.sHJumpPads;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Effect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.StringUtil;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class SHJumpPads extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {

    // Physique reprise de l'ancien JumpPads : il lançait une FallingBlock (gravité 0.04, frottement 0.98)
    // et recopiait sa vitesse sur le joueur à chaque tick. On simule la même trajectoire sans entité.
    private static final double GRAVITY = 0.04;
    private static final double DRAG = 0.98;
    private static final double POWER_DIVISOR = 15.0;
    private static final int MAX_FLIGHT_TICKS = 400;

    private final Map<String, JumpPad> pads = new HashMap<>();
    // Cases (clé de bloc) où se trouve une plaque, par monde : filtre rapide pour PlayerMoveEvent
    private final Map<String, Set<Long>> padCells = new HashMap<>();
    private final Set<UUID> launched = new HashSet<>();
    private final Set<UUID> fallProtected = new HashSet<>();
    // Joueurs à qui le plugin a donné allowFlight pour la durée du vol
    private final Set<UUID> flightGranted = new HashSet<>();
    // Plaques illisibles de jumppads.yml, réécrites telles quelles à la sauvegarde
    private final Map<String, Object> unreadablePads = new HashMap<>();
    // Faux si jumppads.yml n'a pas pu être lu : on ne le réécrit pas
    private boolean padsReadable = true;
    private File padsFile;

    private String prefix;
    private double maxPower;
    private boolean launchWalking;
    private boolean blockTeleporting;
    private boolean trailEnabled;
    private Particle.DustOptions trailDust;
    private int trailDensity;
    private boolean soundEnabled;
    private boolean soundWorld;
    private String soundName;
    private float soundVolume;
    private float soundPitch;
    private boolean visualizerEnabled;
    private Particle visualizerParticle;
    private Particle.DustOptions visualizerDust;
    private double visualizerRadius;
    private double visualizerSpeed;
    private double visualizerAngle;
    private Effect launchEffect;
    private boolean cleanUp;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfiguration();
        padsFile = new File(getDataFolder(), "jumppads.yml");
        importLegacyPads();
        loadPads();

        getServer().getPluginManager().registerEvents(this, this);
        getCommand("shjumppads").setExecutor(this);
        getCommand("shjumppads").setTabCompleter(this);

        Bukkit.getScheduler().runTaskTimer(this, this::drawVisualizers, 40L, 2L);
        getLogger().info(pads.size() + " plaques de saut chargées.");
    }

    @Override
    public void onDisable() {
        for (UUID uuid : flightGranted) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR) {
                player.setFlying(false);
                player.setAllowFlight(false);
            }
        }
        flightGranted.clear();
        launched.clear();
        fallProtected.clear();
    }

    // ------------------------------------------------------------------ configuration et données

    private void loadConfiguration() {
        reloadConfig();
        FileConfiguration c = getConfig();
        prefix = c.getString("prefix", "&7&l« &a&lSH &8&l» &7");
        maxPower = c.getDouble("max-power", 50);
        launchWalking = c.getBoolean("launch-in-walking-direction", true);
        blockTeleporting = c.getBoolean("block-teleporting", true);
        trailEnabled = c.getBoolean("trail.enabled", true);
        trailDust = new Particle.DustOptions(parseColor(c.getString("trail.color", "red")), 1.0f);
        trailDensity = Math.max(1, c.getInt("trail.density", 1));
        soundEnabled = c.getBoolean("sound.enabled", true);
        soundWorld = c.getBoolean("sound.world", true);
        soundName = c.getString("sound.name", "entity.wither.shoot");
        soundVolume = (float) c.getDouble("sound.volume", 1.0);
        soundPitch = (float) c.getDouble("sound.pitch", 1.0);
        visualizerEnabled = c.getBoolean("visualizer.enabled", true);
        visualizerParticle = parseParticle(c.getString("visualizer.particle", "DUST"), Particle.DUST);
        visualizerDust = new Particle.DustOptions(parseColor(c.getString("visualizer.color", "red")), 1.0f);
        visualizerRadius = c.getDouble("visualizer.radius", 0.5);
        visualizerSpeed = c.getDouble("visualizer.rotation-speed", 2);
        launchEffect = null;
        if (c.getBoolean("launch-effect.enabled", true)) {
            try {
                launchEffect = Effect.valueOf(c.getString("launch-effect.effect", "END_GATEWAY_SPAWN").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                getLogger().warning("Effet de lancement inconnu : " + c.getString("launch-effect.effect"));
            }
        }
        cleanUp = c.getBoolean("clean-up", true);
    }

    /**
     * Premier démarrage : reprend tel quel le fichier de l'ancien plugin JumpPads.
     */
    private void importLegacyPads() {
        File legacy = new File(getDataFolder().getParentFile(), "JumpPads/jumppads.yml");
        if (padsFile.exists() || !legacy.exists()) {
            return;
        }
        try {
            getDataFolder().mkdirs();
            Files.copy(legacy.toPath(), padsFile.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
            getLogger().info("jumppads.yml de l'ancien plugin JumpPads importé.");
        } catch (IOException e) {
            getLogger().warning("Import de JumpPads/jumppads.yml impossible : " + e.getMessage());
        }
    }

    private void loadPads() {
        YamlConfiguration yaml = new YamlConfiguration();
        if (padsFile.exists()) {
            try {
                yaml.load(padsFile);
            } catch (IOException | InvalidConfigurationException e) {
                // loadConfiguration rendrait une configuration vide : la sauvegarde suivante effacerait toutes les plaques
                padsReadable = false;
                getLogger().severe("jumppads.yml illisible, les plaques ne seront pas sauvegardées pour ne pas l'écraser : " + e.getMessage());
                return;
            }
        }
        padsReadable = true;
        pads.clear();
        padCells.clear();
        unreadablePads.clear();
        ConfigurationSection root = yaml.getConfigurationSection("JumpPads");
        if (root == null) {
            return;
        }
        for (String key : root.getKeys(false)) {
            JumpPad pad = JumpPad.fromConfig(key, root.getConfigurationSection(key));
            if (pad == null) {
                getLogger().warning("Plaque illisible ignorée (gardée telle quelle dans jumppads.yml) : " + key);
                unreadablePads.put(key, plain(root.get(key)));
                continue;
            }
            pads.put(pad.key(), pad);
            indexPad(pad);
        }
    }

    // Sections converties en Map : une ConfigurationSection posée telle quelle dans une autre configuration se sauve mal
    private static Object plain(Object value) {
        if (value instanceof ConfigurationSection section) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (String key : section.getKeys(false)) {
                map.put(key, plain(section.get(key)));
            }
            return map;
        }
        return value;
    }

    private void indexPad(JumpPad pad) {
        padCells.computeIfAbsent(pad.world, world -> new HashSet<>()).add(cell(pad.x, pad.y, pad.z));
    }

    // Clé d'une case : 26 bits pour x et z, 12 bits pour y
    private static long cell(int x, int y, int z) {
        return ((long) x & 0x3FFFFFF) << 38 | ((long) z & 0x3FFFFFF) << 12 | (y & 0xFFF);
    }

    private void savePads() {
        if (!padsReadable) {
            getLogger().warning("jumppads.yml n'a pas pu être lu : sauvegarde ignorée.");
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        ConfigurationSection root = yaml.createSection("JumpPads");
        for (JumpPad pad : pads.values()) {
            pad.save(root.createSection(pad.key()));
        }
        unreadablePads.forEach(root::set);
        try {
            yaml.save(padsFile);
        } catch (IOException e) {
            getLogger().severe("Sauvegarde de jumppads.yml impossible : " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ lancement

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location to = event.getTo();
        Set<Long> cells = padCells.get(to.getWorld().getName());
        if (cells == null || launched.contains(player.getUniqueId())) {
            return;
        }
        // Sort tout de suite si ni la case du joueur ni celle du dessous ne porte de plaque
        int bx = to.getBlockX(), by = to.getBlockY(), bz = to.getBlockZ();
        if (!cells.contains(cell(bx, by, bz)) && !cells.contains(cell(bx, by - 1, bz))) {
            return;
        }
        if (player.getGameMode() == GameMode.SPECTATOR || player.isFlying() || player.isInsideVehicle()) {
            return;
        }
        Block padBlock = to.getBlock();
        JumpPad pad = pads.get(JumpPad.key(padBlock.getLocation()));
        if (pad == null) {
            padBlock = padBlock.getRelative(0, -1, 0);
            pad = pads.get(JumpPad.key(padBlock.getLocation()));
        }
        if (pad == null || padBlock.getType() != pad.plate) {
            return;
        }
        if (!player.hasPermission("shjumppads.use") || (pad.permission != null && !player.hasPermission(pad.permission))) {
            return;
        }
        launch(player, pad, event.getFrom(), to);
    }

    private void launch(Player player, JumpPad pad, Location from, Location to) {
        Location aim = player.getLocation().clone();
        if (pad.direction != null) {
            aim.setYaw(pad.direction.floatValue());
        } else if (launchWalking) {
            Vector walk = to.toVector().subtract(from.toVector()).setY(0);
            if (walk.lengthSquared() > 1.0E-4) {
                aim.setDirection(walk);
            }
        }
        aim.setPitch((float) -pad.angle);
        Vector velocity = aim.getDirection().normalize().multiply(pad.power / POWER_DIVISOR);

        UUID uuid = player.getUniqueId();
        launched.add(uuid);
        fallProtected.add(uuid);
        // Sans ça, le serveur expulse pour "vol" pendant la montée (allow-flight=false)
        boolean grantedFlight = !player.getAllowFlight();
        if (grantedFlight) {
            player.setAllowFlight(true);
            flightGranted.add(uuid);
        }
        playLaunchFeedback(player);
        new LaunchTask(player, velocity, resolveTrail(pad), grantedFlight).runTaskTimer(this, 0L, 1L);
    }

    private final class LaunchTask extends BukkitRunnable {
        private final Player player;
        private final Vector motion;
        private final Particle trail;
        private final boolean grantedFlight;
        private int ticks;

        LaunchTask(Player player, Vector velocity, Particle trail, boolean grantedFlight) {
            this.player = player;
            this.motion = velocity.clone();
            this.trail = trail;
            this.grantedFlight = grantedFlight;
        }

        @Override
        public void run() {
            ticks++;
            boolean landed = ticks > 4 && player.isOnGround();
            if (!player.isOnline() || player.isDead() || landed || player.isInWater() || player.isInLava()
                || player.isGliding() || player.getGameMode() == GameMode.SPECTATOR || ticks > MAX_FLIGHT_TICKS) {
                finish();
                return;
            }
            player.setVelocity(motion);
            player.setFallDistance(0f);
            if (trail != null) {
                Location at = player.getLocation();
                if (trail == Particle.DUST) {
                    at.getWorld().spawnParticle(Particle.DUST, at, trailDensity, 0.1, 0.1, 0.1, 0, trailDust);
                } else {
                    at.getWorld().spawnParticle(trail, at, trailDensity, 0.1, 0.1, 0.1, 0);
                }
            }
            motion.setY(motion.getY() - GRAVITY);
            motion.multiply(DRAG);
        }

        private void finish() {
            cancel();
            UUID uuid = player.getUniqueId();
            launched.remove(uuid);
            flightGranted.remove(uuid);
            if (grantedFlight && player.isOnline()
                && player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR) {
                player.setFlying(false);
                player.setAllowFlight(false);
            }
            // Protection de chute encore une seconde après l'atterrissage
            Bukkit.getScheduler().runTaskLater(SHJumpPads.this, () -> {
                if (!launched.contains(uuid)) {
                    fallProtected.remove(uuid);
                }
            }, 20L);
        }
    }

    private Particle resolveTrail(JumpPad pad) {
        if (pad.particle != null) {
            return pad.particle.equalsIgnoreCase("NONE") ? null : parseParticle(pad.particle, Particle.DUST);
        }
        return trailEnabled ? Particle.DUST : null;
    }

    private void playLaunchFeedback(Player player) {
        Location loc = player.getLocation();
        if (launchEffect != null) {
            loc.getWorld().playEffect(loc, launchEffect, null);
        }
        if (soundEnabled) {
            if (soundWorld) {
                loc.getWorld().playSound(loc, soundName, soundVolume, soundPitch);
            } else {
                player.playSound(loc, soundName, soundVolume, soundPitch);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFall(EntityDamageEvent event) {
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL && fallProtected.contains(event.getEntity().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (!blockTeleporting || !launched.contains(event.getPlayer().getUniqueId())) {
            return;
        }
        PlayerTeleportEvent.TeleportCause cause = event.getCause();
        if (cause == PlayerTeleportEvent.TeleportCause.ENDER_PEARL || cause == PlayerTeleportEvent.TeleportCause.CONSUMABLE_EFFECT) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        launched.remove(uuid);
        fallProtected.remove(uuid);
        flightGranted.remove(uuid);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (cleanUp && pads.remove(JumpPad.key(event.getBlock().getLocation())) != null) {
            savePads();
            send(event.getPlayer(), "broken");
        }
    }

    // ------------------------------------------------------------------ visualiseur

    private void drawVisualizers() {
        if (!visualizerEnabled || pads.isEmpty()) {
            return;
        }
        visualizerAngle = (visualizerAngle + visualizerSpeed * 0.1) % (Math.PI * 2);
        double dx = Math.cos(visualizerAngle) * visualizerRadius;
        double dz = Math.sin(visualizerAngle) * visualizerRadius;
        for (JumpPad pad : pads.values()) {
            World world = Bukkit.getWorld(pad.world);
            if (world == null || !world.isChunkLoaded(pad.x >> 4, pad.z >> 4)) {
                continue;
            }
            Location center = pad.center(world);
            boolean watched = world.getPlayers().stream().anyMatch(p -> p.getLocation().distanceSquared(center) < 32 * 32);
            if (watched) {
                spawnVisualizer(world, center.clone().add(dx, 0, dz));
                spawnVisualizer(world, center.clone().add(-dx, 0, -dz));
            }
        }
    }

    private void spawnVisualizer(World world, Location at) {
        if (visualizerParticle == Particle.DUST) {
            world.spawnParticle(Particle.DUST, at, 1, 0, 0, 0, 0, visualizerDust);
        } else {
            world.spawnParticle(visualizerParticle, at, 1, 0, 0, 0, 0);
        }
    }

    // ------------------------------------------------------------------ commandes

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> {
                if (check(sender, "shjumppads.reload")) {
                    loadConfiguration();
                    loadPads();
                    send(sender, "reloaded", "{count}", String.valueOf(pads.size()));
                }
            }
            case "list" -> {
                if (check(sender, "shjumppads.list")) {
                    send(sender, "list-header", "{count}", String.valueOf(pads.size()));
                    pads.values().stream().limit(50).forEach(pad -> send(sender, "list-line",
                        "{world}", pad.world, "{x}", String.valueOf(pad.x), "{y}", String.valueOf(pad.y),
                        "{z}", String.valueOf(pad.z), "{power}", fmt(pad.power), "{angle}", fmt(pad.angle)));
                }
            }
            case "create", "delete", "set", "info" -> {
                if (!(sender instanceof Player player)) {
                    send(sender, "player-only");
                } else if (check(sender, "shjumppads." + sub)) {
                    handleBlockCommand(player, sub, args);
                }
            }
            default -> send(sender, "usage");
        }
        return true;
    }

    private void handleBlockCommand(Player player, String sub, String[] args) {
        Block target = player.getTargetBlockExact(5);
        if (target == null || !isPadMaterial(target.getType())) {
            Block under = player.getLocation().getBlock();
            target = isPadMaterial(under.getType()) ? under : under.getRelative(0, -1, 0);
        }
        if (!isPadMaterial(target.getType())) {
            send(player, sub.equals("create") ? "invalid-target" : "not-a-pad");
            return;
        }
        String key = JumpPad.key(target.getLocation());
        JumpPad pad = pads.get(key);
        if (sub.equals("create")) {
            create(player, target, key, pad, args);
            return;
        }
        if (pad == null) {
            send(player, "not-a-pad");
            return;
        }
        switch (sub) {
            case "delete" -> {
                pads.remove(key);
                savePads();
                send(player, "deleted");
            }
            case "info" -> send(player, "info", "{world}", pad.world, "{x}", String.valueOf(pad.x),
                "{y}", String.valueOf(pad.y), "{z}", String.valueOf(pad.z), "{plate}", pad.plate.name(),
                "{power}", fmt(pad.power), "{angle}", fmt(pad.angle),
                "{direction}", pad.direction == null ? "auto" : fmt(pad.direction),
                "{particle}", pad.particle == null ? "défaut" : pad.particle);
            default -> setField(player, pad, args);
        }
    }

    private void create(Player player, Block target, String key, JumpPad existing, String[] args) {
        if (existing != null) {
            send(player, "already-exists");
            return;
        }
        Double power = args.length > 1 ? parseDouble(player, args[1]) : Double.valueOf(10);
        Double angle = args.length > 2 ? parseDouble(player, args[2]) : Double.valueOf(45);
        if (power == null || angle == null) {
            return;
        }
        if (power > maxPower && !player.hasPermission("shjumppads.maxpower.bypass")) {
            send(player, "max-power", "{max}", fmt(maxPower));
            return;
        }
        JumpPad pad = new JumpPad(target.getWorld().getName(), target.getX(), target.getY(), target.getZ());
        pad.plate = target.getType();
        pad.power = power;
        pad.angle = angle;
        if (args.length > 3 && !args[3].equalsIgnoreCase("auto")) {
            pad.direction = args[3].equalsIgnoreCase("here") ? Double.valueOf(player.getLocation().getYaw()) : parseDouble(player, args[3]);
            if (pad.direction == null) {
                return;
            }
        }
        pad.owners.add(player.getUniqueId().toString());
        pads.put(key, pad);
        indexPad(pad);
        savePads();
        send(player, "created", "{power}", fmt(power), "{angle}", fmt(angle));
    }

    private void setField(Player player, JumpPad pad, String[] args) {
        if (args.length < 3) {
            send(player, "set-usage");
            return;
        }
        String field = args[1].toLowerCase(Locale.ROOT);
        String value = args[2];
        boolean none = value.equalsIgnoreCase("none");
        switch (field) {
            case "power" -> {
                Double v = parseDouble(player, value);
                if (v == null) {
                    return;
                }
                if (v > maxPower && !player.hasPermission("shjumppads.maxpower.bypass")) {
                    send(player, "max-power", "{max}", fmt(maxPower));
                    return;
                }
                pad.power = v;
            }
            case "angle" -> {
                Double v = parseDouble(player, value);
                if (v == null) {
                    return;
                }
                pad.angle = v;
            }
            case "direction" -> {
                if (none || value.equalsIgnoreCase("auto")) {
                    pad.direction = null;
                } else if (value.equalsIgnoreCase("here")) {
                    pad.direction = (double) player.getLocation().getYaw();
                } else {
                    Double v = parseDouble(player, value);
                    if (v == null) {
                        return;
                    }
                    pad.direction = v;
                }
            }
            case "particle" -> pad.particle = none ? "NONE" : value.toUpperCase(Locale.ROOT);
            case "permission" -> pad.permission = none ? null : value;
            default -> {
                send(player, "set-usage");
                return;
            }
        }
        savePads();
        send(player, "set", "{key}", field, "{value}", value);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return StringUtil.copyPartialMatches(args[0], List.of("create", "delete", "set", "info", "list", "reload"), new ArrayList<>());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
            return StringUtil.copyPartialMatches(args[1], List.of("power", "angle", "direction", "particle", "permission"), new ArrayList<>());
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("create")) {
            return StringUtil.copyPartialMatches(args[3], List.of("auto", "here"), new ArrayList<>());
        }
        return Collections.emptyList();
    }

    // ------------------------------------------------------------------ utilitaires

    private static boolean isPadMaterial(Material material) {
        return material == Material.SLIME_BLOCK || material.name().endsWith("_PRESSURE_PLATE");
    }

    private boolean check(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) {
            return true;
        }
        send(sender, "no-permission");
        return false;
    }

    private Double parseDouble(CommandSender sender, String raw) {
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            send(sender, "invalid-number", "{value}", raw);
            return null;
        }
    }

    private static String fmt(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.format(Locale.ROOT, "%.1f", value);
    }

    private void send(CommandSender sender, String key, String... replacements) {
        String message = getConfig().getString("messages." + key, key);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            message = message.replace(replacements[i], replacements[i + 1]);
        }
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', prefix + message));
    }

    /**
     * Accepte les noms de l'ancien plugin (REDSTONE, SLIME) et les noms actuels.
     */
    private Particle parseParticle(String name, Particle fallback) {
        if (name == null) {
            return fallback;
        }
        String upper = name.toUpperCase(Locale.ROOT);
        if (upper.equals("REDSTONE")) {
            return Particle.DUST;
        }
        if (upper.equals("SLIME")) {
            return Particle.ITEM_SLIME;
        }
        try {
            return Particle.valueOf(upper);
        } catch (IllegalArgumentException e) {
            getLogger().warning("Particule inconnue : " + name);
            return fallback;
        }
    }

    private static Color parseColor(String raw) {
        if (raw == null) {
            return Color.RED;
        }
        if (raw.startsWith("#") && raw.length() == 7) {
            try {
                return Color.fromRGB(Integer.parseInt(raw.substring(1), 16));
            } catch (NumberFormatException ignored) {
                return Color.RED;
            }
        }
        try {
            Object value = Color.class.getField(raw.toUpperCase(Locale.ROOT)).get(null);
            return value instanceof Color color ? color : Color.RED;
        } catch (ReflectiveOperationException e) {
            return Color.RED;
        }
    }
}
