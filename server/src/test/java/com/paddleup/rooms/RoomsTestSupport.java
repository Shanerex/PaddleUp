package com.paddleup.rooms;

import com.paddleup.packs.PackTestSupport;
import com.paddleup.packs.dto.PackDto;
import com.paddleup.packs.dto.PresetDto;
import com.paddleup.packs.service.PackService;
import com.paddleup.rooms.config.RoomsProperties;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

/** Loads real pack files for unit tests that need no Spring context of their own. */
public final class RoomsTestSupport {

  public static final Path CRICKET_DIR = Path.of("../packs").toAbsolutePath();

  private RoomsTestSupport() {}

  public static PackDto load(Path dir, String packId) {
    var holder = new AtomicReference<PackDto>();
    PackTestSupport.runner(dir).run(ctx -> holder.set(ctx.getBean(PackService.class).get(packId)));
    return holder.get();
  }

  public static PackDto cricket() {
    return load(CRICKET_DIR, "cricket-t20");
  }

  public static PresetDto preset(PackDto pack, String presetId) {
    return pack.presets().stream().filter(p -> p.id().equals(presetId)).findFirst().orElseThrow();
  }

  public static RoomsProperties defaults() {
    return new RoomsProperties(
        Duration.ofDays(7),
        12,
        2,
        10,
        "http://localhost:3000",
        new RoomsProperties.JoinThrottle(5, 20, Duration.ofMinutes(1)));
  }
}
