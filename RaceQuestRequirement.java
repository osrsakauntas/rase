package com.kodari.raceborder.model;

import lombok.Getter;

@Getter
public class RaceQuestRequirement {
    private final int index;
    private final Profession profession;
    private final QuestAction action;
    private final String target;
    private final int amount;

    public RaceQuestRequirement(int index, Profession profession, QuestAction action, String target, int amount) {
        this.index = index;
        this.profession = profession;
        this.action = action;
        this.target = target;
        this.amount = amount;
    }
}