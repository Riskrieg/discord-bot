package com.riskrieg.bot.game;

import com.riskrieg.core.api.game.Attack;
import com.riskrieg.core.api.game.GameConstants;
import com.riskrieg.core.api.game.entity.nation.Nation;
import com.riskrieg.core.api.game.territory.Claim;
import com.riskrieg.core.api.game.territory.GameTerritory;
import com.riskrieg.core.api.game.territory.TerritoryType;
import com.riskrieg.core.util.game.GameUtil;
import com.riskrieg.map.RkmMap;
import com.riskrieg.map.Territory;
import com.riskrieg.map.territory.TerritoryIdentity;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public class StandardAttack implements Attack {

  @Override
  public boolean success(Nation attacker, Nation defender, TerritoryIdentity identity, RkmMap map, Set<Claim> claims, GameConstants constants) {
    if (attacker == null) {
      return false;
    }
    if (defender == null) {
      return true;
    }
    if (attacker.equals(defender)) {
      return false;
    }
    if (!defender.hasClaimOn(identity, claims)) {
      return false;
    }
    int attackRolls = 1;
    int defenseRolls = 1;
    int attackSides = 8;
    int defenseSides = 6;
    var neighbors = GameUtil.getNeighbors(identity, map).stream().map(Territory::identity).collect(Collectors.toSet());
    for (TerritoryIdentity neighbor : neighbors) {
      var attackerTerritories = attacker.getClaimedTerritories(claims).stream().map(com.riskrieg.core.api.game.territory.Claim::territory).map(GameTerritory::identity)
          .collect(Collectors.toSet());
      var defenderTerritories = defender.getClaimedTerritories(claims).stream().map(com.riskrieg.core.api.game.territory.Claim::territory).map(GameTerritory::identity)
          .collect(Collectors.toSet());
      if (attackerTerritories.contains(neighbor)) {
        if (GameUtil.territoryIsOfType(neighbor, TerritoryType.CAPITAL, claims)) {
          attackRolls += 1 + constants.capitalAttackBonus();
        } else {
          attackRolls++;
        }
      } else if (defenderTerritories.contains(neighbor)) {
        defenseRolls++;
        if (GameUtil.territoryIsOfType(identity, TerritoryType.CAPITAL, claims)) {
          defenseSides += 1 + constants.capitalDefenseBonus();
        }
      }
    }
    Dice attackDice = new Dice(attackSides, attackRolls);
    Dice defenseDice = new Dice(defenseSides, defenseRolls);
    int attackerMax = Arrays.stream(attackDice.roll()).summaryStatistics().getMax();
    int defenderMax = Arrays.stream(defenseDice.roll()).summaryStatistics().getMax();
    return attackerMax > defenderMax;
  }

}
