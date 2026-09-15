package com.kodari.raceborder.api;

import com.kodari.raceborder.model.Profession;
import org.bukkit.entity.Player;

@FunctionalInterface
public interface ProfessionProvider {
    Profession getProfession(Player player);
}