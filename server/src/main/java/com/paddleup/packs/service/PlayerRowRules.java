package com.paddleup.packs.service;

import com.paddleup.accounts.service.DisplayNameSanitiser;
import com.paddleup.packs.dto.FieldError;
import com.paddleup.packs.dto.PackDto;
import com.paddleup.packs.dto.PlayerInput;
import com.paddleup.packs.dto.RoleDto;
import com.paddleup.packs.dto.TagDto;
import com.paddleup.packs.dto.ValidPlayer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The rules one player row must meet, shared by the dataset import and by a Commissioner's edit of
 * a room's copy. Pure: it reads the role keys, tag keys and price rule it was built with and
 * nothing else. Text is sanitised before it is measured.
 */
public final class PlayerRowRules {

  public static final int NAME_MAX = 60;
  public static final int COUNTRY_MAX = 40;
  public static final int SET_MAX = 40;
  public static final int PROVIDER_ID_MAX = 64;
  public static final int TAGS_MAX = 10;

  private static final int VALUE_SHOWN_MAX = 60;
  private static final int RAW_TEXT_MAX = 200;
  private static final Pattern PROVIDER_ID = Pattern.compile("[A-Za-z0-9._:-]+");
  private static final Pattern WHOLE_NUMBER = Pattern.compile("-?\\d{1,18}");

  /** The outcome for one row: its errors, or the cleaned player when there are none. */
  public record Result(List<FieldError> errors, ValidPlayer player) {
    public boolean valid() {
      return errors.isEmpty();
    }
  }

  private final Set<String> roleKeys;
  private final Set<String> tagKeys;
  private final PriceRule price;
  private final String packName;

  /** The rules for an import: every preset of the pack must accept the price. */
  public static PlayerRowRules forPack(PackDto pack) {
    return new PlayerRowRules(
        pack.roles().stream().map(RoleDto::id).collect(Collectors.toCollection(LinkedHashSet::new)),
        pack.tags().stream().map(TagDto::id).collect(Collectors.toCollection(LinkedHashSet::new)),
        PriceRule.ofPack(pack),
        pack.display().name() == null || pack.display().name().isBlank()
            ? pack.id()
            : pack.display().name());
  }

  public PlayerRowRules(
      Set<String> roleKeys, Set<String> tagKeys, PriceRule price, String packName) {
    this.roleKeys = new LinkedHashSet<>(roleKeys);
    this.tagKeys = new LinkedHashSet<>(tagKeys);
    this.price = price;
    this.packName = packName;
  }

  public Result check(PlayerInput row) {
    var errors = new ArrayList<FieldError>();
    String name = text("name", row.name(), NAME_MAX, errors);
    String country = text("country", row.country(), COUNTRY_MAX, errors);
    String set = text("set", row.set(), SET_MAX, errors);
    String role = role(row.role(), errors);
    List<String> tags = tags(row.tags(), errors);
    long basePrice = price(row.basePrice(), errors);
    String providerId = providerId(row.providerId(), errors);
    if (!errors.isEmpty()) {
      return new Result(List.copyOf(errors), null);
    }
    return new Result(
        List.of(), new ValidPlayer(name, country, role, tags, basePrice, set, providerId));
  }

  /** "A, B or C" in the order the pack lists them. */
  private static String oneOf(Set<String> keys) {
    List<String> list = List.copyOf(keys);
    if (list.size() <= 1) {
      return String.join("", list);
    }
    return String.join(", ", list.subList(0, list.size() - 1)) + " or " + list.get(list.size() - 1);
  }

  private static String text(String field, String raw, int max, List<FieldError> errors) {
    if (raw == null || raw.isBlank()) {
      errors.add(error(field, raw, "Required."));
      return null;
    }
    if (raw.length() > RAW_TEXT_MAX) {
      errors.add(error(field, raw, "Must be at most " + max + " characters."));
      return null;
    }
    String clean = DisplayNameSanitiser.sanitise(raw);
    int length = clean.codePointCount(0, clean.length());
    if (length == 0) {
      errors.add(error(field, raw, "Required."));
      return null;
    }
    if (length > max) {
      errors.add(error(field, raw, "Must be at most " + max + " characters."));
      return null;
    }
    return clean;
  }

  private String role(String raw, List<FieldError> errors) {
    String role = raw == null ? "" : raw.trim();
    if (role.isEmpty()) {
      errors.add(error("role", raw, "Required."));
      return null;
    }
    if (!roleKeys.contains(role)) {
      errors.add(error("role", role, "Not a " + packName + " role. Use " + oneOf(roleKeys) + "."));
      return null;
    }
    return role;
  }

  private List<String> tags(List<String> raw, List<FieldError> errors) {
    var clean = new ArrayList<String>();
    var seen = new HashSet<String>();
    boolean ok = true;
    if (raw.size() > TAGS_MAX) {
      errors.add(error("tags", String.join(";", raw), "At most " + TAGS_MAX + " tags."));
      return null;
    }
    for (String tag : raw) {
      String t = tag == null ? "" : tag.trim();
      if (t.isEmpty()) {
        continue;
      }
      if (!tagKeys.contains(t)) {
        errors.add(error("tags", t, "\"" + t + "\" is not a " + packName + " tag."));
        ok = false;
      } else if (!seen.add(t)) {
        errors.add(error("tags", t, "Listed twice."));
        ok = false;
      } else {
        clean.add(t);
      }
    }
    return ok ? clean : null;
  }

  private long price(String raw, List<FieldError> errors) {
    String text = raw == null ? "" : raw.trim();
    if (text.isEmpty()) {
      errors.add(error("base_price", raw, "Base price is required."));
      return 0;
    }
    if (!WHOLE_NUMBER.matcher(text).matches()) {
      errors.add(error("base_price", text, "Base price must be a whole number."));
      return 0;
    }
    long value = Long.parseLong(text);
    if (value <= 0) {
      errors.add(error("base_price", text, "Base price must be above zero."));
      return 0;
    }
    if (!price.accepts(value)) {
      errors.add(error("base_price", text, "That base price isn't allowed in " + packName + "."));
      return 0;
    }
    return value;
  }

  private static String providerId(String raw, List<FieldError> errors) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    String id = raw.trim();
    if (id.length() > PROVIDER_ID_MAX || !PROVIDER_ID.matcher(id).matches()) {
      errors.add(
          error(
              "provider_id",
              id,
              "Up to "
                  + PROVIDER_ID_MAX
                  + " letters, digits, dots, colons, dashes or underscores."));
      return null;
    }
    return id;
  }

  private static FieldError error(String field, String value, String message) {
    String shown = value == null ? "" : value;
    if (shown.length() > VALUE_SHOWN_MAX) {
      shown = shown.substring(0, VALUE_SHOWN_MAX) + "...";
    }
    return new FieldError(field, shown, message);
  }
}
