#!/usr/bin/env python3
"""Generate srd-5.2-monsters.json with 20 common D&D 5.5e SRD monsters."""

import json

MONSTERS = [
    {
        "sourceKey": "goblin", "name": "Goblin", "size": "Small", "type": "Humanoid",
        "alignment": "Neutral Evil", "ac": 15, "hp": "7 (2d6)", "speed": "30 ft.",
        "strScore": 8, "dexScore": 14, "conScore": 10, "intScore": 10, "wisScore": 8, "chaScore": 8,
        "skills": "Stealth +6", "senses": "darkvision 60 ft., passive Perception 9",
        "languages": "Common, Goblin",
        "traits": json.dumps([
            {"name": "Nimble Escape", "description": "The goblin can take the Disengage or Hide action as a bonus action on each of its turns."}
        ]),
        "actions": json.dumps([
            {"name": "Scimitar", "description": "Melee Weapon Attack: +4 to hit, reach 5 ft., one target. Hit: 5 (1d6 + 2) slashing damage."},
            {"name": "Shortbow", "description": "Ranged Weapon Attack: +4 to hit, range 80/320 ft., one target. Hit: 5 (1d6 + 2) piercing damage."}
        ]),
        "cr": "1/4"
    },
    {
        "sourceKey": "kobold", "name": "Kobold", "size": "Small", "type": "Dragon",
        "alignment": "Lawful Evil", "ac": 12, "hp": "5 (2d6 - 2)", "speed": "30 ft.",
        "strScore": 7, "dexScore": 15, "conScore": 9, "intScore": 8, "wisScore": 7, "chaScore": 8,
        "senses": "darkvision 60 ft., passive Perception 8", "languages": "Common, Draconic",
        "traits": json.dumps([
            {"name": "Pack Tactics", "description": "The kobold has advantage on an attack roll against a creature if at least one of the kobold's allies is within 5 feet of the creature and the ally isn't incapacitated."},
            {"name": "Sunlight Sensitivity", "description": "While in sunlight, the kobold has disadvantage on attack rolls."}
        ]),
        "actions": json.dumps([
            {"name": "Dagger", "description": "Melee or Ranged Weapon Attack: +4 to hit, reach 5 ft. or range 20/60 ft., one target. Hit: 4 (1d4 + 2) piercing damage."},
            {"name": "Sling", "description": "Ranged Weapon Attack: +4 to hit, range 30/120 ft., one target. Hit: 4 (1d4 + 2) bludgeoning damage."}
        ]),
        "cr": "1/8"
    },
    {
        "sourceKey": "orc", "name": "Orc", "size": "Medium", "type": "Humanoid",
        "alignment": "Chaotic Evil", "ac": 13, "hp": "15 (2d8 + 6)", "speed": "30 ft.",
        "strScore": 16, "dexScore": 12, "conScore": 16, "intScore": 7, "wisScore": 11, "chaScore": 10,
        "skills": "Intimidation +2", "senses": "darkvision 60 ft., passive Perception 10",
        "languages": "Common, Orc",
        "traits": json.dumps([
            {"name": "Aggressive", "description": "As a bonus action, the orc can move up to its speed toward a hostile creature that it can see."}
        ]),
        "actions": json.dumps([
            {"name": "Greataxe", "description": "Melee Weapon Attack: +5 to hit, reach 5 ft., one target. Hit: 9 (1d12 + 3) slashing damage."},
            {"name": "Javelin", "description": "Melee or Ranged Weapon Attack: +5 to hit, reach 5 ft. or range 30/120 ft., one target. Hit: 6 (1d6 + 3) piercing damage."}
        ]),
        "cr": "1/2"
    },
    {
        "sourceKey": "skeleton", "name": "Skeleton", "size": "Medium", "type": "Undead",
        "alignment": "Lawful Evil", "ac": 13, "hp": "13 (2d8 + 4)", "speed": "30 ft.",
        "strScore": 10, "dexScore": 14, "conScore": 15, "intScore": 6, "wisScore": 8, "chaScore": 5,
        "damageVulnerabilities": "bludgeoning", "damageImmunities": "poison",
        "conditionImmunities": "exhaustion, poisoned",
        "senses": "darkvision 60 ft., passive Perception 9",
        "languages": "understands all languages it knew in life but can't speak",
        "actions": json.dumps([
            {"name": "Shortsword", "description": "Melee Weapon Attack: +4 to hit, reach 5 ft., one target. Hit: 5 (1d6 + 2) piercing damage."},
            {"name": "Shortbow", "description": "Ranged Weapon Attack: +4 to hit, range 80/320 ft., one target. Hit: 5 (1d6 + 2) piercing damage."}
        ]),
        "cr": "1/4"
    },
    {
        "sourceKey": "zombie", "name": "Zombie", "size": "Medium", "type": "Undead",
        "alignment": "Neutral Evil", "ac": 8, "hp": "22 (3d8 + 9)", "speed": "20 ft.",
        "strScore": 13, "dexScore": 6, "conScore": 16, "intScore": 3, "wisScore": 6, "chaScore": 5,
        "damageImmunities": "poison", "conditionImmunities": "poisoned",
        "senses": "darkvision 60 ft., passive Perception 8",
        "languages": "understands all languages it knew in life but can't speak",
        "traits": json.dumps([
            {"name": "Undead Fortitude", "description": "If damage reduces the zombie to 0 hit points, it must make a Constitution saving throw with a DC of 5 + the damage taken, unless the damage is radiant or from a critical hit. On a success, the zombie drops to 1 hit point instead."}
        ]),
        "actions": json.dumps([
            {"name": "Slam", "description": "Melee Weapon Attack: +3 to hit, reach 5 ft., one target. Hit: 4 (1d6 + 1) bludgeoning damage."}
        ]),
        "cr": "1/4"
    },
    {
        "sourceKey": "ghoul", "name": "Ghoul", "size": "Medium", "type": "Undead",
        "alignment": "Chaotic Evil", "ac": 12, "hp": "22 (5d8)", "speed": "30 ft.",
        "strScore": 13, "dexScore": 15, "conScore": 10, "intScore": 7, "wisScore": 10, "chaScore": 6,
        "damageImmunities": "poison", "conditionImmunities": "charmed, exhaustion, poisoned",
        "senses": "darkvision 60 ft., passive Perception 10", "languages": "Common",
        "actions": json.dumps([
            {"name": "Bite", "description": "Melee Weapon Attack: +2 to hit, reach 5 ft., one creature. Hit: 9 (2d6 + 2) piercing damage."},
            {"name": "Claws", "description": "Melee Weapon Attack: +4 to hit, reach 5 ft., one target. Hit: 7 (2d4 + 2) slashing damage. If the target is a creature other than an elf or undead, it must succeed on a DC 10 Constitution saving throw or be paralyzed for 1 minute. The target can repeat the saving throw at the end of each of its turns, ending the effect on itself on a success."}
        ]),
        "cr": "1"
    },
    {
        "sourceKey": "ghost", "name": "Ghost", "size": "Medium", "type": "Undead",
        "alignment": "Any Alignment", "ac": 11, "hp": "45 (10d8)", "speed": "0 ft., fly 40 ft. (hover)",
        "strScore": 7, "dexScore": 13, "conScore": 10, "intScore": 10, "wisScore": 12, "chaScore": 17,
        "damageResistances": "acid, fire, lightning, thunder; bludgeoning, piercing, and slashing from nonmagical attacks",
        "damageImmunities": "cold, necrotic, poison",
        "conditionImmunities": "charmed, exhaustion, frightened, grappled, paralyzed, petrified, poisoned, prone, restrained",
        "senses": "darkvision 60 ft., passive Perception 11", "languages": "any languages it knew in life",
        "traits": json.dumps([
            {"name": "Ethereal Sight", "description": "The ghost can see 60 feet into the Ethereal Plane when it is on the Material Plane, and vice versa."},
            {"name": "Incorporeal Movement", "description": "The ghost can move through other creatures and objects as if they were difficult terrain. It takes 5 (1d10) force damage if it ends its turn inside an object."}
        ]),
        "actions": json.dumps([
            {"name": "Withering Touch", "description": "Melee Weapon Attack: +5 to hit, reach 5 ft., one target. Hit: 17 (4d6 + 3) necrotic damage."},
            {"name": "Etherealness", "description": "The ghost enters the Ethereal Plane from the Material Plane, or vice versa."},
            {"name": "Horrifying Visage", "description": "Each non-undead creature within 60 feet of the ghost that can see it must succeed on a DC 13 Wisdom saving throw or be frightened for 1 minute."},
            {"name": "Possession (Recharge 6)", "description": "One humanoid that the ghost can see within 5 feet must succeed on a DC 13 Charisma saving throw or be possessed by the ghost."}
        ]),
        "cr": "4"
    },
    {
        "sourceKey": "ogre", "name": "Ogre", "size": "Large", "type": "Giant",
        "alignment": "Chaotic Evil", "ac": 11, "hp": "59 (7d10 + 21)", "speed": "40 ft.",
        "strScore": 19, "dexScore": 8, "conScore": 16, "intScore": 5, "wisScore": 7, "chaScore": 7,
        "senses": "darkvision 60 ft., passive Perception 8", "languages": "Common, Giant",
        "actions": json.dumps([
            {"name": "Greatclub", "description": "Melee Weapon Attack: +6 to hit, reach 5 ft., one target. Hit: 13 (2d8 + 4) bludgeoning damage."},
            {"name": "Javelin", "description": "Melee or Ranged Weapon Attack: +6 to hit, reach 5 ft. or range 30/120 ft., one target. Hit: 11 (2d6 + 4) piercing damage."}
        ]),
        "cr": "2"
    },
    {
        "sourceKey": "owlbear", "name": "Owlbear", "size": "Large", "type": "Monstrosity",
        "alignment": "Unaligned", "ac": 13, "hp": "59 (7d10 + 21)", "speed": "40 ft.",
        "strScore": 20, "dexScore": 12, "conScore": 17, "intScore": 3, "wisScore": 12, "chaScore": 7,
        "skills": "Perception +3", "senses": "darkvision 60 ft., passive Perception 13",
        "traits": json.dumps([
            {"name": "Keen Sight and Smell", "description": "The owlbear has advantage on Wisdom (Perception) checks that rely on sight or smell."}
        ]),
        "actions": json.dumps([
            {"name": "Multiattack", "description": "The owlbear makes two attacks: one with its beak and one with its claws."},
            {"name": "Beak", "description": "Melee Weapon Attack: +7 to hit, reach 5 ft., one creature. Hit: 10 (1d10 + 5) piercing damage."},
            {"name": "Claws", "description": "Melee Weapon Attack: +7 to hit, reach 5 ft., one target. Hit: 14 (2d8 + 5) slashing damage."}
        ]),
        "cr": "3"
    },
    {
        "sourceKey": "troll", "name": "Troll", "size": "Large", "type": "Giant",
        "alignment": "Chaotic Evil", "ac": 15, "hp": "84 (8d10 + 40)", "speed": "30 ft.",
        "strScore": 18, "dexScore": 13, "conScore": 20, "intScore": 7, "wisScore": 9, "chaScore": 7,
        "skills": "Perception +2", "senses": "darkvision 60 ft., passive Perception 12", "languages": "Giant",
        "traits": json.dumps([
            {"name": "Keen Smell", "description": "The troll has advantage on Wisdom (Perception) checks that rely on smell."},
            {"name": "Regeneration", "description": "The troll regains 10 hit points at the start of its turn. If the troll takes acid or fire damage, this trait doesn't function at the start of the troll's next turn. The troll dies only if it starts its turn with 0 hit points and doesn't regenerate."}
        ]),
        "actions": json.dumps([
            {"name": "Multiattack", "description": "The troll makes three attacks: one with its bite and two with its claws."},
            {"name": "Bite", "description": "Melee Weapon Attack: +7 to hit, reach 5 ft., one target. Hit: 7 (1d6 + 4) piercing damage."},
            {"name": "Claw", "description": "Melee Weapon Attack: +7 to hit, reach 5 ft., one target. Hit: 11 (2d6 + 4) slashing damage."}
        ]),
        "cr": "5"
    },
    {
        "sourceKey": "wyvern", "name": "Wyvern", "size": "Large", "type": "Dragon",
        "alignment": "Unaligned", "ac": 13, "hp": "110 (13d10 + 39)", "speed": "20 ft., fly 80 ft.",
        "strScore": 19, "dexScore": 10, "conScore": 16, "intScore": 5, "wisScore": 12, "chaScore": 6,
        "skills": "Perception +4", "senses": "darkvision 60 ft., passive Perception 14",
        "actions": json.dumps([
            {"name": "Multiattack", "description": "The wyvern makes two attacks: one with its bite and one with its stinger."},
            {"name": "Bite", "description": "Melee Weapon Attack: +7 to hit, reach 10 ft., one creature. Hit: 11 (2d6 + 4) piercing damage."},
            {"name": "Claws", "description": "Melee Weapon Attack: +7 to hit, reach 5 ft., one target. Hit: 13 (2d8 + 4) slashing damage."},
            {"name": "Stinger", "description": "Melee Weapon Attack: +7 to hit, reach 10 ft., one creature. Hit: 11 (2d6 + 4) piercing damage plus DC 15 Con save or 24 (7d6) poison damage."}
        ]),
        "cr": "6"
    },
    {
        "sourceKey": "frost-giant", "name": "Frost Giant", "size": "Huge", "type": "Giant",
        "alignment": "Neutral Evil", "ac": 15, "hp": "138 (12d12 + 60)", "speed": "40 ft.",
        "strScore": 23, "dexScore": 9, "conScore": 21, "intScore": 9, "wisScore": 10, "chaScore": 12,
        "strSave": 9, "conSave": 8, "wisSave": 3, "chaSave": 4,
        "skills": "Athletics +9, Perception +3", "damageImmunities": "cold",
        "senses": "passive Perception 13", "languages": "Giant",
        "actions": json.dumps([
            {"name": "Multiattack", "description": "The giant makes two greatsword attacks."},
            {"name": "Greatsword", "description": "Melee Weapon Attack: +9 to hit, reach 10 ft., one target. Hit: 25 (3d12 + 6) slashing damage."},
            {"name": "Rock", "description": "Ranged Weapon Attack: +9 to hit, range 60/240 ft., one target. Hit: 28 (4d10 + 6) bludgeoning damage."}
        ]),
        "cr": "8"
    },
    {
        "sourceKey": "adult-black-dragon", "name": "Adult Black Dragon", "size": "Huge", "type": "Dragon",
        "alignment": "Chaotic Evil", "ac": 19, "hp": "195 (17d12 + 85)", "speed": "40 ft., fly 80 ft., swim 40 ft.",
        "strScore": 23, "dexScore": 14, "conScore": 21, "intScore": 14, "wisScore": 13, "chaScore": 17,
        "dexSave": 7, "conSave": 10, "wisSave": 6, "chaSave": 8,
        "skills": "Perception +11, Stealth +7", "damageImmunities": "acid",
        "senses": "blindsight 60 ft., darkvision 120 ft., passive Perception 21", "languages": "Common, Draconic",
        "traits": json.dumps([
            {"name": "Amphibious", "description": "The dragon can breathe air and water."},
            {"name": "Legendary Resistance (3/Day)", "description": "If the dragon fails a saving throw, it can choose to succeed instead."}
        ]),
        "actions": json.dumps([
            {"name": "Multiattack", "description": "The dragon can use its Frightful Presence. It then makes three attacks: one with its bite and two with its claws."},
            {"name": "Bite", "description": "Melee Weapon Attack: +11 to hit, reach 10 ft., one target. Hit: 17 (2d10 + 6) piercing damage plus 4 (1d8) acid damage."},
            {"name": "Claw", "description": "Melee Weapon Attack: +11 to hit, reach 5 ft., one target. Hit: 13 (2d6 + 6) slashing damage."},
            {"name": "Tail", "description": "Melee Weapon Attack: +11 to hit, reach 15 ft., one target. Hit: 15 (2d8 + 6) bludgeoning damage."},
            {"name": "Frightful Presence", "description": "Each creature of the dragon's choice within 120 feet that is aware of it must succeed on a DC 16 Wisdom saving throw or become frightened for 1 minute."},
            {"name": "Acid Breath (Recharge 5-6)", "description": "The dragon exhales acid in a 60-foot line. Each creature must make a DC 18 Dexterity saving throw, taking 54 (12d8) acid damage on a failed save, or half on a success."}
        ]),
        "legendaryActions": json.dumps([
            {"name": "Detect", "description": "The dragon makes a Wisdom (Perception) check."},
            {"name": "Tail Attack", "description": "The dragon makes a tail attack."},
            {"name": "Wing Attack (Costs 2 Actions)", "description": "The dragon beats its wings. Each creature within 10 feet must succeed on a DC 19 Dex save or take 13 (2d6 + 6) bludgeoning damage and be knocked prone. The dragon can then fly up to half its speed."}
        ]),
        "legendaryDescription": "The dragon can take 3 legendary actions, choosing from the options below. Only one legendary action option can be used at a time and only at the end of another creature's turn. The dragon regains spent legendary actions at the start of its turn.",
        "cr": "14"
    },
    {
        "sourceKey": "imp", "name": "Imp", "size": "Tiny", "type": "Fiend",
        "alignment": "Lawful Evil", "ac": 13, "hp": "10 (3d4 + 3)", "speed": "20 ft., fly 40 ft.",
        "strScore": 6, "dexScore": 17, "conScore": 13, "intScore": 11, "wisScore": 12, "chaScore": 14,
        "skills": "Deception +4, Insight +3, Persuasion +4, Stealth +5",
        "damageResistances": "cold; bludgeoning, piercing, and slashing from nonmagical attacks that aren't silvered",
        "damageImmunities": "fire, poison", "conditionImmunities": "poisoned",
        "senses": "darkvision 120 ft., passive Perception 11", "languages": "Infernal, Common",
        "traits": json.dumps([
            {"name": "Shapechanger", "description": "The imp can use its action to polymorph into a beast form (rat, raven, or spider) or back into its true form."},
            {"name": "Devil's Sight", "description": "Magical darkness doesn't impede the imp's darkvision."},
            {"name": "Magic Resistance", "description": "The imp has advantage on saving throws against spells and other magical effects."}
        ]),
        "actions": json.dumps([
            {"name": "Sting", "description": "Melee Weapon Attack: +5 to hit, reach 5 ft., one target. Hit: 5 (1d4 + 3) piercing damage plus DC 11 Con save or 10 (3d6) poison damage."},
            {"name": "Invisibility", "description": "The imp magically turns invisible until it attacks or until its concentration ends."}
        ]),
        "cr": "1"
    },
    {
        "sourceKey": "commoner", "name": "Commoner", "size": "Medium", "type": "Humanoid",
        "alignment": "Any Alignment", "ac": 10, "hp": "4 (1d8)", "speed": "30 ft.",
        "strScore": 10, "dexScore": 10, "conScore": 10, "intScore": 10, "wisScore": 10, "chaScore": 10,
        "senses": "passive Perception 10", "languages": "any one language (usually Common)",
        "actions": json.dumps([
            {"name": "Club", "description": "Melee Weapon Attack: +2 to hit, reach 5 ft., one target. Hit: 2 (1d4) bludgeoning damage."}
        ]),
        "cr": "0"
    },
    {
        "sourceKey": "bandit", "name": "Bandit", "size": "Medium", "type": "Humanoid",
        "alignment": "Any Non-Lawful Alignment", "ac": 12, "hp": "11 (2d8 + 2)", "speed": "30 ft.",
        "strScore": 11, "dexScore": 12, "conScore": 12, "intScore": 10, "wisScore": 10, "chaScore": 10,
        "senses": "passive Perception 10", "languages": "any one language (usually Common)",
        "actions": json.dumps([
            {"name": "Scimitar", "description": "Melee Weapon Attack: +3 to hit, reach 5 ft., one target. Hit: 4 (1d6 + 1) slashing damage."},
            {"name": "Light Crossbow", "description": "Ranged Weapon Attack: +3 to hit, range 80/320 ft., one target. Hit: 5 (1d8 + 1) piercing damage."}
        ]),
        "cr": "1/8"
    },
    {
        "sourceKey": "cultist", "name": "Cultist", "size": "Medium", "type": "Humanoid",
        "alignment": "Any Non-Good Alignment", "ac": 12, "hp": "9 (2d8)", "speed": "30 ft.",
        "strScore": 11, "dexScore": 12, "conScore": 10, "intScore": 10, "wisScore": 11, "chaScore": 10,
        "skills": "Deception +2, Religion +2", "senses": "passive Perception 10",
        "languages": "any one language (usually Common)",
        "traits": json.dumps([
            {"name": "Dark Devotion", "description": "The cultist has advantage on saving throws against being charmed or frightened."}
        ]),
        "actions": json.dumps([
            {"name": "Scimitar", "description": "Melee Weapon Attack: +3 to hit, reach 5 ft., one creature. Hit: 4 (1d6 + 1) slashing damage."}
        ]),
        "cr": "1/8"
    },
    {
        "sourceKey": "dire-wolf", "name": "Dire Wolf", "size": "Large", "type": "Beast",
        "alignment": "Unaligned", "ac": 14, "hp": "37 (5d10 + 10)", "speed": "50 ft.",
        "strScore": 17, "dexScore": 15, "conScore": 15, "intScore": 3, "wisScore": 12, "chaScore": 7,
        "skills": "Perception +3, Stealth +4", "senses": "passive Perception 13",
        "traits": json.dumps([
            {"name": "Keen Hearing and Smell", "description": "The wolf has advantage on Wisdom (Perception) checks that rely on hearing or smell."},
            {"name": "Pack Tactics", "description": "The wolf has advantage on an attack roll against a creature if at least one of the wolf's allies is within 5 feet of the creature and the ally isn't incapacitated."}
        ]),
        "actions": json.dumps([
            {"name": "Bite", "description": "Melee Weapon Attack: +5 to hit, reach 5 ft., one target. Hit: 10 (2d6 + 3) piercing damage. If the target is a creature, it must succeed on a DC 13 Strength saving throw or be knocked prone."}
        ]),
        "cr": "1"
    },
    {
        "sourceKey": "shadow", "name": "Shadow", "size": "Medium", "type": "Undead",
        "alignment": "Chaotic Evil", "ac": 12, "hp": "16 (3d8 + 3)", "speed": "40 ft.",
        "strScore": 6, "dexScore": 14, "conScore": 13, "intScore": 6, "wisScore": 10, "chaScore": 8,
        "skills": "Stealth +4", "damageVulnerabilities": "radiant",
        "damageResistances": "acid, cold, fire, lightning, thunder; bludgeoning, piercing, and slashing from nonmagical attacks",
        "damageImmunities": "necrotic, poison",
        "conditionImmunities": "exhaustion, frightened, grappled, paralyzed, petrified, poisoned, prone, restrained",
        "senses": "darkvision 60 ft., passive Perception 10",
        "traits": json.dumps([
            {"name": "Amorphous", "description": "The shadow can move through a space as narrow as 1 inch wide without squeezing."},
            {"name": "Shadow Stealth", "description": "While in dim light or darkness, the shadow can take the Hide action as a bonus action."},
            {"name": "Sunlight Weakness", "description": "While in sunlight, the shadow has disadvantage on attack rolls, ability checks, and saving throws."}
        ]),
        "actions": json.dumps([
            {"name": "Strength Drain", "description": "Melee Weapon Attack: +4 to hit, reach 5 ft., one creature. Hit: 9 (2d6 + 2) necrotic damage, and the target's Strength score is reduced by 1d4. The target dies if this reduces its Strength to 0."}
        ]),
        "cr": "1/2"
    },
    {
        "sourceKey": "specter", "name": "Specter", "size": "Medium", "type": "Undead",
        "alignment": "Chaotic Evil", "ac": 12, "hp": "22 (5d8)", "speed": "0 ft., fly 50 ft. (hover)",
        "strScore": 1, "dexScore": 14, "conScore": 11, "intScore": 10, "wisScore": 10, "chaScore": 11,
        "damageResistances": "acid, cold, fire, lightning, thunder; bludgeoning, piercing, and slashing from nonmagical attacks",
        "damageImmunities": "necrotic, poison",
        "conditionImmunities": "charmed, exhaustion, grappled, paralyzed, petrified, poisoned, prone, restrained, unconscious",
        "senses": "darkvision 60 ft., passive Perception 10",
        "languages": "understands all languages it knew in life but can't speak",
        "traits": json.dumps([
            {"name": "Incorporeal Movement", "description": "The specter can move through other creatures and objects as if they were difficult terrain. It takes 5 (1d10) force damage if it ends its turn inside an object."},
            {"name": "Sunlight Sensitivity", "description": "While in sunlight, the specter has disadvantage on attack rolls and Wisdom (Perception) checks that rely on sight."}
        ]),
        "actions": json.dumps([
            {"name": "Life Drain", "description": "Melee Spell Attack: +4 to hit, reach 5 ft., one creature. Hit: 10 (3d6) necrotic damage plus DC 10 Con save or hit point max reduced."}
        ]),
        "cr": "1"
    },
    {
        "sourceKey": "werewolf", "name": "Werewolf", "size": "Medium", "type": "Humanoid",
        "alignment": "Chaotic Evil", "ac": 12, "hp": "58 (9d8 + 18)", "speed": "30 ft. (40 ft. in wolf form)",
        "strScore": 15, "dexScore": 13, "conScore": 14, "intScore": 10, "wisScore": 11, "chaScore": 10,
        "skills": "Perception +4, Stealth +3",
        "damageImmunities": "bludgeoning, piercing, and slashing from nonmagical attacks that aren't silvered",
        "senses": "passive Perception 14", "languages": "Common (can't speak in wolf form)",
        "traits": json.dumps([
            {"name": "Shapechanger", "description": "The werewolf can use its action to polymorph into a wolf-humanoid hybrid or into a wolf, or back into its true form. Its statistics are the same in each form."},
            {"name": "Keen Hearing and Smell", "description": "The werewolf has advantage on Wisdom (Perception) checks that rely on hearing or smell."}
        ]),
        "actions": json.dumps([
            {"name": "Multiattack (Humanoid or Hybrid Form Only)", "description": "The werewolf makes two attacks: one with its bite and one with its claws or spear."},
            {"name": "Bite (Wolf or Hybrid Form Only)", "description": "Melee Weapon Attack: +4 to hit, reach 5 ft., one target. Hit: 6 (1d8 + 2) piercing damage. DC 12 Con save or cursed with werewolf lycanthropy."},
            {"name": "Claws (Hybrid Form Only)", "description": "Melee Weapon Attack: +4 to hit, reach 5 ft., one creature. Hit: 7 (2d4 + 2) slashing damage."},
            {"name": "Spear (Humanoid Form Only)", "description": "Melee or Ranged Weapon Attack: +4 to hit, reach 5 ft. or range 20/60 ft., one creature. Hit: 5 (1d6 + 2) piercing damage."}
        ]),
        "cr": "3"
    }
]

with open("src/main/resources/srd/srd-5.2-monsters.json", "w") as f:
    json.dump(MONSTERS, f, indent=2)

print(f"Wrote {len(MONSTERS)} monsters to src/main/resources/srd/srd-5.2-monsters.json")
