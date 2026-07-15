package dev.hendrikhoemberg.dmhelper.campaign.web;

import java.util.UUID;

public record CampaignSigil(String path, int rotation) {
  public static CampaignSigil from(UUID id) {
    long state = id.getMostSignificantBits() ^ Long.rotateLeft(id.getLeastSignificantBits(), 17);
    StringBuilder path = new StringBuilder("M 50 10");
    for (int point = 0; point < 5; point++) {
      state = mix(state + 0x9E3779B97F4A7C15L + point);
      int x = 10 + Math.floorMod((int) state, 80);
      int y = 10 + Math.floorMod((int) (state >>> 32), 80);
      path.append(" L ").append(x).append(' ').append(y);
    }
    int rotation = Math.floorMod((int) (state >>> 16), 4) * 45;
    return new CampaignSigil(path.append(" Z").toString(), rotation);
  }

  private static long mix(long value) {
    value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
    value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
    return value ^ (value >>> 31);
  }
}
