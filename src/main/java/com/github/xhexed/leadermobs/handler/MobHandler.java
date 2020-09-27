package com.github.xhexed.leadermobs.handler;

import com.github.xhexed.leadermobs.Reward;
import com.github.xhexed.leadermobs.data.MobDamageInfo;
import com.github.xhexed.leadermobs.utils.Pair;
import com.github.xhexed.leadermobs.utils.Utils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static com.github.xhexed.leadermobs.LeaderMobs.getInstance;
import static com.github.xhexed.leadermobs.utils.Utils.*;
import static org.bukkit.Bukkit.getScheduler;

public class MobHandler {
    private static final Map<Entity, MobDamageInfo> data = new ConcurrentHashMap<>();
    
    static {
        getScheduler().runTaskTimerAsynchronously(getInstance(), () -> {
            for (final Entity entity : data.keySet()) {
                if (!entity.isValid()) {
                    data.remove(entity);
                }
            }
        }, 0, 200);
    }

    public static void onMobSpawn(final Entity entity, final String mobName) {
        data.put(entity, new MobDamageInfo(new HashMap<>(), new HashMap<>()));

        final Location location = entity.getLocation();
        final int x = location.getBlockX();
        final int y = location.getBlockY();
        final int z = location.getBlockZ();

        final FileConfiguration config = getInstance().getConfig();
        if (!config.getBoolean("Messages.MobSpawn.broadcast", true)) return;

        getScheduler().runTaskLater(getInstance(), () -> {
            config.getStringList("Messages.MobSpawn.messages").stream()
                    .map(message -> getMobSpawnMessage(mobName, x, y, z, message))
                    .forEach(Utils::sendMessage);

            getScheduler().runTaskLater(getInstance(), () -> {
                if (config.getBoolean("Messages.MobSpawn.title.enabled", false)) {
                    Bukkit.getOnlinePlayers().forEach((p) -> sendTitle(p, ChatColor.translateAlternateColorCodes('&', getMobSpawnMessage(mobName, x, y, z, config.getString("Messages.MobSpawn.title.title", ""))),
                            ChatColor.translateAlternateColorCodes('&', getMobSpawnMessage(mobName, x, y, z, config.getString("Messages.MobSpawn.title.subTitle", ""))),
                            config.getInt("Messages.MobSpawn.title.fadeIn", 0),
                            config.getInt("Messages.MobSpawn.title.stay", 0),
                            config.getInt("Messages.MobSpawn.title.fadeOut", 0)));
                }
            }, config.getLong("Messages.MobSpawn.title.delay", 0));

            getScheduler().runTaskLater(getInstance(), () -> {
                if (config.getBoolean("Messages.MobSpawn.actionbar.enabled", false)) {
                    Bukkit.getOnlinePlayers().forEach((p) -> sendActionBar(p, ChatColor.translateAlternateColorCodes('&', getMobSpawnMessage(mobName, x, y, z, config.getString("Messages.MobSpawn.actionbar.message", "")))));
                }
            }, config.getLong("Messages.MobSpawn.actionbar.delay", 0));
        }, config.getLong("Messages.MobSpawn.delay", 0));
    }

    public static void onPlayerDamage(final UUID player, final Entity entity, final Double damage) {
        final MobDamageInfo info = data.containsKey(entity) ? data.get(entity) :
                new MobDamageInfo(new HashMap<>(), new HashMap<>());
        final Map<UUID, Double> damageDealtList = info.getDamageDealt();
        damageDealtList.put(player, damageDealtList.containsKey(player) ?
                damageDealtList.get(player) + damage : damage);
        data.put(entity, new MobDamageInfo(damageDealtList, info.getDamageTaken()));
    }

    public static void onMobDamage(final Entity entity, final UUID player, final Double damage) {
        final MobDamageInfo info = data.containsKey(entity) ? data.get(entity) :
                new MobDamageInfo(new HashMap<>(), new HashMap<>());
        final Map<UUID, Double> damageTakenList = info.getDamageTaken();
        damageTakenList.put(player, damageTakenList.containsKey(player) ?
                damageTakenList.get(player) + damage : damage);
        data.put(entity, new MobDamageInfo(info.getDamageDealt(), damageTakenList));
    }

    public static void onMobDeath(final Entity entity, final String mobName, final String internalName) {
        if (!data.containsKey(entity)) return;
        final MobDamageInfo damageInfo = data.get(entity);
        damageInfo.calculateTop();
        final FileConfiguration config = getInstance().getConfig();
        if (damageInfo.getDamageDealt().keySet().size() < config.getInt("PlayersRequired")) return;

        getScheduler().runTaskLater(getInstance(), () -> {
            if (config.getBoolean("Messages.MobDead.damageDealt.broadcast", true)) {
                getScheduler().runTaskLater(getInstance(), () -> {
                    if (!(damageInfo.getTopDamageDealt().isEmpty() && config.getBoolean("Messages.MobDead.damageDealt.hide-empty-header", false))) {
                        String damageDealtHeader = config.getString("Messages.MobDead.damageDealt.header", "");
                        damageDealtHeader = NAME.matcher(damageDealtHeader != null ? damageDealtHeader : "").replaceAll(ChatColor.stripColor(mobName));
                        damageDealtHeader = replaceMobPlaceholder(damageDealtHeader, damageInfo);
                        sendMessage(damageDealtHeader);
                    }

                    sendPlaceMessage(damageInfo.getTotalDamageDealt(), config, damageInfo.getTopDamageDealt(), config.getString("Messages.MobDead.damageDealt.message", ""));

                    getScheduler().runTaskLater(getInstance(), () -> {
                        if (config.getBoolean("Messages.MobDead.damageDealt.title.enabled", false)) {
                            Bukkit.getOnlinePlayers().forEach((p) -> sendTitle(p, ChatColor.translateAlternateColorCodes('&', getMobDeathMessage(damageInfo, mobName, config.getString("Messages.MobDead.damageDealt.title.title", ""))),
                                                                               ChatColor.translateAlternateColorCodes('&', getMobDeathMessage(damageInfo, mobName, config.getString("Messages.MobDead.damageDealt.title.subTitle", ""))),
                                                                               config.getInt("Messages.MobDead.damageDealt.title.fadeIn", 0),
                                                                               config.getInt("Messages.MobDead.damageDealt.title.stay", 0),
                                                                               config.getInt("Messages.MobDead.damageDealt.title.fadeOut", 0)));
                        }
                    }, config.getLong("Messages.MobDead.damageDealt.title.delay", 0));

                    getScheduler().runTaskLater(getInstance(), () -> {
                        if (config.getBoolean("Messages.MobDead.damageDealt.actionbar.enabled", false)) {
                            Bukkit.getOnlinePlayers().forEach((p) -> sendActionBar(p, ChatColor.translateAlternateColorCodes('&', getMobDeathMessage(damageInfo, mobName, config.getString("Messages.MobDead.damageDealt.actionbar.message", "")))));
                        }
                    }, config.getLong("Messages.MobDead.damageDealt.actionbar.delay", 0));

                    if (!(damageInfo.getTopDamageDealt().isEmpty() && config.getBoolean("Messages.MobDead.damageDealt.hide-empty-footer", false))) {
                        String damageDealtFooter = config.getString("Messages.MobDead.damageDealt.footer", "");
                        damageDealtFooter = NAME.matcher(damageDealtFooter != null ? damageDealtFooter : "").replaceAll(ChatColor.stripColor(mobName));
                        damageDealtFooter = replaceMobPlaceholder(damageDealtFooter, damageInfo);
                        sendMessage(damageDealtFooter);
                    }
                }, config.getLong("Messages.MobDead.damageDealt.delay", 0));
            }
            if (config.getBoolean("Messages.MobDead.damageTaken.broadcast", true)) {
                getScheduler().runTaskLater(getInstance(), () -> {
                    if (!(damageInfo.getTopDamageTaken().isEmpty() && config.getBoolean("Messages.MobDead.damageTaken.hide-empty-header", false))) {
                        String damageTakenheader = config.getString("Messages.MobDead.damageTaken.header", "");
                        damageTakenheader = NAME.matcher(damageTakenheader != null ? damageTakenheader : "").replaceAll(ChatColor.stripColor(mobName));
                        damageTakenheader = replaceMobPlaceholder(damageTakenheader, damageInfo);
                        sendMessage(damageTakenheader);
                    }

                    sendPlaceMessage(damageInfo.getTotalDamageTaken(), config, damageInfo.getTopDamageTaken(), config.getString("Messages.MobDead.damageTaken.message", ""));

                    getScheduler().runTaskLater(getInstance(), () -> {
                        if (config.getBoolean("Messages.MobDead.damageTaken.title.enabled", false)) {
                            Bukkit.getOnlinePlayers().forEach((p) -> sendTitle(p, ChatColor.translateAlternateColorCodes('&', getMobDeathMessage(damageInfo, mobName, config.getString("Messages.MobDead.damageTaken.title.title", ""))),
                                                                               ChatColor.translateAlternateColorCodes('&', getMobDeathMessage(damageInfo, mobName, config.getString("Messages.MobDead.damageTaken.title.subTitle", ""))),
                                                                               config.getInt("Messages.MobDead.damageTaken.title.fadeIn", 0),
                                                                               config.getInt("Messages.MobDead.damageTaken.title.stay", 0),
                                                                               config.getInt("Messages.MobDead.damageTaken.title.fadeOut", 0)));
                        }
                    }, config.getLong("Messages.MobDead.damageTaken.title.delay", 0));

                    getScheduler().runTaskLater(getInstance(), () -> {
                        if (config.getBoolean("Messages.MobDead.damageTaken.actionbar.enabled", false)) {
                            Bukkit.getOnlinePlayers().forEach((p) -> sendActionBar(p, ChatColor.translateAlternateColorCodes('&', getMobDeathMessage(damageInfo, mobName, config.getString("Messages.MobDead.damageTaken.actionbar.message", "")))));
                        }
                    }, config.getLong("Messages.MobDead.damageTaken.actionbar.delay", 0));

                    if (!(damageInfo.getTopDamageTaken().isEmpty() && config.getBoolean("Messages.MobDead.damageTaken.hide-empty-footer", false))) {
                        String damageTakenFooter = config.getString("Messages.MobDead.damageTaken.footer", "");
                        damageTakenFooter = NAME.matcher(damageTakenFooter != null ? damageTakenFooter : "").replaceAll(ChatColor.stripColor(mobName));
                        damageTakenFooter = replaceMobPlaceholder(damageTakenFooter, damageInfo);
                        sendMessage(damageTakenFooter);
                    }
                }, config.getLong("Messages.MobDead.damageTaken.delay", 0));
            }
        }, config.getLong("Messages.MobDead.delay", 0));

        new Reward(internalName,
                damageInfo.getTopDamageDealt().stream().map(Pair::getValue).collect(Collectors.toList()),
                damageInfo.getTopDamageTaken().stream().map(Pair::getValue).collect(Collectors.toList()));
        data.remove(entity);
    }

    private static void sendPlaceMessage(final double total, final ConfigurationSection config, final List<? extends Pair<Double, UUID>> damageList, final String damageMessage) {
        for (int place = 1; place <= damageList.size(); place++) {
            if (place >= config.getInt("PlacesToBroadcast")) break;

            final Pair<Double, UUID> info = damageList.get(place - 1);

            final Double damage = info.getKey();
            final UUID uuid = info.getValue();
            final OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);

            String message = damageMessage;
            message = PLACE_PREFIX.matcher(message != null ? message : "").replaceAll(config.getString(config.contains("PlacePrefix." + place) ? "PlacePrefix." + place : "PlacePrefix.default", ""));
            message = DAMAGE_POS.matcher(message).replaceAll(Integer.toString(place));
            message = PLAYER_NAME.matcher(message).replaceAll(player.getName());
            message = DAMAGE.matcher(message).replaceAll(DOUBLE_FORMAT.format(damage));
            message = PERCENTAGE.matcher(message).replaceAll(DOUBLE_FORMAT.format(getPercentage(damage, total)));
            message = replacePlaceholder(player, message);
            sendMessage(ChatColor.translateAlternateColorCodes('&', message));
        }
    }
}