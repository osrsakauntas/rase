package com.kodari.raceborder.model;

import lombok.Getter;

import java.util.List;

@Getter
public class RaceQuest {
    private final String id;
    private final RacePhase phase;
    private final int difficulty;
    private final String name;
    private final String description;
    private final List<RaceQuestRequirement> requirements;

    public RaceQuest(String id, RacePhase phase, int difficulty, String name, String description,
                     List<RaceQuestRequirement> requirements) {
        this.id = id;
        this.phase = phase;
        this.difficulty = difficulty;
        this.name = name;
        this.description = description;
        this.requirements = List.copyOf(requirements);
    }
}