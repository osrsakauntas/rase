package com.kodari.raceborder.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class Race {
    private String id;
    private String displayName;
    private String prefixColor;
    private String world;
    private BorderShape shape;
    private double centerX;
    private double centerZ;
    private double borderSize;
    private double spawnX;
    private double spawnY;
    private double spawnZ;
    private float spawnYaw;
    private float spawnPitch;
}