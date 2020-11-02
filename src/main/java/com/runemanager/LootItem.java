package com.runemanager;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@AllArgsConstructor
@Getter
@EqualsAndHashCode
public class LootItem {
    private int id;
    private String name;
    private int quantity;
}
