package me.xemor.minclaimdistance;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.william278.huskclaims.api.HuskClaimsAPI;
import net.william278.huskclaims.claim.Claim;
import net.william278.huskclaims.claim.ClaimWorld;
import net.william278.huskclaims.claim.Region;
import net.william278.huskclaims.event.BukkitCreateClaimEvent;
import net.william278.huskclaims.event.BukkitResizeClaimEvent;
import net.william278.huskclaims.position.Position;
import net.william278.huskclaims.trust.TrustLevel;
import net.william278.huskclaims.trust.UserGroup;
import org.bukkit.ChatColor;
import org.bukkit.entity.Husk;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class MinClaimDistance extends JavaPlugin implements Listener {

    private long minDistance = 0;
    private String message;

    @Override
    public void onEnable() {
        // Plugin startup logic
        this.getServer().getPluginManager().registerEvents(this, this);
        saveDefaultConfig();
        minDistance = getConfig().getInt("minDistance", 0);
        message = getConfig().getString("message");
    }

    @EventHandler
    public void onClaim(BukkitCreateClaimEvent event) {
        if (!event.isCancelled()) {
            if (event.getClaimOwner().isPresent()) {
                if (isClaimTooClose(event.getClaimOwner().get().getUuid(), event.getRegion(), event.getClaimWorld())) {
                    event.setCancelled(true);
                    event.getPlayer().sendMessage(
                            MiniMessage.miniMessage().deserialize(message,
                                    TagResolver.builder()
                                            .tag("player", Tag.inserting(Component.text(event.getPlayer().getName())))
                                            .build()
                            )
                    );
                }
            }
        }
    }

    @EventHandler
    public void onClaimChange(BukkitResizeClaimEvent event) {
        if (!event.isCancelled()) {
            if (event.getClaim().getOwner().isPresent()) {
                if (isClaimTooClose(
                        event.getClaim().getOwner().get(),
                        event.getClaim().getRegion(),
                        event.getClaimWorld()
                )) {
                    event.setCancelled(true);
                    event.getPlayer().sendMessage(
                            MiniMessage.miniMessage().deserialize(message,
                                    TagResolver.builder()
                                            .tag("player", Tag.inserting(Component.text(event.getPlayer().getName())))
                                            .build()
                            )
                    );
                }
            }
        }
    }

    public boolean isClaimTooClose(UUID attemptingOwner, Region region, ClaimWorld claimWorld) {
        Region.Point a = region.getNearCorner(); // a has lower x and z
        Region.Point b = region.getFarCorner(); // b has higher x and z
        Region expandedRegion = Region.from(
                Position.at(a.getBlockX() - minDistance, 0.0, a.getBlockZ() - minDistance, null),
                Position.at(b.getBlockX() + minDistance, 0.0, b.getBlockZ() + minDistance, null)
        );
        List<UserGroup> userGroups = HuskClaimsAPI.getInstance().getUserGroups(attemptingOwner);
        return claimWorld.getClaims()
                .stream()
                .filter((claim) -> claim.getOwner().isEmpty() || !claim.getOwner().get().equals(attemptingOwner))
                .filter((claim) -> !isTrusted(claim, claimWorld, attemptingOwner))
                .map(Claim::getRegion)
                .anyMatch(
                        (r) -> r.overlaps(expandedRegion)
                );
    }

    public boolean isTrusted(Claim claim, ClaimWorld claimWorld, UUID checkAgainst) {
        var optionalTrustLevel = HuskClaimsAPI.getInstance().getTrustLevel(claim, claimWorld, HuskClaimsAPI.getInstance().getOnlineUser(checkAgainst));
        return optionalTrustLevel.filter((t) -> t.getPrivileges().contains(TrustLevel.Privilege.MANAGE_CHILD_CLAIMS)).isPresent();
    }
}
