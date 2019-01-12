package gdx.diablo.item;

import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.GdxRuntimeException;

import org.apache.commons.lang3.builder.ToStringBuilder;

import gdx.diablo.Diablo;
import gdx.diablo.codec.DC6;
import gdx.diablo.codec.Index;
import gdx.diablo.codec.excel.Inventory;
import gdx.diablo.codec.excel.ItemEntry;
import gdx.diablo.codec.excel.ItemTypes;
import gdx.diablo.codec.excel.MagicAffix;
import gdx.diablo.codec.excel.SetItems;
import gdx.diablo.codec.excel.UniqueItems;
import gdx.diablo.codec.util.BBox;
import gdx.diablo.codec.util.BitStream;
import gdx.diablo.entity.Player;
import gdx.diablo.graphics.PaletteIndexedBatch;

import static gdx.diablo.item.Quality.SET;

public class Item extends Actor implements Disposable {
  private static final String TAG = "Item";
  private static final boolean DEBUG = true;
  private static final boolean DEBUG_VERBOSE = DEBUG && !true;

  public static final float ETHEREAL_ALPHA = 0.667f;

  private static final int MAGIC_AFFIX_SIZE = 11;
  private static final int MAGIC_AFFIX_MASK = 0x7FF;
  private static final int RARE_AFFIX_SIZE = 8;
  private static final int RARE_AFFIX_MASK = 0xFF;

  private static final int QUEST      = 0x00000001;
  private static final int IDENTIFIED = 0x00000010;
  private static final int SOCKETED   = 0x00000800;
  private static final int EAR        = 0x00010000;
  private static final int STARTER    = 0x00020000;
  private static final int COMPACT    = 0x00200000;
  private static final int ETHEREAL   = 0x00400000;
  private static final int INSCRIBED  = 0x01000000;
  private static final int RUNEWORD   = 0x04000000;

  public int      flags;
  public int      version; // 0 = pre-1.08; 1 = 1.08/1.09 normal; 2 = 1.10 normal; 100 = 1.08/1.09 expansion; 101 = 1.10 expansion
  public Location location;
  public BodyLoc  bodyLoc;
  public StoreLoc storeLoc;
  public byte     gridX;
  public byte     gridY;
  public String   typeCode;
  public int      socketsFilled;

  public Array<Item> socketed;

  // Extended
  public long     id;
  public byte     level;
  public Quality  quality;
  public byte     pictureId;
  public short    classOnly;
  public int      qualityId;
  public Object   qualityData;
  public int      runewordData;
  public String   inscription;

  public ItemEntry       base;
  public ItemTypes.Entry type;

  private String  name;
  private AssetDescriptor<DC6> invFileDescriptor;
  public DC6      invFile;
  public Index    invColormap;
  public int      invColorIndex;
  public Index    charColormap;
  public int      charColorIndex;

  public static Item loadFromStream(BitStream bitStream) {
    return new Item().read(bitStream);
  }

  private Item() {
    addListener(new ClickListener() {
      @Override
      public void clicked(InputEvent event, float x, float y) {
        //...
      }
    });
  }

  private Item read(BitStream bitStream) {
    flags    = bitStream.read32BitsOrLess(Integer.SIZE);
    version  = bitStream.readUnsigned8OrLess(8);
    bitStream.skip(2); // TODO: Unknown, likely included with location, should log at some point to check
    location = Location.valueOf(bitStream.readUnsigned7OrLess(3));
    bodyLoc  = BodyLoc.valueOf(bitStream.readUnsigned7OrLess(4));
    gridX    = bitStream.readUnsigned7OrLess(4);
    gridY    = bitStream.readUnsigned7OrLess(4);
    storeLoc = StoreLoc.valueOf(bitStream.readUnsigned7OrLess(3));

    if ((flags & EAR) == EAR) {
      typeCode      = "play"; // Player Body Part
      socketsFilled = 0;
      // TODO: read ear data
    } else {
      typeCode      = bitStream.readString(4).trim();
      socketsFilled = bitStream.readUnsigned7OrLess(3);
    }

    socketed = new Array<>(6);

    base = findBase(typeCode);
    type = Diablo.files.ItemTypes.get(base.type);

    if ((flags & COMPACT) == COMPACT) {
      id           = 0;
      level        = 0;
      quality      = Quality.NONE;
      pictureId    = -1;
      classOnly    = -1;
      qualityId    = 0;
      qualityData  = null;
      runewordData = 0;
      inscription  = null;
    } else {
      id        = bitStream.read32BitsOrLess(Integer.SIZE);
      level     = bitStream.readUnsigned7OrLess(7);
      quality   = Quality.valueOf(bitStream.readUnsigned7OrLess(4));
      pictureId = bitStream.readBoolean() ? bitStream.readUnsigned7OrLess(3)   : -1;
      classOnly = bitStream.readBoolean() ? bitStream.readUnsigned15OrLess(11) : -1;
      switch (quality) {
        case LOW:
        case HIGH:
          qualityId = bitStream.readUnsigned31OrLess(3);
          break;

        case NORMAL:
          qualityId = 0;
          break;

        case SET:
        case UNIQUE:
          qualityId = bitStream.readUnsigned31OrLess(12);
          qualityData = quality == SET
              ? Diablo.files.SetItems.get(qualityId)
              : Diablo.files.UniqueItems.get(qualityId);
          break;

        case MAGIC:
          qualityId = bitStream.readUnsigned31OrLess(2 * MAGIC_AFFIX_SIZE); // 11 for prefix, 11 for suffix
          break;

        case RARE:
        case CRAFTED:
          qualityId = bitStream.readUnsigned31OrLess(2 * RARE_AFFIX_SIZE); // 8 for prefix, 8 for suffix
          qualityData = new RareQualityData(bitStream);
          break;

        default:
          qualityId = 0;
      }

      if ((flags & RUNEWORD) == RUNEWORD) {
        runewordData = bitStream.read16BitsOrLess(Short.SIZE);
      }

      if ((flags & INSCRIBED) == INSCRIBED) {
        inscription = bitStream.readString2(Player.MAX_NAME_LENGTH + 1, 7);
      }
    }

    return this;
  }

  public void load() {
    if (invFileDescriptor != null) return;
    invFileDescriptor = new AssetDescriptor<>("data\\global\\items\\" + getInvFileName() + '.' + DC6.EXT, DC6.class);
    Diablo.assets.load(invFileDescriptor);
    Diablo.assets.finishLoadingAsset(invFileDescriptor);
    invFile = Diablo.assets.get(invFileDescriptor);

    invColormap     = Diablo.colormaps.get(base.InvTrans);
    String invColor = getInvColor();
    invColorIndex   = invColor != null ? Diablo.files.colors.index(invColor) + 1 : 0;

    charColormap    = Diablo.colormaps.get(base.Transform);
    String charColor = getCharColor();
    charColorIndex  = charColor != null ? Diablo.files.colors.index(charColor) + 1 : 0;
  }

  @Override
  public void dispose() {
    Diablo.assets.unload(invFileDescriptor.fileName);
  }

  @SuppressWarnings("unchecked")
  public static <T extends ItemEntry> T findBase(String code) {
    ItemEntry entry;
    if ((entry = Diablo.files.armor  .get(code)) != null) return (T) entry;
    if ((entry = Diablo.files.weapons.get(code)) != null) return (T) entry;
    if ((entry = Diablo.files.misc   .get(code)) != null) return (T) entry;
    throw new GdxRuntimeException("Unable to locate entry for code: " + code);
  }

  @SuppressWarnings("unchecked")
  public <T extends ItemEntry> T getBase() {
    return (T) base;
  }

  @Override
  public String toString() {
    ToStringBuilder builder = new ToStringBuilder(this);
    builder
        .append("name", getName())
        .append("type", typeCode)
        .append("flags", getFlagsString())
        .append("version", version);
    if (DEBUG_VERBOSE) {
      builder
          .append("location", location)
          .append("bodyLoc", bodyLoc)
          .append("storeLoc", storeLoc)
          .append("gridX", gridX)
          .append("gridY", gridY)
          .append("socketsFilled", socketsFilled)
          .append("id", String.format("0x%08X", (int) id))
          .append("level", level)
          .append("quality", quality)
          .append("pictureId", pictureId)
          .append("classOnly", String.format("0x%04X", classOnly))
          .append("qualityId", String.format("0x%08X", qualityId))
          .append("qualityData", qualityData)
          .append("runewordData", String.format("0x%04X", runewordData))
          .append("inscription", inscription)
          .append("socketed", socketed);
    } else {
      builder.append("location", location);
      switch (location) {
        case EQUIPPED:
          builder.append("bodyLoc", bodyLoc);
          break;

        case STORED:
          builder
              .append("storeLoc", storeLoc)
              .append("gridX", gridX)
              .append("gridY", gridY);
          break;

        case BELT:
          builder.append("gridX", gridX);
          break;

        default:
          // ignored
      }

      if ((flags & COMPACT) == 0) {
        builder
            .append("id", String.format("0x%08X", (int) id))
            .append("level", level)
            .append("quality", quality);
        if (pictureId >= 0) builder.append("pictureId", pictureId);
        if (classOnly >= 0) builder.append("classOnly", String.format("0x%04X", classOnly));
        switch (quality) {
          case LOW:
            builder.append("qualityId", LowQuality.valueOf((int) qualityId));
            break;

          case NORMAL:
            break;

          case HIGH:
            builder.append("qualityId", Diablo.files.QualityItems.get((int) qualityId));
            break;

          case MAGIC:
            builder.append("qualityId", String.format("0x%06X", qualityId));
            break;

          case RARE:
          case CRAFTED:
            builder
                .append("qualityId", String.format("0x%02X", qualityId))
                .append("affixes", qualityData);
            break;

          case SET:
          case UNIQUE:
          default:
            builder.append("qualityId", qualityId);
        }

        if ((flags & RUNEWORD) == RUNEWORD) {
          builder.append("runewordData", String.format("[id=%d, extra=%d]",
              RunewordData.id(runewordData), RunewordData.extra(runewordData)));
        }

        if ((flags & INSCRIBED) == INSCRIBED) {
          builder.append("inscription", inscription);
        }

        if ((flags & SOCKETED) == SOCKETED && socketsFilled > 0) {
          builder.append("socketed", socketed);
        }
      }
    }

    return builder.toString();
  }

  private String getFlagsString() {
    StringBuilder builder = new StringBuilder();
    if ((flags & QUEST     ) == QUEST     ) builder.append("QUEST"     ).append('|');
    if ((flags & IDENTIFIED) == IDENTIFIED) builder.append("IDENTIFIED").append('|');
    if ((flags & SOCKETED  ) == SOCKETED  ) builder.append("SOCKETED"  ).append('|');
    if ((flags & EAR       ) == EAR       ) builder.append("EAR"       ).append('|');
    if ((flags & STARTER   ) == STARTER   ) builder.append("STARTER"   ).append('|');
    if ((flags & COMPACT   ) == COMPACT   ) builder.append("COMPACT"   ).append('|');
    if ((flags & ETHEREAL  ) == ETHEREAL  ) builder.append("ETHEREAL"  ).append('|');
    if ((flags & INSCRIBED ) == INSCRIBED ) builder.append("INSCRIBED" ).append('|');
    if ((flags & RUNEWORD  ) == RUNEWORD  ) builder.append("RUNEWORD"  ).append('|');
    if (builder.length() > 0) builder.setLength(builder.length() - 1);
    return builder.toString();
  }

  public String getName() {
    if (name == null) updateName();
    return name;
  }

  public void updateName() {
    StringBuilder name = new StringBuilder();
    int prefix, suffix;
    MagicAffix affix;
    switch (quality) {
      case LOW:
      case NORMAL:
      case HIGH:
        if ((flags & RUNEWORD) == RUNEWORD) {
          int runeword = RunewordData.id(runewordData);
          name.append(Diablo.files.Runes.get(runeword).Rune_Name);
          break;
        } else if (socketsFilled > 0) {
          name.append(Diablo.string.lookup(1728)) // Gemmed
              .append(' ')
              .append(base.name);
          break;
        }

        switch (quality) {
          case LOW:
            name.append(Diablo.string.lookup(LowQuality.valueOf(qualityId).stringId))
                .append(' ')
                .append(base.name);
            break;

          case HIGH:
            name.append(Diablo.string.lookup(1727)) // Superior
                .append(' ')
                .append(base.name);
            break;

          default:
            name.append(base.name);
        }
        break;

      case MAGIC:
        prefix = qualityId &   MAGIC_AFFIX_MASK;
        suffix = qualityId >>> MAGIC_AFFIX_SIZE;
        if ((affix = Diablo.files.MagicPrefix.get(prefix)) != null) name.append(affix.name).append(' ');
        name.append(base.name);
        if ((affix = Diablo.files.MagicSuffix.get(suffix)) != null) name.append(' ').append(affix.name);
        break;

      case RARE:
      case CRAFTED:
        prefix = qualityId &   RARE_AFFIX_MASK;
        suffix = qualityId >>> RARE_AFFIX_SIZE;
        name.append(Diablo.files.RarePrefix.get(prefix).name)
            .append(' ')
            .append(Diablo.files.RareSuffix.get(suffix).name);
        break;

      case SET:
        name.append(Diablo.files.SetItems.get(qualityId).index);
        break;

      case UNIQUE:
        name.append(Diablo.files.UniqueItems.get(qualityId).index);
        break;

      default:
        name.append(base.name);
    }

    this.name = name.toString();
  }

  private String getInvFileName() {
    if (pictureId >= 0) return type.InvGfx[pictureId];
    switch (quality) {
      case SET:
        return !base.setinvfile.isEmpty()
            ? base.setinvfile
            : base.invfile;
      case UNIQUE:
        return !base.uniqueinvfile.isEmpty()
            ? base.uniqueinvfile
            : base.invfile;
      default:
        return base.invfile;
    }
  }

  public String getInvColor() {
    if (base.InvTrans == 0) return null;
    switch (quality) {
      case MAGIC: {
        MagicAffix affix;
        int prefix = qualityId & MAGIC_AFFIX_MASK;
        if ((affix = Diablo.files.MagicPrefix.get(prefix)) != null && affix.transform)
          return affix.transformcolor;
        int suffix = qualityId >>> MAGIC_AFFIX_SIZE;
        if ((affix = Diablo.files.MagicSuffix.get(suffix)) != null && affix.transform)
          return affix.transformcolor;
        return null;
      }

      case RARE:
      case CRAFTED: {
        MagicAffix affix;
        RareQualityData rareQualityData = (RareQualityData) qualityData;
        for (int i = 0; i < RareQualityData.NUM_AFFIXES; i++) {
          int prefix = rareQualityData.prefixes[i];
          if ((affix = Diablo.files.MagicPrefix.get(prefix)) != null && affix.transform)
            return affix.transformcolor;
          int suffix = rareQualityData.suffixes[i];
          if ((affix = Diablo.files.MagicSuffix.get(suffix)) != null && affix.transform)
            return affix.transformcolor;
        }
        return null;
      }

      case SET:
        return ((SetItems.Entry) qualityData).invtransform;

      case UNIQUE:
        return ((UniqueItems.Entry) qualityData).invtransform;

      default:
        return null;
    }
  }

  public String getCharColor() {
    if (base.Transform == 0) return null;
    switch (quality) {
      case MAGIC: {
        MagicAffix affix;
        int prefix = qualityId & MAGIC_AFFIX_MASK;
        if ((affix = Diablo.files.MagicPrefix.get(prefix)) != null && affix.transform)
          return affix.transformcolor;
        int suffix = qualityId >>> MAGIC_AFFIX_SIZE;
        if ((affix = Diablo.files.MagicSuffix.get(suffix)) != null && affix.transform)
          return affix.transformcolor;
        return null;
      }

      case RARE:
      case CRAFTED: {
        MagicAffix affix;
        RareQualityData rareQualityData = (RareQualityData) qualityData;
        for (int i = 0; i < RareQualityData.NUM_AFFIXES; i++) {
          int prefix = rareQualityData.prefixes[i];
          if ((affix = Diablo.files.MagicPrefix.get(prefix)) != null && affix.transform)
            return affix.transformcolor;
          int suffix = rareQualityData.suffixes[i];
          if ((affix = Diablo.files.MagicSuffix.get(suffix)) != null && affix.transform)
            return affix.transformcolor;
        }
        return null;
      }

      case SET:
        return ((SetItems.Entry) qualityData).chrtransform;

      case UNIQUE:
        return ((UniqueItems.Entry) qualityData).chrtransform;

      default:
        return null;
    }
  }

  public boolean isEthereal() {
    return (flags & ETHEREAL) == ETHEREAL;
  }

  public void resize() {
    BBox box = invFile.getBox();
    setSize(box.width, box.height);
  }

  public void resize(Inventory.Entry inv) {
    setSize(base.invwidth * inv.gridBoxWidth, base.invheight * inv.gridBoxHeight);
  }

  @Override
  public void draw(Batch batch, float a) {
    PaletteIndexedBatch b = (PaletteIndexedBatch) batch;
    boolean ethereal = (flags & ETHEREAL) == ETHEREAL;
    if (ethereal) b.setAlpha(ETHEREAL_ALPHA);
    if (invColormap != null) b.setColormap(invColormap, invColorIndex);
    invFile.draw(b, getX(), getY());
    if (invColormap != null) b.resetColormap();
    if (ethereal) b.resetColor();
  }

  private static class RareQualityData {
    static final int NUM_AFFIXES = 3;
    int[] prefixes, suffixes;
    RareQualityData(BitStream bitStream) {
      prefixes = new int[NUM_AFFIXES];
      suffixes = new int[NUM_AFFIXES];
      for (int i = 0; i < NUM_AFFIXES; i++) {
        prefixes[i] = bitStream.readBoolean() ? bitStream.readUnsigned15OrLess(MAGIC_AFFIX_SIZE) : 0;
        suffixes[i] = bitStream.readBoolean() ? bitStream.readUnsigned15OrLess(MAGIC_AFFIX_SIZE) : 0;
      }
    }

    @Override
    public String toString() {
      return new ToStringBuilder(this)
          .append("prefixes", prefixes)
          .append("suffixes", suffixes)
          .build();
    }
  }

  private static class RunewordData {
    static final int RUNEWORD_ID_SHIFT    = 0;
    static final int RUNEWORD_ID_MASK     = 0xFFF << RUNEWORD_ID_SHIFT;
    static final int RUNEWORD_EXTRA_SHIFT = 12;
    static final int RUNEWORD_EXTRA_MASK  = 0xF << RUNEWORD_EXTRA_SHIFT;

    static int id(int pack) {
      return (pack & RUNEWORD_ID_MASK) >>> RUNEWORD_ID_SHIFT;
    }

    static int extra(int pack) {
      return (pack & RUNEWORD_EXTRA_MASK) >>> RUNEWORD_EXTRA_SHIFT;
    }
  }
}
