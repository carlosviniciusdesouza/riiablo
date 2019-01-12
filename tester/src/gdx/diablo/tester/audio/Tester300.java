package gdx.diablo.tester.audio;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl.LwjglApplication;
import com.badlogic.gdx.backends.lwjgl.LwjglApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;

import gdx.diablo.mpq.MPQ;
import gdx.diablo.mpq.MPQFileHandle;

public class Tester300 extends ApplicationAdapter {
  private static final String TAG = "Tester";

  public static void main(String[] args) {
    LwjglApplicationConfiguration config = new LwjglApplicationConfiguration();
    config.title = "Tester300";
    config.resizable = true;
    config.width = 640;
    config.height = 480;
    config.foregroundFPS = config.backgroundFPS = 144;
    new LwjglApplication(new Tester300(), config);
  }

  @Override
  public void create() {
    MPQ d2xMusic = MPQ.loadFromFile(Gdx.files.absolute("C:\\Program Files (x86)\\Steam\\steamapps\\common\\Diablo II\\d2xMusic.mpq"));
    MPQFileHandle fileHandle = new MPQFileHandle(d2xMusic, "data/global/music/introedit.wav");
    FileHandle tempFile = new FileHandle(Gdx.files.getLocalStoragePath()).child(fileHandle.fileName);
    tempFile.writeBytes(fileHandle.readBytes(), false);
    Gdx.app.exit();
  }

  @Override
  public void render() {
    Gdx.gl.glClearColor(0.3f, 0.3f, 0.3f, 1);
    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
  }

  @Override
  public void dispose() {
  }
}
