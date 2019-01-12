package gdx.diablo.codec.excel;

public class ArmType extends Excel<ArmType.Entry>{
  public static class Entry extends Excel.Entry {
    @Override
    public String toString() {
      return Name;
    }

    @Column
    public String Name;

    @Column
    @Key
    public String Token;
  }
}
