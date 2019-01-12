package gdx.diablo.codec.excel;

public class PlayerClass extends Excel<PlayerClass.Entry>{
  public static class Entry extends Excel.Entry {
    @Override
    public String toString() {
      return PlayerClass;
    }

    @Column(format = "Player Class")
    public String PlayerClass;

    @Column
    @Key
    public String Code;
  }
}
