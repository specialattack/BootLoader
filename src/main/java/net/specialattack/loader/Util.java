package net.specialattack.loader;

import java.io.Closeable;
import java.io.IOException;

public final class Util {

    private Util() {
    }

    public static void close(Closeable cls) {
        if (cls != null) {
            try {
                cls.close();
            } catch (IOException e) {
            }
        }
    }

}
