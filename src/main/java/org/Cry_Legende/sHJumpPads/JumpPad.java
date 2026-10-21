package org.Cry_Legende.sHJumpPads;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

/**
 * Une plaque de saut. La clé "monde, x, y, z" et les champs reprennent le format de jumppads.yml
 * de l'ancien plugin JumpPads, ce qui permet de garder le fichier tel quel.
 */
public class JumpPad {

    final String world;
    final int x, y, z;
    Material plate;
    double power;
    double angle;
    Double direction;      // null : direction de marche (ou regard) du joueur
    String particle;       // null : particule de traînée par défaut, "NONE" : pas de traînée
    String permission;     // null : aucune permission spécifique
    final List<String> owners = new ArrayList<>();

    JumpPad(String world, int x, int y, int z) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    static String key(String world, int x, int y, int z) {
        return world + ", " + x + ", " + y + ", " + z;
    }

    static String key(Location loc) {
        return key(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    String key() {
        return key(world, x, y, z);
    }

    /**
     * Lit une entrée de jumppads.yml ; retourne null si la clé ou le bloc sont illisibles.
     */
    static JumpPad fromConfig(String key, ConfigurationSection section) {
        String[] parts = key.split(",");
        if (parts.length != 4 || section == null) {
            return null;
        }
        try {
            JumpPad pad = new JumpPad(parts[0].trim(), Integer.parseInt(parts[1].trim()),
                Integer.parseInt(parts[2].trim()), Integer.parseInt(parts[3].trim()));
            pad.plate = Material.matchMaterial(section.getString("plate", "SLIME_BLOCK"));
            if (pad.plate == null) {
                return null;
            }
            pad.power = section.getDouble("power", 10.0);
            pad.angle = section.getDouble("angle", 45.0);
            pad.direction = section.isSet("direction") ? section.getDouble("direction") : null;
            pad.particle = section.getString("particle");
            pad.permission = section.getString("permission");
            pad.owners.addAll(section.getStringList("owners"));
            return pad;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    void save(ConfigurationSection section) {
        section.set("owners", owners);
        section.set("plate", plate.name());
        section.set("power", power);
        section.set("angle", angle);
        section.set("direction", direction);
        section.set("particle", particle);
        section.set("permission", permission);
    }

    Location center(World w) {
        return new Location(w, x + 0.5, y + (plate == Material.SLIME_BLOCK ? 1.05 : 0.1), z + 0.5);
    }
}
